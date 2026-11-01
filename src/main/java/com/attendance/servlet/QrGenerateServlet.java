package com.attendance.servlet;

import com.attendance.dao.AttendanceDao;
import com.attendance.dao.CourseDao;
import com.attendance.dao.PlannedSessionDao;
import com.attendance.dao.QrSessionDao;
import com.attendance.dao.TeacherDao;
import com.attendance.model.Attendance;
import com.attendance.model.Course;
import com.attendance.model.PlannedSession;
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
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Servlet handling Dynamic QR Code Generation, 7-Second HMAC Token Rotation,
 * Planned-Session Activation, Display Eligibility, and Session Closure.
 *
 * Key architectural rules enforced here:
 * ─────────────────────────────────────
 * 1. /qr/close and /qr/reopen are STATE-CHANGING and must only be reached via POST.
 *    CSRF token is validated before any mutation on those paths.
 *
 * 2. /qr/generate:
 *    a. If plannedSessionId is present → PLANNED path.
 *       - Association lookup (findByPlannedSessionId) — independent of active state.
 *       - Display eligibility evaluation (5-condition gate).
 *       - If not eligible: render expired/closed state.  NEVER create ordinary QR.
 *    b. If plannedSessionId is absent → ORDINARY path (unchanged from Stage 9/10).
 *
 * 3. /qr/active (ephemeral 7-second polling):
 *    a. If plannedSessionId is present → PLANNED polling path.
 *       Returns JSON status: "ok" / "closed" / "expired".
 *    b. If plannedSessionId is absent → ORDINARY polling path (unchanged).
 *
 * 4. /qr/activate (new POST-only endpoint):
 *    Atomically activates a SCHEDULED planned session and creates the linked QR.
 *
 * 5. Ordinary QR workflow remains 100% backward-compatible.
 */
@WebServlet(name = "QrGenerateServlet",
            urlPatterns = {"/qr/generate", "/qr/active", "/qr/close", "/qr/reopen", "/qr/activate"})
public class QrGenerateServlet extends HttpServlet {

    private QrSessionDao    qrSessionDao;
    private CourseDao       courseDao;
    private TeacherDao      teacherDao;
    private AttendanceDao   attendanceDao;
    private PlannedSessionDao plannedSessionDao;

    @Override
    public void init() throws ServletException {
        this.qrSessionDao      = new QrSessionDao();
        this.courseDao         = new CourseDao();
        this.teacherDao        = new TeacherDao();
        this.attendanceDao     = new AttendanceDao();
        this.plannedSessionDao = new PlannedSessionDao();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // doPost — handles all state-changing endpoints with CSRF enforcement
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String path = request.getServletPath();

        // POST /qr/activate — atomic planned-session activation
        if ("/qr/activate".equals(path)) {
            handlePlannedActivation(request, response);
            return;
        }

        // POST /qr/close — state-changing; CSRF required
        if ("/qr/close".equals(path)) {
            handleClose(request, response);
            return;
        }

        // POST /qr/reopen — state-changing; CSRF required
        if ("/qr/reopen".equals(path)) {
            handleReopen(request, response);
            return;
        }

        // All other paths on POST fall through to GET (read-only display)
        doGet(request, response);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // doGet — read-only display and polling
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // ── Authentication gate ──────────────────────────────────────────────
        User user = SessionUtil.getLoggedInUser(request);
        if (user == null || !"TEACHER".equalsIgnoreCase(user.getRole())) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        Teacher teacher = SessionUtil.getTeacherProfile(request);
        if (teacher == null) {
            teacher = teacherDao.findByUserId(user.getId()).orElse(null);
        }

        // ADMIN without a teacher profile must not receive implicit ownership.
        if (teacher == null && !"ADMIN".equalsIgnoreCase(user.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                "Faculty profile not found. Contact system administrator.");
            return;
        }

        String path   = request.getServletPath();
        String format = request.getParameter("format");

        // ── /qr/close via GET is no longer allowed for safety ───────────────
        if ("/qr/close".equals(path)) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                "Session closure requires a POST request with a valid CSRF token.");
            return;
        }

        // ── /qr/activate via GET is not allowed ─────────────────────────────
        if ("/qr/activate".equals(path)) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                "Planned session activation requires a POST request.");
            return;
        }

        // ── /qr/reopen — still accept GET for backward compatibility ─────────
        if ("/qr/reopen".equals(path)) {
            // Surface deprecation: prefer POST, but handle GET for now.
            handleReopen(request, response);
            return;
        }

        // ── Course ID Validation & Ownership ─────────────────────────────────
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

        // Enforce persisted course ownership from the database record — never trust a browser-supplied ID.
        if (teacher != null && course.getTeacherId() != teacher.getTeacherId()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                "Unauthorized: You do not own this course.");
            return;
        }

        // ── Resolve plannedSessionId parameter ───────────────────────────────
        String psIdParam = request.getParameter("plannedSessionId");
        boolean isPlannedRequest = (psIdParam != null && !psIdParam.trim().isEmpty());

        if ("/qr/active".equals(path)) {
            if (isPlannedRequest) {
                handlePlannedPolling(request, response, psIdParam, courseId);
            } else {
                handleOrdinaryPolling(request, response, courseId);
            }
            return;
        }

        // ── /qr/generate ─────────────────────────────────────────────────────
        if (isPlannedRequest) {
            handlePlannedDisplay(request, response, psIdParam, courseId, course, teacher);
        } else {
            handleOrdinaryDisplay(request, response, courseId, course, teacher);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PLANNED SESSION — display (GET /qr/generate?courseId=C&plannedSessionId=P)
    // ═══════════════════════════════════════════════════════════════════════

    private void handlePlannedDisplay(HttpServletRequest request, HttpServletResponse response,
                                      String psIdParam, int courseId, Course course, Teacher teacher)
            throws ServletException, IOException {

        int plannedSessionId;
        try {
            plannedSessionId = Integer.parseInt(psIdParam.trim());
        } catch (NumberFormatException e) {
            response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Invalid+planned+session+id");
            return;
        }

        // ── Step A: Association lookup (independent of active/expired state) ─
        Optional<QrSession> assocOpt;
        try {
            assocOpt = qrSessionDao.findByPlannedSessionId(plannedSessionId);
        } catch (IllegalStateException integrityError) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Data integrity error: " + integrityError.getMessage());
            return;
        }

        if (!assocOpt.isPresent()) {
            // No QR has been created for this planned session yet.
            // Redirect to dashboard — this should not happen in normal flow because
            // the teacher activates first via POST /qr/activate.
            response.sendRedirect(request.getContextPath() +
                "/teacher/dashboard?error=Planned+session+not+yet+activated");
            return;
        }

        QrSession qs = assocOpt.get();

        // ── Step B: Persisted course match (never trust browser-supplied ids) ─
        if (qs.getCourseId() != courseId) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                "Course ID mismatch: requested courseId=" + courseId +
                " but planned session belongs to courseId=" + qs.getCourseId());
            return;
        }

        // ── Step C: Planned session status check ─────────────────────────────
        PlannedSession plannedSession = null;
        try {
            Optional<PlannedSession> psOpt = plannedSessionDao.findById(plannedSessionId);
            if (psOpt.isPresent()) {
                plannedSession = psOpt.get();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // ── Step D: Display eligibility evaluation (5 conditions) ───────────
        boolean qrActive    = qs.isActive() && qs.getClosedAt() == null;
        boolean hasExpiry   = qs.getExpiresAt() != null;
        boolean notExpired  = hasExpiry &&
                              LocalDateTime.now(ZoneOffset.UTC).isBefore(qs.getExpiresAt());
        boolean statusOpen  = plannedSession != null &&
                              "ATTENDANCE_OPEN".equals(plannedSession.getStatus());
        boolean eligible    = qrActive && hasExpiry && notExpired && statusOpen;

        // Attendance count for both eligible and expired/closed views.
        List<Attendance> markedList = attendanceDao.findBySession(qs.getId());

        if (!eligible) {
            // Render expired/closed view.  NEVER auto-create a replacement QR.
            request.setAttribute("course", course);
            request.setAttribute("qs", qs);
            request.setAttribute("plannedSession", plannedSession);
            request.setAttribute("plannedSessionId", plannedSessionId);
            request.setAttribute("sessionId", qs.getId());
            request.setAttribute("markedCount", markedList.size());
            request.setAttribute("isExpired", true);
            request.setAttribute("csrfToken", SessionUtil.getOrCreateCsrfToken(request));
            request.getRequestDispatcher("/WEB-INF/views/qr-display.jsp").forward(request, response);
            return;
        }

        // ── Eligible path: generate 7-second rotating token ──────────────────
        long now                 = System.currentTimeMillis();
        long step                = QrCodeUtil.getCurrentTimeStep(now);
        long stepStartMs         = step * QrCodeUtil.ROTATION_INTERVAL_MS;
        long nextRotationBoundary = QrCodeUtil.getNextRotationBoundary(now);
        long remainingMs         = QrCodeUtil.getRemainingMsInStep(now);
        int  remainingSeconds    = (int) Math.max(1L, (remainingMs + 999L) / 1000L);

        String token       = QrCodeUtil.generateSignedTokenForStep(qs.getId(), courseId, step);
        String numericCode = QrCodeUtil.computeNumericCode(qs.getId(), courseId, now);

        if (!token.equals(qs.getSessionToken())) {
            qrSessionDao.updateActiveToken(qs.getId(), token);
            qs.setSessionToken(token);
        }

        String host    = request.getServerName();
        int    port    = request.getServerPort();
        String scheme  = request.getScheme();
        String baseUrl = scheme + "://" + host + (port == 80 || port == 443 ? "" : ":" + port)
                         + request.getContextPath();
        String fullScanUrl = baseUrl + "/qr/scan?token=" + token;

        request.setAttribute("course", course);
        request.setAttribute("qs", qs);
        request.setAttribute("plannedSession", plannedSession);
        request.setAttribute("plannedSessionId", plannedSessionId);
        request.setAttribute("sessionToken", token);
        request.setAttribute("numericCode", numericCode);
        request.setAttribute("scanUrl", fullScanUrl);
        request.setAttribute("sessionId", qs.getId());
        request.setAttribute("markedCount", markedList.size());
        request.setAttribute("serverTimestamp", now);
        request.setAttribute("step", step);
        request.setAttribute("tokenGeneratedAt", stepStartMs);
        request.setAttribute("nextRotationBoundary", nextRotationBoundary);
        request.setAttribute("remainingMs", remainingMs);
        request.setAttribute("remainingSeconds", remainingSeconds);
        request.setAttribute("isExpired", false);
        request.setAttribute("csrfToken", SessionUtil.getOrCreateCsrfToken(request));
        request.getRequestDispatcher("/WEB-INF/views/qr-display.jsp").forward(request, response);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PLANNED SESSION — 7-second polling (GET /qr/active?plannedSessionId=P)
    // ═══════════════════════════════════════════════════════════════════════

    private void handlePlannedPolling(HttpServletRequest request, HttpServletResponse response,
                                      String psIdParam, int courseId)
            throws IOException {

        int plannedSessionId;
        try {
            plannedSessionId = Integer.parseInt(psIdParam.trim());
        } catch (NumberFormatException e) {
            sendJson(response, "{\"status\":\"error\",\"message\":\"Invalid plannedSessionId.\"}");
            return;
        }

        Optional<QrSession> assocOpt;
        try {
            assocOpt = qrSessionDao.findByPlannedSessionId(plannedSessionId);
        } catch (IllegalStateException e) {
            sendJson(response, "{\"status\":\"error\",\"message\":\"Integrity error: " + escapeJson(e.getMessage()) + "\"}");
            return;
        }

        if (!assocOpt.isPresent()) {
            sendJson(response, "{\"status\":\"closed\",\"message\":\"Planned QR session not found.\"}");
            return;
        }

        QrSession session = assocOpt.get();

        // Course integrity check.
        if (session.getCourseId() != courseId) {
            sendJson(response, "{\"status\":\"error\",\"message\":\"Course ID mismatch.\"}");
            return;
        }

        if (!session.isActive() || session.getClosedAt() != null) {
            sendJson(response, "{\"status\":\"closed\",\"message\":\"Meeting closed.\",\"sessionId\":"
                    + session.getId() + ",\"markedCount\":" + attendanceDao.findBySession(session.getId()).size() + "}");
            return;
        }

        if (session.getExpiresAt() == null) {
            sendJson(response, "{\"status\":\"error\",\"message\":\"Missing expiry timestamp.\"}");
            return;
        }

        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        if (!nowUtc.isBefore(session.getExpiresAt())) {
            sendJson(response, "{\"status\":\"expired\",\"message\":\"Class meeting scheduled window has expired.\","
                    + "\"sessionId\":" + session.getId()
                    + ",\"markedCount\":" + attendanceDao.findBySession(session.getId()).size() + "}");
            return;
        }

        // Eligible: rotate 7-second token.
        long now                  = System.currentTimeMillis();
        long step                 = QrCodeUtil.getCurrentTimeStep(now);
        long stepStartMs          = step * QrCodeUtil.ROTATION_INTERVAL_MS;
        long nextRotationBoundary = QrCodeUtil.getNextRotationBoundary(now);
        long remainingMs          = QrCodeUtil.getRemainingMsInStep(now);
        int  remainingSeconds     = (int) Math.max(0L, (remainingMs + 999L) / 1000L);

        String token       = QrCodeUtil.generateSignedTokenForStep(session.getId(), courseId, step);
        String numericCode = QrCodeUtil.computeNumericCode(session.getId(), courseId, now);

        if (!token.equals(session.getSessionToken())) {
            qrSessionDao.updateActiveToken(session.getId(), token);
        }

        String host    = request.getServerName();
        int    port    = request.getServerPort();
        String scheme  = request.getScheme();
        String baseUrl = scheme + "://" + host + (port == 80 || port == 443 ? "" : ":" + port)
                         + request.getContextPath();
        String fullScanUrl = baseUrl + "/qr/scan?token=" + token;

        sendJson(response, "{"
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
                + "\"markedCount\":" + attendanceDao.findBySession(session.getId()).size()
                + "}");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ORDINARY SESSION — display (existing Stage 9/10 logic, UNCHANGED)
    // ═══════════════════════════════════════════════════════════════════════

    private void handleOrdinaryDisplay(HttpServletRequest request, HttpServletResponse response,
                                       int courseId, Course course, Teacher teacher)
            throws ServletException, IOException {

        // Clean up any genuinely expired class meetings from hours ago.
        qrSessionDao.autoCloseExpiredSessions();

        Optional<QrSession> activeOpt = qrSessionDao.findActiveByCourseId(courseId);
        QrSession session;
        if (activeOpt.isPresent()) {
            session = activeOpt.get();
        } else {
            session = new QrSession();
            session.setCourseId(courseId);
            session.setTeacherId(teacher != null ? teacher.getTeacherId() : course.getTeacherId());
            // Standard 60-minute ordinary class meeting window in UTC.
            session.setExpiresAt(LocalDateTime.now().plusMinutes(60));
            session.setActive(true);
            String initialPlaceholder = "init_" + System.currentTimeMillis() + "_"
                    + java.util.UUID.randomUUID().toString().replace("-", "");
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

        long now                  = System.currentTimeMillis();
        long step                 = QrCodeUtil.getCurrentTimeStep(now);
        long stepStartMs          = step * QrCodeUtil.ROTATION_INTERVAL_MS;
        long nextRotationBoundary = QrCodeUtil.getNextRotationBoundary(now);
        long remainingMs          = QrCodeUtil.getRemainingMsInStep(now);
        int  remainingSeconds     = (int) Math.max(1L, (remainingMs + 999L) / 1000L);

        String token       = QrCodeUtil.generateSignedTokenForStep(session.getId(), courseId, step);
        String numericCode = QrCodeUtil.computeNumericCode(session.getId(), courseId, now);

        if (!token.equals(session.getSessionToken())) {
            qrSessionDao.updateActiveToken(session.getId(), token);
            session.setSessionToken(token);
        }

        String host    = request.getServerName();
        int    port    = request.getServerPort();
        String scheme  = request.getScheme();
        String baseUrl = scheme + "://" + host + (port == 80 || port == 443 ? "" : ":" + port)
                         + request.getContextPath();
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
        request.setAttribute("isExpired", false);
        request.setAttribute("csrfToken", SessionUtil.getOrCreateCsrfToken(request));
        request.getRequestDispatcher("/WEB-INF/views/qr-display.jsp").forward(request, response);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ORDINARY SESSION — 7-second polling (unchanged)
    // ═══════════════════════════════════════════════════════════════════════

    private void handleOrdinaryPolling(HttpServletRequest request, HttpServletResponse response,
                                       int courseId)
            throws IOException {

        // CRITICAL ARCHITECTURAL GUARANTEE:
        // Polling MUST NEVER create a class meeting session, MUST NEVER auto-close an active meeting,
        // and MUST NEVER run absentee reconciliation.
        Optional<QrSession> activeOpt = qrSessionDao.findActiveByCourseId(courseId);
        if (!activeOpt.isPresent()) {
            sendJson(response, "{\"status\":\"closed\",\"message\":\"Class meeting attendance session has ended or is not active.\"}");
            return;
        }

        QrSession session          = activeOpt.get();
        long now                   = System.currentTimeMillis();
        long step                  = QrCodeUtil.getCurrentTimeStep(now);
        long stepStartMs           = step * QrCodeUtil.ROTATION_INTERVAL_MS;
        long nextRotationBoundary  = QrCodeUtil.getNextRotationBoundary(now);
        long remainingMs           = QrCodeUtil.getRemainingMsInStep(now);
        int  remainingSeconds      = (int) Math.max(0L, (remainingMs + 999L) / 1000L);

        String token       = QrCodeUtil.generateSignedTokenForStep(session.getId(), courseId, step);
        String numericCode = QrCodeUtil.computeNumericCode(session.getId(), courseId, now);

        if (!token.equals(session.getSessionToken())) {
            qrSessionDao.updateActiveToken(session.getId(), token);
            session.setSessionToken(token);
        }

        String host    = request.getServerName();
        int    port    = request.getServerPort();
        String scheme  = request.getScheme();
        String baseUrl = scheme + "://" + host + (port == 80 || port == 443 ? "" : ":" + port)
                         + request.getContextPath();
        String fullScanUrl = baseUrl + "/qr/scan?token=" + token;

        List<Attendance> markedList = attendanceDao.findBySession(session.getId());

        sendJson(response, "{"
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
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST /qr/activate — atomic planned-session activation
    // ═══════════════════════════════════════════════════════════════════════

    private void handlePlannedActivation(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        User user = SessionUtil.getLoggedInUser(request);
        if (user == null || !"TEACHER".equalsIgnoreCase(user.getRole())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required.");
            return;
        }

        // CSRF gate before reading any parameters or touching the database.
        if (!SessionUtil.validateCsrfToken(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            sendJson(response, "{\"success\":false,\"message\":\"CSRF validation failed.\"}");
            return;
        }

        Teacher teacher = SessionUtil.getTeacherProfile(request);
        if (teacher == null) {
            teacher = teacherDao.findByUserId(user.getId()).orElse(null);
        }
        if (teacher == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            sendJson(response, "{\"success\":false,\"message\":\"Faculty profile not found.\"}");
            return;
        }

        String psIdParam = request.getParameter("plannedSessionId");
        if (psIdParam == null || psIdParam.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendJson(response, "{\"success\":false,\"message\":\"Missing plannedSessionId parameter.\"}");
            return;
        }

        int plannedSessionId;
        try {
            plannedSessionId = Integer.parseInt(psIdParam.trim());
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendJson(response, "{\"success\":false,\"message\":\"Invalid plannedSessionId.\"}");
            return;
        }

        // Verify that the planned session belongs to this teacher's course.
        PlannedSession ps = null;
        try {
            Optional<PlannedSession> psOpt = plannedSessionDao.findById(plannedSessionId);
            if (!psOpt.isPresent()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                sendJson(response, "{\"success\":false,\"message\":\"Planned session not found.\"}");
                return;
            }
            ps = psOpt.get();
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendJson(response, "{\"success\":false,\"message\":\"Database error looking up planned session.\"}");
            return;
        }

        // Ownership: teacher_id in the planned_sessions row, not browser-supplied.
        if (ps.getTeacherId() != teacher.getTeacherId()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            sendJson(response, "{\"success\":false,\"message\":\"You do not own this planned session.\"}");
            return;
        }

        String redirectParam = request.getParameter("redirect");
        boolean doRedirect = "true".equalsIgnoreCase(redirectParam);

        // Activate atomically.
        int newQrSessionId;
        try {
            newQrSessionId = plannedSessionDao.activatePlannedSession(
                plannedSessionId, teacher.getTeacherId(), qrSessionDao);
        } catch (IllegalStateException e) {
            if (doRedirect) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=" +
                    java.net.URLEncoder.encode(e.getMessage(), "UTF-8"));
                return;
            }
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            sendJson(response, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            return;
        } catch (SQLException e) {
            e.printStackTrace();
            if (doRedirect) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Database+error+during+activation");
                return;
            }
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendJson(response, "{\"success\":false,\"message\":\"Database error during activation.\"}");
            return;
        }

        if (doRedirect) {
            response.sendRedirect(request.getContextPath() + "/qr/generate?courseId=" + ps.getCourseId() + "&plannedSessionId=" + plannedSessionId);
            return;
        }

        sendJson(response, "{\"success\":true,\"qrSessionId\":" + newQrSessionId
                + ",\"plannedSessionId\":" + plannedSessionId
                + ",\"courseId\":" + ps.getCourseId() + "}");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST /qr/close — unified closure for ordinary and planned sessions
    // ═══════════════════════════════════════════════════════════════════════

    private void handleClose(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        User user = SessionUtil.getLoggedInUser(request);
        if (user == null || !"TEACHER".equalsIgnoreCase(user.getRole())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required.");
            return;
        }

        // CSRF gate — enforced before reading sessionId or touching any table.
        if (!SessionUtil.validateCsrfToken(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            sendJson(response, "{\"status\":\"error\",\"message\":\"CSRF validation failed.\"}");
            return;
        }

        Teacher teacher = SessionUtil.getTeacherProfile(request);
        if (teacher == null) {
            teacher = teacherDao.findByUserId(user.getId()).orElse(null);
        }
        if (teacher == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            sendJson(response, "{\"status\":\"error\",\"message\":\"Faculty profile not found.\"}");
            return;
        }

        String sessionIdParam = request.getParameter("sessionId");
        String format         = request.getParameter("format");

        if (sessionIdParam == null || sessionIdParam.trim().isEmpty()) {
            if ("json".equalsIgnoreCase(format)) {
                sendJson(response, "{\"status\":\"error\",\"message\":\"Missing sessionId.\"}");
            } else {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Missing+session+id");
            }
            return;
        }

        int sessionId;
        try {
            sessionId = Integer.parseInt(sessionIdParam.trim());
        } catch (NumberFormatException e) {
            if ("json".equalsIgnoreCase(format)) {
                sendJson(response, "{\"status\":\"error\",\"message\":\"Invalid sessionId.\"}");
            } else {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Invalid+session+id");
            }
            return;
        }

        Optional<QrSession> sessionOpt = qrSessionDao.findById(sessionId);
        if (!sessionOpt.isPresent()) {
            if ("json".equalsIgnoreCase(format)) {
                sendJson(response, "{\"status\":\"error\",\"message\":\"Session not found.\"}");
            } else {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Session+not+found");
            }
            return;
        }

        QrSession qs = sessionOpt.get();

        // Persisted ownership check — never trust browser-supplied teacher identity.
        if (qs.getTeacherId() != teacher.getTeacherId()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            if ("json".equalsIgnoreCase(format)) {
                sendJson(response, "{\"status\":\"error\",\"message\":\"You do not own this session.\"}");
            } else {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Unauthorized: You do not own this session.");
            }
            return;
        }

        // Dispatch: planned session vs ordinary session.
        if (qs.getPlannedSessionId() != null) {
            // Planned closure — atomic reconciliation via PlannedSessionDao.
            try {
                plannedSessionDao.closeMeetingAndCompleteSession(
                    qs.getPlannedSessionId(), qs.getId(), qs.getCourseId());
            } catch (IllegalArgumentException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                String safeMsg = "Invalid session closure request.";
                if ("json".equalsIgnoreCase(format)) {
                    sendJson(response, "{\"status\":\"error\",\"message\":\"" + escapeJson(safeMsg) + "\"}");
                } else {
                    response.sendRedirect(request.getContextPath() +
                        "/teacher/dashboard?error=" + java.net.URLEncoder.encode(safeMsg, "UTF-8"));
                }
                return;
            } catch (IllegalStateException e) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                String safeMsg = "The session operation is inconsistent with its current lifecycle state.";
                if ("json".equalsIgnoreCase(format)) {
                    sendJson(response, "{\"status\":\"error\",\"message\":\"" + safeMsg + "\"}");
                } else {
                    response.sendRedirect(request.getContextPath() +
                        "/teacher/dashboard?error=" + java.net.URLEncoder.encode(safeMsg, "UTF-8"));
                }
                return;
            } catch (SQLException e) {
                e.printStackTrace();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                if ("json".equalsIgnoreCase(format)) {
                    sendJson(response, "{\"status\":\"error\",\"message\":\"Database error during closure.\"}");
                } else {
                    response.sendRedirect(request.getContextPath() +
                        "/teacher/dashboard?error=Database+error+during+closure");
                }
                return;
            }
        } else {
            // Ordinary closure — existing QrSessionDao pathway.
            qrSessionDao.closeSession(sessionId);
        }

        if ("json".equalsIgnoreCase(format)) {
            sendJson(response, "{\"status\":\"closed\"}");
        } else {
            response.sendRedirect(request.getContextPath() + "/teacher/dashboard?msg=session_closed");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Session Reopen (ordinary sessions only; planned sessions are not reopened)
    // ═══════════════════════════════════════════════════════════════════════

    private void handleReopen(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        User user = SessionUtil.getLoggedInUser(request);
        if (user == null || !"TEACHER".equalsIgnoreCase(user.getRole())) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        // CSRF required when coming via POST; accept GET for backward compat only.
        if ("POST".equalsIgnoreCase(request.getMethod()) && !SessionUtil.validateCsrfToken(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            sendJson(response, "{\"status\":\"error\",\"message\":\"CSRF validation failed.\"}");
            return;
        }

        Teacher teacher = SessionUtil.getTeacherProfile(request);
        if (teacher == null) {
            teacher = teacherDao.findByUserId(user.getId()).orElse(null);
        }

        String format      = request.getParameter("format");
        String sessionIdParam = request.getParameter("sessionId");
        int    courseId    = 0;

        if (sessionIdParam != null && !sessionIdParam.trim().isEmpty()) {
            try {
                int sessionId = Integer.parseInt(sessionIdParam.trim());
                Optional<QrSession> sessionOpt = qrSessionDao.findById(sessionId);
                if (sessionOpt.isPresent()) {
                    QrSession qs = sessionOpt.get();
                    if (teacher != null && qs.getTeacherId() != teacher.getTeacherId()) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN,
                            "Unauthorized: You do not own this session.");
                        return;
                    }
                    // Planned sessions must not be reopened via ordinary reopen path.
                    if (qs.getPlannedSessionId() != null) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        if ("json".equalsIgnoreCase(format)) {
                            sendJson(response, "{\"status\":\"error\",\"message\":\"Planned sessions cannot be reopened. Use the planned session management page.\"}");
                        } else {
                            response.sendRedirect(request.getContextPath() +
                                "/teacher/dashboard?error=Planned+sessions+cannot+be+reopened+here");
                        }
                        return;
                    }
                    qrSessionDao.reopenSession(sessionId);
                    courseId = qs.getCourseId();
                }
            } catch (NumberFormatException ignored) {}
        }

        if ("json".equalsIgnoreCase(format)) {
            sendJson(response, "{\"status\":\"reopened\"}");
        } else if (courseId > 0) {
            response.sendRedirect(request.getContextPath() + "/qr/generate?courseId=" + courseId + "&msg=session_reopened");
        } else {
            response.sendRedirect(request.getContextPath() + "/teacher/dashboard?msg=session_reopened");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private void sendJson(HttpServletResponse response, String json) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.print(json);
            out.flush();
        }
    }

    /** Minimal JSON string escaper for error messages embedded in JSON literals. */
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
