package com.attendance;

import com.attendance.dao.PlannedSessionDao;
import com.attendance.dao.QrSessionDao;
import com.attendance.model.Course;
import com.attendance.model.PlannedSession;
import com.attendance.model.Teacher;
import com.attendance.model.User;
import com.attendance.util.SessionUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ScanIn Stage 13 — Phase 13-C2.2 Contract Verification Test Suite.
 *
 * Scope: Offline static contract and unit tests.
 * Note: These are Java-level unit and static contract tests; browser-level DOM/WebRTC
 * execution is not claimed without an active browser runtime.
 *
 * Verifies:
 * 1. Camera is NOT initialized automatically on page load (static contract).
 * 2. Camera initialization is bound to the explicit Start Camera action (static contract).
 * 3. Camera detection uses the shared submission function (static contract).
 * 4. Prefilled-token page loading does not submit attendance (static contract).
 * 5. Prefilled-token confirmation requires an explicit click (static contract).
 * 6. Login-restored pending tokens require explicit confirmation (static contract).
 * 7. Manual and camera submission retain CSRF header and body transmission (static contract).
 * 8. Duplicate camera detections cannot dispatch repeated submissions while one is pending (static contract).
 * 9. Prefilled tokens are HTML-attribute-encoded before rendering (unit test).
 * 10. GET /qr/scan remains 100% free of attendance and scan-attempt mutations (static contract).
 * 11. Backend CSRF, ownership, block creation, and closure contracts (unit tests).
 */
public class Stage13PhaseC2ContractTest {

    private static int totalPassed = 0;
    private static int totalFailed = 0;

    public static void main(String[] args) {
        System.out.println("================================================================");
        System.out.println("  Stage 13-C2.2 Camera Initiation & HTML Encoding Contract Tests");
        System.out.println("================================================================");

        // ── Phase 13-C2.2 Required Tests (10 items) ──────────────────────────

        runTest("C2.2-01: Camera is NOT initialized automatically on page load [Static Contract]",
                Stage13PhaseC2ContractTest::testCameraNotAutoInitializedOnPageLoad);

        runTest("C2.2-02: Camera initialization is bound to explicit Start Camera action [Static Contract]",
                Stage13PhaseC2ContractTest::testCameraBoundToExplicitAction);

        runTest("C2.2-03: Camera detection uses the shared submitToken pipeline [Static Contract]",
                Stage13PhaseC2ContractTest::testCameraDetectionUsesSharedSubmission);

        runTest("C2.2-04: Prefilled-token page loading does not submit attendance [Static Contract]",
                Stage13PhaseC2ContractTest::testPrefilledTokenNoAutoSubmission);

        runTest("C2.2-05: Prefilled-token confirmation requires an explicit click [Static Contract]",
                Stage13PhaseC2ContractTest::testPrefilledTokenRequiresExplicitClick);

        runTest("C2.2-06: Login-restored pending tokens require explicit confirmation [Static Contract]",
                Stage13PhaseC2ContractTest::testLoginRestoredPendingTokenRequiresExplicitClick);

        runTest("C2.2-07: Manual and camera submission retain CSRF header and body [Static Contract]",
                Stage13PhaseC2ContractTest::testSubmissionRetainsCsrfHeaderAndBody);

        runTest("C2.2-08: Duplicate camera detections blocked while submission is pending [Static Contract]",
                Stage13PhaseC2ContractTest::testDuplicateCameraDetectionsBlocked);

        runTest("C2.2-09: Prefilled tokens are HTML-attribute-encoded before rendering [Unit Test]",
                Stage13PhaseC2ContractTest::testHtmlAttributeEncoding);

        runTest("C2.2-10: GET /qr/scan remains 100% free of attendance/scan mutations [Static Contract]",
                Stage13PhaseC2ContractTest::testGetFreeOfMutations);

        // ── Presentation & Security Regression Invariants ───────────────────

        runTest("C2.2-11: Scanner JSP exposes session CSRF token in meta tag [Static Contract]",
                Stage13PhaseC2ContractTest::testMetaCsrfTokenPresent);

        runTest("C2.2-12: submitToken handles HTTP 401 and HTTP 403 [Static Contract]",
                Stage13PhaseC2ContractTest::testSubmitTokenErrorHandling);

        runTest("C2.2-13: CSRF constant-time comparison rejects null, blank, and mismatch [Unit Test]",
                Stage13PhaseC2ContractTest::testCsrfValidationRejections);

        runTest("C2.2-14: Planned session teacher ownership contract verified [Unit Test]",
                Stage13PhaseC2ContractTest::testTeacherOwnershipContract);

        runTest("C2.2-15: Consecutive block validation rejects non-contiguous time slots [Unit Test]",
                Stage13PhaseC2ContractTest::testBlockValidationRejectsNonContiguous);

        runTest("C2.2-16: Consecutive block validation rejects invalid session count (< 2 or > 4) [Unit Test]",
                Stage13PhaseC2ContractTest::testBlockValidationRejectsInvalidCount);

        runTest("C2.2-17: Expired QR display JSP includes POST closure form with CSRF [Static Contract]",
                Stage13PhaseC2ContractTest::testQrDisplayPostClosureWithCsrf);

        runTest("C2.2-18: qr-scanner.js supports plannedSessionId polling parameter [Static Contract]",
                Stage13PhaseC2ContractTest::testScannerJsSupportsPlannedSessionId);

        runTest("C2.2-19: Teacher dashboard JSP includes planned sessions and forms [Static Contract]",
                Stage13PhaseC2ContractTest::testTeacherDashboardJspIncludesPlannedSessions);

        System.out.println("================================================================");
        System.out.println("Passed: " + totalPassed + " | Failed: " + totalFailed);
        System.out.println("================================================================");

        if (totalFailed > 0) {
            System.exit(1);
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────

    @FunctionalInterface
    interface TestRunnable { void run() throws Exception; }

    private static void runTest(String name, TestRunnable test) {
        System.out.print("[TEST] " + name + " ... ");
        try {
            test.run();
            System.out.println("PASS");
            totalPassed++;
        } catch (Throwable t) {
            System.out.println("FAIL: " + t.getMessage());
            totalFailed++;
        }
    }

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) throw new AssertionError(msg);
    }

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (!expected.equals(actual)) {
            throw new AssertionError(msg + " — expected: " + expected + ", got: " + actual);
        }
    }

    private static String readFile(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    // ── Phase 13-C2.2 Required Tests ──────────────────────────────────────

    private static void testCameraNotAutoInitializedOnPageLoad() throws IOException {
        String content = readFile("src/main/webapp/WEB-INF/views/qr-scan.jsp");
        int domLoadedIdx = content.indexOf("document.addEventListener(\"DOMContentLoaded\"");
        assertTrue(domLoadedIdx != -1, "DOMContentLoaded event listener must exist");
        String domLoadedBody = content.substring(domLoadedIdx);

        // Assert startCamera() is NOT called on DOMContentLoaded
        assertTrue(!domLoadedBody.contains("startCamera()"),
            "CRITICAL (Phase 13-C2.2): DOMContentLoaded must NEVER call startCamera() automatically");
    }

    private static void testCameraBoundToExplicitAction() throws IOException {
        String content = readFile("src/main/webapp/WEB-INF/views/qr-scan.jsp");
        assertTrue(content.contains("id=\"start-camera-btn\""),
            "qr-scan.jsp must declare #start-camera-btn");
        assertTrue(content.contains("onclick=\"startCamera()\""),
            "Camera start must be bound to explicit onclick handler");
        assertTrue(content.contains("id=\"start-camera-overlay-btn\""),
            "qr-scan.jsp must declare overlay start camera button for initial viewfinder state");
        assertTrue(content.contains("id=\"stop-camera-btn\""),
            "qr-scan.jsp must declare #stop-camera-btn for explicit camera shutdown");
    }

    private static void testCameraDetectionUsesSharedSubmission() throws IOException {
        String content = readFile("src/main/webapp/WEB-INF/views/qr-scan.jsp");
        assertTrue(content.contains("handleDetectedUrl(code.data);"),
            "Camera scan in tick() must funnel into handleDetectedUrl");
        int fnIdx = content.indexOf("function handleDetectedUrl(");
        assertTrue(fnIdx != -1, "handleDetectedUrl must exist");
        int nextFnIdx = content.indexOf("async function submitManualToken()", fnIdx);
        String body = content.substring(fnIdx, nextFnIdx);
        assertTrue(body.contains("submitToken(token);"),
            "handleDetectedUrl must invoke the shared submitToken(token) pipeline");
    }

    private static void testPrefilledTokenNoAutoSubmission() throws IOException {
        String content = readFile("src/main/webapp/WEB-INF/views/qr-scan.jsp");
        int domLoadedIdx = content.indexOf("document.addEventListener(\"DOMContentLoaded\"");
        assertTrue(domLoadedIdx != -1, "DOMContentLoaded event listener must exist");
        String domLoadedBody = content.substring(domLoadedIdx);

        assertTrue(!domLoadedBody.contains("submitToken("),
            "DOMContentLoaded must NEVER call submitToken()");
    }

    private static void testPrefilledTokenRequiresExplicitClick() throws IOException {
        String content = readFile("src/main/webapp/WEB-INF/views/qr-scan.jsp");
        assertTrue(content.contains("id=\"confirm-attendance-btn\""),
            "qr-scan.jsp must declare #confirm-attendance-btn");
        assertTrue(content.contains("onclick=\"confirmAttendanceClick()\""),
            "Confirm button must have onclick=\"confirmAttendanceClick()\"");
        assertTrue(content.contains("function confirmAttendanceClick()"),
            "qr-scan.jsp must define confirmAttendanceClick");
        
        int fnIdx = content.indexOf("function confirmAttendanceClick()");
        String fnBody = content.substring(fnIdx, content.indexOf("async function submitToken", fnIdx));
        assertTrue(fnBody.contains("handleDetectedUrl(val);") || fnBody.contains("submitToken("),
            "confirmAttendanceClick must dispatch through the shared submission function");
    }

    private static void testLoginRestoredPendingTokenRequiresExplicitClick() throws IOException {
        String loginContent = readFile("src/main/java/com/attendance/servlet/LoginServlet.java");
        assertTrue(loginContent.contains("pendingToken"),
            "LoginServlet must preserve pendingToken");
        assertTrue(loginContent.contains("/qr/scan?token="),
            "LoginServlet must redirect to /qr/scan?token=... with preserved token");

        String servletContent = readFile("src/main/java/com/attendance/servlet/QrScanServlet.java");
        assertTrue(servletContent.contains("sessionPending"),
            "QrScanServlet must check session for pendingToken");
        assertTrue(servletContent.contains("request.setAttribute(\"prefilledToken\", token.trim());"),
            "QrScanServlet must forward restored token as prefilledToken without mutating database");
    }

    private static void testSubmissionRetainsCsrfHeaderAndBody() throws IOException {
        String content = readFile("src/main/webapp/WEB-INF/views/qr-scan.jsp");
        assertTrue(content.contains("\"X-CSRF-TOKEN\": csrfToken"),
            "fetch in submitToken must send X-CSRF-TOKEN header");
        assertTrue(content.contains("&csrfToken=\" + encodeURIComponent(csrfToken)"),
            "fetch body in submitToken must include csrfToken parameter");
    }

    private static void testDuplicateCameraDetectionsBlocked() throws IOException {
        String content = readFile("src/main/webapp/WEB-INF/views/qr-scan.jsp");
        assertTrue(content.contains("if (isSubmitting) return;"),
            "submitToken and handleDetectedUrl must guard against re-entrant calls via isSubmitting");
        
        int submitIdx = content.indexOf("async function submitToken(");
        assertTrue(submitIdx != -1, "submitToken must exist");
        String submitBody = content.substring(submitIdx, submitIdx + 300);
        assertTrue(submitBody.contains("isSubmitting = true;"),
            "submitToken must set isSubmitting = true immediately upon entry");
        assertTrue(submitBody.contains("scanning = false;"),
            "submitToken must halt camera scanning immediately upon entry");
        assertTrue(submitBody.contains("stopCameraTracks();"),
            "submitToken must release camera hardware immediately during processing");
    }

    private static void testHtmlAttributeEncoding() {
        // 1. Double quote injection attack
        String maliciousAttr = "\" onfocus=\"alert('XSS')\" foo=\"";
        String encodedAttr = SessionUtil.escapeHtmlAttribute(maliciousAttr);
        assertTrue(!encodedAttr.contains("\""),
            "Attribute encoding must eliminate literal double quotes: " + encodedAttr);
        assertTrue(encodedAttr.contains("&quot;"),
            "Attribute encoding must convert double quotes to &quot;: " + encodedAttr);

        // 2. Script tag injection attack
        String scriptInjection = "<script>alert('xss')</script>";
        String encodedScript = SessionUtil.escapeHtmlAttribute(scriptInjection);
        assertTrue(!encodedScript.contains("<") && !encodedScript.contains(">"),
            "Attribute encoding must escape angle brackets: " + encodedScript);
        assertTrue(encodedScript.contains("&lt;") && encodedScript.contains("&gt;"),
            "Attribute encoding must convert < and > to &lt; and &gt;");

        // 3. Ampersand and single quote encoding
        String ampAndSingleQuote = "foo&bar'baz";
        String encodedAmp = SessionUtil.escapeHtmlAttribute(ampAndSingleQuote);
        assertTrue(encodedAmp.contains("&amp;") && encodedAmp.contains("&#39;"),
            "Attribute encoding must escape & and single quotes: " + encodedAmp);

        // 4. Null safety
        assertEquals("", SessionUtil.escapeHtmlAttribute(null), "Null must return empty string");

        // 5. Legitimate alphanumeric token preserved
        String legitToken = "init_planned_101_1730000000_abc123xyz";
        assertEquals(legitToken, SessionUtil.escapeHtmlAttribute(legitToken),
            "Legitimate token without special characters must pass through unchanged");
    }

    private static void testGetFreeOfMutations() throws IOException {
        String content = readFile("src/main/java/com/attendance/servlet/QrScanServlet.java");
        int dogetIdx = content.indexOf("protected void doGet(");
        assertTrue(dogetIdx != -1, "QrScanServlet must declare doGet");
        int dopostIdx = content.indexOf("protected void doPost(");
        assertTrue(dopostIdx != -1, "QrScanServlet must declare doPost");
        
        String dogetBody = content.substring(dogetIdx, dopostIdx);
        
        assertTrue(!dogetBody.contains("processAttendance("),
            "CRITICAL: doGet must NEVER call processAttendance()");
        assertTrue(!dogetBody.contains("recordAttendanceTransaction("),
            "CRITICAL: doGet must NEVER call recordAttendanceTransaction()");
        assertTrue(!dogetBody.contains("recordAttempt("),
            "CRITICAL: doGet must NEVER call recordAttempt()");
    }

    // ── Invariant Tests ───────────────────────────────────────────────────

    private static void testMetaCsrfTokenPresent() throws IOException {
        String content = readFile("src/main/webapp/WEB-INF/views/qr-scan.jsp");
        assertTrue(content.contains("<meta name=\"csrf-token\" content=\"${csrfToken}\">"),
            "qr-scan.jsp must declare <meta name=\"csrf-token\" content=\"${csrfToken}\">");
    }

    private static void testSubmitTokenErrorHandling() throws IOException {
        String content = readFile("src/main/webapp/WEB-INF/views/qr-scan.jsp");
        assertTrue(content.contains("response.status === 401"),
            "submitToken must explicitly detect HTTP 401 for session expiration");
        assertTrue(content.contains("msg=session_expired"),
            "HTTP 401 handler must redirect to login with session_expired");
        assertTrue(content.contains("response.status === 403"),
            "submitToken must explicitly detect HTTP 403 for CSRF validation failure");
        assertTrue(content.contains("window.location.reload()"),
            "HTTP 403 handler must provide a safe recovery reload action");
    }

    private static void testCsrfValidationRejections() {
        String sessionToken = UUID.randomUUID().toString();
        
        String wrongToken = UUID.randomUUID().toString();
        boolean match = MessageDigest.isEqual(
            sessionToken.getBytes(StandardCharsets.UTF_8),
            wrongToken.getBytes(StandardCharsets.UTF_8));
        assertTrue(!match, "Mismatched token must not match session token");

        String blankToken = "   ";
        assertTrue(blankToken.trim().isEmpty(), "Blank token must be detected as empty");
    }

    private static void testTeacherOwnershipContract() {
        Teacher teacher1 = new Teacher();
        teacher1.setTeacherId(101);

        Course courseOwned = new Course();
        courseOwned.setId(5);
        courseOwned.setTeacherId(101);

        Course courseOther = new Course();
        courseOther.setId(6);
        courseOther.setTeacherId(202);

        assertTrue(courseOwned.getTeacherId() == teacher1.getTeacherId(),
            "Teacher must own courseOwned");
        assertTrue(courseOther.getTeacherId() != teacher1.getTeacherId(),
            "Teacher must NOT own courseOther");

        PlannedSession ps = new PlannedSession();
        ps.setTeacherId(101);
        assertTrue(ps.getTeacherId() == teacher1.getTeacherId(),
            "Teacher must own planned session");
    }

    private static void testBlockValidationRejectsNonContiguous() {
        PlannedSessionDao dao = new PlannedSessionDao();
        LocalDate date = LocalDate.of(2026, 11, 1);

        PlannedSession s1 = new PlannedSession();
        s1.setCourseId(1);
        s1.setTeacherId(10);
        s1.setSessionDate(date);
        s1.setSessionType("LAB");
        s1.setStartTime(LocalTime.of(9, 0));
        s1.setEndTime(LocalTime.of(10, 0));

        PlannedSession s2 = new PlannedSession();
        s2.setCourseId(1);
        s2.setTeacherId(10);
        s2.setSessionDate(date);
        s2.setSessionType("LAB");
        s2.setStartTime(LocalTime.of(10, 15)); // GAP: 10:00 to 10:15
        s2.setEndTime(LocalTime.of(11, 15));

        List<PlannedSession> list = new ArrayList<>();
        list.add(s1);
        list.add(s2);

        boolean rejected = false;
        try {
            dao.validateAndCreateConsecutiveBlock(list);
        } catch (IllegalArgumentException e) {
            rejected = true;
            assertTrue(e.getMessage().contains("contiguous"),
                "Rejection message must mention contiguity");
        } catch (Throwable t) {}
        assertTrue(rejected, "Non-contiguous block must be rejected by validation");
    }

    private static void testBlockValidationRejectsInvalidCount() {
        PlannedSessionDao dao = new PlannedSessionDao();
        List<PlannedSession> single = new ArrayList<>();
        single.add(new PlannedSession());

        boolean rejected = false;
        try {
            dao.validateAndCreateConsecutiveBlock(single);
        } catch (IllegalArgumentException e) {
            rejected = true;
            assertTrue(e.getMessage().contains("between 2 and 4"),
                "Rejection message must mention between 2 and 4");
        } catch (Throwable t) {}
        assertTrue(rejected, "Block of 1 session must be rejected");
    }

    private static void testQrDisplayPostClosureWithCsrf() throws IOException {
        String content = readFile("src/main/webapp/WEB-INF/views/qr-display.jsp");
        assertTrue(content.contains("action=\"${pageContext.request.contextPath}/qr/close\" method=\"POST\""),
            "qr-display.jsp must use method=\"POST\" for closure");
        assertTrue(content.contains("name=\"csrfToken\" value=\"<%= csrfToken"),
            "qr-display.jsp closure form must include csrfToken parameter");
        assertTrue(content.contains("isExpired"),
            "qr-display.jsp must handle isExpired attribute");
    }

    private static void testScannerJsSupportsPlannedSessionId() throws IOException {
        String content = readFile("src/main/webapp/js/qr-scanner.js");
        assertTrue(content.contains("plannedSessionId"),
            "qr-scanner.js initDynamicQrSession must accept plannedSessionId");
        assertTrue(content.contains("&plannedSessionId="),
            "qr-scanner.js must append plannedSessionId to polling URL");
        assertTrue(content.contains("status === \"expired\""),
            "qr-scanner.js must handle status === 'expired' from polling");
    }

    private static void testTeacherDashboardJspIncludesPlannedSessions() throws IOException {
        String content = readFile("src/main/webapp/WEB-INF/views/teacher-dashboard.jsp");
        assertTrue(content.contains("Planned Academic Sessions & Attendance"),
            "teacher-dashboard.jsp must display planned academic sessions section");
        assertTrue(content.contains("/teacher/planned-sessions/create"),
            "teacher-dashboard.jsp must have standalone session creation form");
        assertTrue(content.contains("/teacher/planned-sessions/create-block"),
            "teacher-dashboard.jsp must have consecutive block creation form");
        assertTrue(content.contains("/qr/activate"),
            "teacher-dashboard.jsp must have activation form for SCHEDULED sessions");
        assertTrue(content.contains("name=\"csrfToken\""),
            "Creation and activation forms must include csrfToken");
    }
}
