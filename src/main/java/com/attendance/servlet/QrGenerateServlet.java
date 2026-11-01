package com.attendance.servlet;

import com.attendance.dao.AttendanceDao;
import com.attendance.dao.CourseDao;
import com.attendance.dao.QrSessionDao;
import com.attendance.dao.TeacherDao;
import com.attendance.model.Attendance;
import com.attendance.model.Course;
import com.attendance.model.QrSession;
import com.attendance.model.Teacher;
import com.attendance.model.User;
import com.attendance.util.QrCodeUtil;
import com.attendance.util.SessionUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Servlet handling Dynamic QR Code Generation, 5-Second HMAC Token Rotation,
 * and Session Closure with Absentee Reconcile.
 */
@WebServlet(name = "QrGenerateServlet", urlPatterns = {"/qr/generate", "/qr/active", "/qr/close", "/qr/reopen"})
public class QrGenerateServlet extends HttpServlet {

    private QrSessionDao qrSessionDao;
    private CourseDao courseDao;
    private TeacherDao teacherDao;
    private AttendanceDao attendanceDao;

    @Override
    public void init() throws ServletException {
        this.qrSessionDao = new QrSessionDao();
        this.courseDao = new CourseDao();
        this.teacherDao = new TeacherDao();
        this.attendanceDao = new AttendanceDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = SessionUtil.getLoggedInUser(request);
        if (user == null || !"TEACHER".equalsIgnoreCase(user.getRole())) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        Teacher teacher = SessionUtil.getTeacherProfile(request);
        if (teacher == null) {
            teacher = teacherDao.findByUserId(user.getId()).orElse(null);
        }

        String path = request.getServletPath();
        String format = request.getParameter("format");

        // 1. Handle Explicit Session Close
        if ("/qr/close".equals(path)) {
            String sessionIdParam = request.getParameter("sessionId");
            if (sessionIdParam != null && !sessionIdParam.trim().isEmpty()) {
                try {
                    int sessionId = Integer.parseInt(sessionIdParam.trim());
                    Optional<QrSession> sessionOpt = qrSessionDao.findById(sessionId);
                    if (sessionOpt.isPresent()) {
                        QrSession qs = sessionOpt.get();
                        // Enforce teacher ownership
                        if (teacher != null && qs.getTeacherId() != teacher.getTeacherId()) {
                            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized: You do not own this session.");
                            return;
                        }
                        qrSessionDao.closeSession(sessionId);
                    }
                } catch (NumberFormatException ignored) {}
            }
            if ("json".equalsIgnoreCase(format)) {
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                try (PrintWriter out = response.getWriter()) {
                    out.print("{\"status\":\"closed\"}");
                }
                return;
            }
            response.sendRedirect(request.getContextPath() + "/teacher/dashboard?msg=session_closed");
            return;
        }

        // 1b. Handle Session Reopen
        if ("/qr/reopen".equals(path)) {
            String sessionIdParam = request.getParameter("sessionId");
            int courseId = 0;
            if (sessionIdParam != null && !sessionIdParam.trim().isEmpty()) {
                try {
                    int sessionId = Integer.parseInt(sessionIdParam.trim());
                    Optional<QrSession> sessionOpt = qrSessionDao.findById(sessionId);
                    if (sessionOpt.isPresent()) {
                        QrSession qs = sessionOpt.get();
                        // Enforce teacher ownership
                        if (teacher != null && qs.getTeacherId() != teacher.getTeacherId()) {
                            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized: You do not own this session.");
                            return;
                        }
                        qrSessionDao.reopenSession(sessionId);
                        courseId = qs.getCourseId();
                    }
                } catch (NumberFormatException ignored) {}
            }
            if ("json".equalsIgnoreCase(format)) {
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                try (PrintWriter out = response.getWriter()) {
                    out.print("{\"status\":\"reopened\"}");
                }
                return;
            }
            if (courseId > 0) {
                response.sendRedirect(request.getContextPath() + "/qr/generate?courseId=" + courseId + "&msg=session_reopened");
            } else {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?msg=session_reopened");
            }
            return;
        }

        // 2. Course ID Validation & Authorization
        String courseIdParam = request.getParameter("courseId");
        if (courseIdParam == null || courseIdParam.trim().isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/teacher/dashboard");
            return;
        }

        int courseId;
        try {
            courseId = Integer.parseInt(courseIdParam.trim());
        } catch (NumberFormatException e) {
            response.sendRedirect(request.getContextPath() + "/teacher/dashboard");
            return;
        }

        Optional<Course> courseOpt = courseDao.findById(courseId);
        if (!courseOpt.isPresent()) {
            response.sendRedirect(request.getContextPath() + "/teacher/dashboard");
            return;
        }
        Course course = courseOpt.get();

        // Enforce faculty course ownership
        if (teacher != null && course.getTeacherId() != teacher.getTeacherId()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized: You do not own this course.");
            return;
        }

        // 3. Branch A: Ephemeral 7-Second QR Token Polling (/qr/active)
        // CRITICAL ARCHITECTURAL GUARANTEE:
        // Polling MUST NEVER create a class meeting session, MUST NEVER auto-close an active meeting,
        // and MUST NEVER run absentee reconciliation. It only rotates the ephemeral token for an active meeting.
        if ("/qr/active".equals(path)) {
            Optional<QrSession> activeOpt = qrSessionDao.findActiveByCourseId(courseId);
            if (!activeOpt.isPresent()) {
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                try (PrintWriter out = response.getWriter()) {
                    out.print("{\"status\":\"closed\",\"message\":\"Class meeting attendance session has ended or is not active.\"}");
                }
                return;
            }

            QrSession session = activeOpt.get();
            // Server-synchronized 7-second rotating cryptographic token and 6-digit numeric fallback
            long now = System.currentTimeMillis();
            long step = QrCodeUtil.getCurrentTimeStep(now);
            long stepStartMs = step * QrCodeUtil.ROTATION_INTERVAL_MS;
            long nextRotationBoundary = QrCodeUtil.getNextRotationBoundary(now);
            long remainingMs = QrCodeUtil.getRemainingMsInStep(now);
            int remainingSeconds = (int) Math.max(0L, (remainingMs + 999L) / 1000L);

            String token = QrCodeUtil.generateSignedTokenForStep(session.getId(), courseId, step);
            String numericCode = QrCodeUtil.computeNumericCode(session.getId(), courseId, now);

            // Synchronize active session token in DB only when step advances
            if (!token.equals(session.getSessionToken())) {
                qrSessionDao.updateActiveToken(session.getId(), token);
                session.setSessionToken(token);
            }

            String host = request.getServerName();
            int port = request.getServerPort();
            String scheme = request.getScheme();
            String baseUrl = scheme + "://" + host + (port == 80 || port == 443 ? "" : ":" + port) + request.getContextPath();
            String fullScanUrl = baseUrl + "/qr/scan?token=" + token;

            List<Attendance> markedList = attendanceDao.findBySession(session.getId());

            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            try (PrintWriter out = response.getWriter()) {
                out.print("{"
                        + "\"status\":\"ok\","
                        + "\"token\":\"" + token + "\","
                        + "\"numericCode\":\"" + numericCode + "\","
                        + "\"scanUrl\":\"" + fullScanUrl + "\","
                        + "\"sessionId\":" + session.getId() + ","
                        + "\"serverTimestamp\":" + now + ","
                        + "\"step\":" + step + ","
                        + "\"tokenGeneratedAt\":" + stepStartMs + ","
                        + "\"nextRotationBoundary\":" + nextRotationBoundary + ","
                        + "\"remainingMs\":" + remainingMs + ","
                        + "\"remainingSeconds\":" + remainingSeconds + ","
                        + "\"expiresInSeconds\":" + QrCodeUtil.ROTATION_INTERVAL_SECONDS + ","
                        + "\"markedCount\":" + markedList.size()
                        + "}");
                out.flush();
            }
            return;
        }

        // 4. Branch B: Teacher Launches or Views Live QR Display (/qr/generate)
        // Clean up any historical class meetings whose scheduled duration (60+ min) genuinely expired hours ago
        qrSessionDao.autoCloseExpiredSessions();

        // Retrieve existing active meeting session or initialize a single persistent class meeting
        Optional<QrSession> activeOpt = qrSessionDao.findActiveByCourseId(courseId);
        QrSession session;
        if (activeOpt.isPresent()) {
            session = activeOpt.get();
        } else {
            session = new QrSession();
            session.setCourseId(courseId);
            session.setTeacherId(teacher != null ? teacher.getTeacherId() : course.getTeacherId());
            // Standard 60-minute scheduled class meeting window in UTC
            session.setExpiresAt(LocalDateTime.now().plusMinutes(60));
            session.setActive(true);
            String initialPlaceholder = "init_" + System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().replace("-", "");
            session.setSessionToken(initialPlaceholder);
            boolean saved = qrSessionDao.save(session);
            if (!saved || session.getId() <= 0) {
                activeOpt = qrSessionDao.findActiveByCourseId(courseId);
                if (activeOpt.isPresent()) {
                    session = activeOpt.get();
                } else {
                    throw new ServletException("Failed to initialize active attendance session for course ID " + courseId);
                }
            }
        }

        // Generate initial rotating token for this active class meeting
        long now = System.currentTimeMillis();
        long step = QrCodeUtil.getCurrentTimeStep(now);
        long stepStartMs = step * QrCodeUtil.ROTATION_INTERVAL_MS;
        long nextRotationBoundary = QrCodeUtil.getNextRotationBoundary(now);
        long remainingMs = QrCodeUtil.getRemainingMsInStep(now);
        int remainingSeconds = (int) Math.max(1L, (remainingMs + 999L) / 1000L);

        String token = QrCodeUtil.generateSignedTokenForStep(session.getId(), courseId, step);
        String numericCode = QrCodeUtil.computeNumericCode(session.getId(), courseId, now);

        if (!token.equals(session.getSessionToken())) {
            qrSessionDao.updateActiveToken(session.getId(), token);
            session.setSessionToken(token);
        }

        String host = request.getServerName();
        int port = request.getServerPort();
        String scheme = request.getScheme();
        String baseUrl = scheme + "://" + host + (port == 80 || port == 443 ? "" : ":" + port) + request.getContextPath();
        String fullScanUrl = baseUrl + "/qr/scan?token=" + token;

        List<Attendance> markedList = attendanceDao.findBySession(session.getId());

        request.setAttribute("course", course);
        request.setAttribute("sessionToken", token);
        request.setAttribute("numericCode", numericCode);
        request.setAttribute("scanUrl", fullScanUrl);
        request.setAttribute("sessionId", session.getId());
        request.setAttribute("markedCount", markedList.size());
        request.setAttribute("serverTimestamp", now);
        request.setAttribute("step", step);
        request.setAttribute("tokenGeneratedAt", stepStartMs);
        request.setAttribute("nextRotationBoundary", nextRotationBoundary);
        request.setAttribute("remainingMs", remainingMs);
        request.setAttribute("remainingSeconds", remainingSeconds);
        request.getRequestDispatcher("/WEB-INF/views/qr-display.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doGet(request, response);
    }
}
