package com.attendance.servlet;

import com.attendance.dao.AttendanceDao;
import com.attendance.dao.CourseDao;
import com.attendance.dao.QrSessionDao;
import com.attendance.dao.ScanAttemptDao;
import com.attendance.dao.StudentDao;
import com.attendance.model.Attendance;
import com.attendance.model.Course;
import com.attendance.model.QrSession;
import com.attendance.model.Student;
import com.attendance.model.User;
import com.attendance.util.PasswordUtil;
import com.attendance.util.QrCodeUtil;
import com.attendance.util.QrCodeUtil.TokenStatus;
import com.attendance.util.QrCodeUtil.TokenValidation;
import com.attendance.util.SessionUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Servlet handling Student QR Code Scanning, Typed Fallback Verification,
 * and Rigorous Server-Side Security Checks:
 * - Server-signed HMAC-SHA256 verification
 * - 5-second dynamic rotation window (EXPIRED check)
 * - Closed-session rejection (CLOSED_SESSION check)
 * - Enrollment validation (WRONG_COURSE check)
 * - Duplicate scan rejection (DUPLICATE check)
 * - Replayed token detection (REPLAYED check)
 * - ACID transactional persistence with duplicate key rollback
 */
@WebServlet(name = "QrScanServlet", urlPatterns = {"/qr/scan"})
public class QrScanServlet extends HttpServlet {

    private QrSessionDao qrSessionDao;
    private CourseDao courseDao;
    private StudentDao studentDao;
    private AttendanceDao attendanceDao;
    private ScanAttemptDao scanAttemptDao;
    private com.attendance.dao.PlannedSessionDao plannedSessionDao;

    @Override
    public void init() throws ServletException {
        this.qrSessionDao = new QrSessionDao();
        this.courseDao = new CourseDao();
        this.studentDao = new StudentDao();
        this.attendanceDao = new AttendanceDao();
        this.scanAttemptDao = new ScanAttemptDao();
        this.plannedSessionDao = new com.attendance.dao.PlannedSessionDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String token = request.getParameter("token");
        User user = SessionUtil.getLoggedInUser(request);

        // If student is not logged in, remember token in session and redirect to login
        if (user == null) {
            if (token != null && !token.trim().isEmpty()) {
                request.getSession(true).setAttribute("pendingToken", token.trim());
            }
            response.sendRedirect(request.getContextPath() + "/login?msg=login_to_scan");
            return;
        }

        if (!"STUDENT".equalsIgnoreCase(user.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Only enrolled students can mark attendance.");
            return;
        }

        Student student = SessionUtil.getStudentProfile(request);
        if (student == null) {
            student = studentDao.findByUserId(user.getId()).orElse(null);
        }

        // Restore pending token from session if present (e.g. from prior unauthenticated scan)
        String sessionPending = (String) request.getSession().getAttribute("pendingToken");
        if (sessionPending != null && !sessionPending.trim().isEmpty()) {
            if (token == null || token.trim().isEmpty()) {
                token = sessionPending.trim();
            }
            request.getSession().removeAttribute("pendingToken");
        }

        // NON-NEGOTIABLE SECURITY RULE: GET must NEVER mutate database state
        // (zero attendance records, zero scan-attempt records).
        // If a token parameter was supplied (or restored from pending session),
        // prefill the token in the scanner view so the student submits it
        // exclusively through an explicit confirmation click on the CSRF-protected POST path.
        if (token != null && !token.trim().isEmpty()) {
            request.setAttribute("prefilledToken", token.trim());
        }
        request.setAttribute("csrfToken", SessionUtil.getOrCreateCsrfToken(request));
        request.getRequestDispatcher("/WEB-INF/views/qr-scan.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // 1. Session authentication check.
        User user = SessionUtil.getLoggedInUser(request);
        if (user == null) {
            // Expired unauthenticated session — 401 so the client can redirect to login.
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            sendJsonResponse(response, false, "Session expired. Please log in again.");
            return;
        }

        if (!"STUDENT".equalsIgnoreCase(user.getRole())) {
            sendJsonResponse(response, false, "Unauthorized: Only students can mark attendance.");
            return;
        }

        // 2. CSRF validation — enforced before reading the token parameter or touching
        //    any database table, including scan_attempts.
        if (!SessionUtil.validateCsrfToken(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            sendJsonResponse(response, false,
                "Security validation failed: Invalid or missing CSRF token. Please refresh the page and try again.");
            return;
        }

        Student student = SessionUtil.getStudentProfile(request);
        if (student == null) {
            student = studentDao.findByUserId(user.getId()).orElse(null);
        }

        String token = request.getParameter("token");
        if (token == null || token.trim().isEmpty()) {
            sendJsonResponse(response, false, "Session token is required.");
            return;
        }

        processAttendance(token.trim(), student, request, response, true);
    }

    private void processAttendance(String token, Student student,
                                   HttpServletRequest request, HttpServletResponse response,
                                   boolean isJson) throws ServletException, IOException {
        if (student == null) {
            handleResult(request, response, isJson, false, "Student profile not found.");
            return;
        }

        String tokenHash = PasswordUtil.hashPassword(token);

        // 0. Rapid Guessing & Brute-Force Rate Limiting Protection (Max 5 failed attempts in 60s)
        int recentFailures = scanAttemptDao.countRecentFailedAttempts(student.getStudentId(), 60);
        if (recentFailures >= 5) {
            scanAttemptDao.recordAttempt(null, student.getStudentId(), tokenHash, "RATE_LIMITED");
            handleResult(request, response, isJson, false, "Too many failed attempts. Rapid guessing blocked. Please wait 1 minute before trying again.");
            return;
        }

        // 1. Polymorphic contract validation
        if (!student.canMarkAttendance(token)) {
            scanAttemptDao.recordAttempt(null, student.getStudentId(), tokenHash, "INVALID");
            handleResult(request, response, isJson, false, "Invalid attendance attempt.");
            return;
        }

        QrSession session;

        // Branch A: Short 6-Digit Numeric Fallback Code
        if (token.matches("^\\d{6}$")) {
            Optional<QrSession> numericSessionOpt = qrSessionDao.findByNumericFallbackCode(student.getStudentId(), token);
            if (!numericSessionOpt.isPresent()) {
                scanAttemptDao.recordAttempt(null, student.getStudentId(), tokenHash, "INVALID");
                handleResult(request, response, isJson, false, "Invalid or expired 6-digit code. Please enter the active code on screen.");
                return;
            }
            session = numericSessionOpt.get();
        } else {
            // Branch B: Cryptographic Server-Side Signed HMAC Token
            TokenValidation validation = QrCodeUtil.validateToken(token);

            if (validation.getStatus() == TokenStatus.INVALID) {
                scanAttemptDao.recordAttempt(null, student.getStudentId(), tokenHash, "INVALID");
                handleResult(request, response, isJson, false, "Invalid QR Code or signature.");
                return;
            }

            if (validation.getStatus() == TokenStatus.EXPIRED) {
                scanAttemptDao.recordAttempt(validation.getSessionId(), student.getStudentId(), tokenHash, "EXPIRED");
                handleResult(request, response, isJson, false, "QR code has expired. Please scan the active code.");
                return;
            }

            // Find Session in Database
            Optional<QrSession> sessionOpt = qrSessionDao.findById(validation.getSessionId());
            if (!sessionOpt.isPresent()) {
                scanAttemptDao.recordAttempt(null, student.getStudentId(), tokenHash, "INVALID");
                handleResult(request, response, isJson, false, "Session not found.");
                return;
            }

            session = sessionOpt.get();

            // Course Match Check
            if (session.getCourseId() != validation.getCourseId()) {
                scanAttemptDao.recordAttempt(session.getId(), student.getStudentId(), tokenHash, "WRONG_COURSE");
                handleResult(request, response, isJson, false, "Token course does not match active session.");
                return;
            }

            // Replay & Rotation Check for Full Signed Token:
            // The token must match the currently active rotation token on screen.
            // Multiple enrolled students can scan the active token within its window,
            // while duplicate scans by the same student are strictly blocked per-session below.
            boolean isCurrentActive = token.equals(session.getSessionToken());
            if (!isCurrentActive) {
                scanAttemptDao.recordAttempt(session.getId(), student.getStudentId(), tokenHash, "REPLAYED");
                handleResult(request, response, isJson, false, "Token has already rotated or been replayed. Please scan the current code on screen.");
                return;
            }
        }

        // 4. Closed Session Check
        if (!session.isActive() || session.getClosedAt() != null) {
            scanAttemptDao.recordAttempt(session.getId(), student.getStudentId(), tokenHash, "CLOSED_SESSION");
            handleResult(request, response, isJson, false, "This attendance session has ended / is closed.");
            return;
        }

        // 4b. Direct Real-Time Meeting Expiry Enforcement (Independent of background sweep interval)
        if (session.getExpiresAt() != null) {
            java.time.LocalDateTime nowUtc = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
            if (!nowUtc.isBefore(session.getExpiresAt())) {
                scanAttemptDao.recordAttempt(session.getId(), student.getStudentId(), tokenHash, "EXPIRED_SESSION");
                handleResult(request, response, isJson, false, "This class meeting has reached its scheduled end time and is no longer accepting attendance.");
                return;
            }
        }

        // 4c. Schedule-Driven Planned Session Status Enforcement
        if (session.getPlannedSessionId() != null) {
            try {
                java.util.Optional<com.attendance.model.PlannedSession> plannedOpt = plannedSessionDao.findById(session.getPlannedSessionId());
                if (!plannedOpt.isPresent() || !"ATTENDANCE_OPEN".equals(plannedOpt.get().getStatus())) {
                    scanAttemptDao.recordAttempt(session.getId(), student.getStudentId(), tokenHash, "PLANNED_SESSION_CLOSED");
                    handleResult(request, response, isJson, false, "Attendance is not currently open for this planned academic session.");
                    return;
                }
            } catch (Exception e) {
                handleResult(request, response, isJson, false, "Error verifying planned session status.");
                return;
            }
        }

        // 5. Student Enrollment Verification
        boolean isEnrolled = courseDao.isStudentEnrolled(student.getStudentId(), session.getCourseId());
        if (!isEnrolled) {
            scanAttemptDao.recordAttempt(session.getId(), student.getStudentId(), tokenHash, "WRONG_COURSE");
            handleResult(request, response, isJson, false, "You are not enrolled in this course (" + session.getCourseCode() + ").");
            return;
        }

        // 6. Duplicate Scan Check (Same Student in Same Session)
        Optional<Attendance> existing = attendanceDao.findBySessionAndStudent(session.getId(), student.getStudentId());
        if (existing.isPresent()) {
            Attendance att = existing.get();
            // Stage 13-C4.2: Planned sessions enforce a strict one-way lifecycle (no ABSENT-to-PRESENT recovery)
            if (session.getPlannedSessionId() != null) {
                scanAttemptDao.recordAttempt(session.getId(), student.getStudentId(), tokenHash, "DUPLICATE");
                handleResult(request, response, isJson, false, "Attendance has already been recorded for this session.");
                return;
            }

            // Ordinary legacy sessions: preserve existing ABSENT-to-PRESENT recovery if reopened
            if (!"ABSENT".equalsIgnoreCase(att.getStatus())) {
                scanAttemptDao.recordAttempt(session.getId(), student.getStudentId(), tokenHash, "DUPLICATE");
                handleResult(request, response, isJson, false, "Attendance was already marked for this session.");
                return;
            }
            // If student was marked ABSENT when meeting temporarily closed, transition to PRESENT upon valid scan
            attendanceDao.updateStatus(att.getId(), "PRESENT");
            scanAttemptDao.recordAttempt(session.getId(), student.getStudentId(), tokenHash, "SUCCESS");
            Optional<Course> c = courseDao.findById(session.getCourseId());
            String courseTitle = c.map(course -> course.getCourseCode() + " - " + course.getCourseName()).orElse("Course");
            handleResult(request, response, isJson, true, "Attendance successfully marked PRESENT for " + courseTitle + "!");
            return;
        }

        // 8. ACID Transactional Attendance Recording
        try {
            boolean saved;
            if (session.getPlannedSessionId() != null) {
                saved = attendanceDao.recordPlannedAttendanceTransaction(
                    student.getStudentId(), session.getCourseId(), session.getId(),
                    session.getPlannedSessionId(), "PRESENT", tokenHash
                );
            } else {
                saved = attendanceDao.recordAttendanceTransaction(
                    student.getStudentId(), session.getCourseId(), session.getId(), "PRESENT", tokenHash
                );
            }
            if (saved) {
                Optional<Course> c = courseDao.findById(session.getCourseId());
                String courseTitle = c.map(course -> course.getCourseCode() + " - " + course.getCourseName()).orElse("Course");
                handleResult(request, response, isJson, true, "Attendance successfully marked PRESENT for " + courseTitle + "!");
            } else {
                handleResult(request, response, isJson, false, "Failed to record attendance. Please try again.");
            }
        } catch (IllegalStateException e) {
            handleResult(request, response, isJson, false, e.getMessage());
        } catch (SQLException e) {
            // Handle race condition duplicate constraint violation
            if (e.getErrorCode() == 1062 || (e.getMessage() != null && e.getMessage().contains("Duplicate entry"))) {
                handleResult(request, response, isJson, false, "Attendance was already marked for this session.");
            } else {
                e.printStackTrace();
                handleResult(request, response, isJson, false, "Transaction error while recording attendance.");
            }
        }
    }

    private void handleResult(HttpServletRequest request, HttpServletResponse response,
                              boolean isJson, boolean success, String message) throws ServletException, IOException {
        if (isJson) {
            sendJsonResponse(response, success, message);
        } else {
            if (success) {
                request.setAttribute("successMessage", message);
            } else {
                request.setAttribute("errorMessage", message);
            }
            request.getRequestDispatcher("/WEB-INF/views/student-dashboard.jsp").forward(request, response);
        }
    }

    private void sendJsonResponse(HttpServletResponse response, boolean success, String message) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.print("{\"success\":" + success + ",\"message\":\"" + message.replace("\"", "\\\"") + "\"}");
            out.flush();
        }
    }
}
