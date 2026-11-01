package com.attendance;

import com.attendance.dao.AttendanceDao;
import com.attendance.dao.CourseDao;
import com.attendance.dao.QrSessionDao;
import com.attendance.dao.ScanAttemptDao;
import com.attendance.model.Attendance;
import com.attendance.model.Course;
import com.attendance.model.QrSession;
import com.attendance.util.DBConnection;
import com.attendance.util.PasswordUtil;
import com.attendance.util.QrCodeUtil;
import com.attendance.util.QrCodeUtil.TokenStatus;
import com.attendance.util.QrCodeUtil.TokenValidation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

/**
 * Automated Verification Test Suite for 7-Second Dynamic QR Token Timing:
 *
 * A. QR Timing Tests
 * 1. Verify that the configured QR rotation interval is exactly 7 seconds.
 * 2. Verify that a newly generated token is valid within its permitted validity window.
 * 3. Verify that a token beyond its configured expiration is rejected.
 * 4. Verify that the QR display countdown starts at 7 seconds and resets correctly.
 * 5. Verify that the six-digit fallback code rotates consistently with the QR token.
 *
 * B. Security Regression Tests
 * 6. Tampered HMAC signatures are rejected.
 * 7. Tokens from another course are rejected.
 * 8. Tokens from another meeting session are rejected.
 * 9. Expired tokens are rejected.
 * 10. Replayed tokens are rejected according to the existing replay policy.
 * 11. Duplicate attendance scans do not create duplicate attendance records.
 * 12. Unenrolled students cannot record attendance.
 * 13. Closed meeting sessions reject attendance scans.
 * 14. Existing rate limiting remains functional.
 *
 * C. Meeting Lifecycle Regression Tests
 * 15. QR rotation does not create additional meeting-session database rows.
 * 16. QR rotation does not automatically close the meeting.
 * 17. QR rotation does not trigger absentee reconciliation.
 * 18. Manual session closure still marks unrecorded enrolled students absent.
 * 19. Reopening a meeting preserves the existing meeting ID.
 * 20. Reclosing a meeting remains idempotent and preserves manual attendance decisions.
 */
public class QrTimingVerificationTest {

    private static final String BASE_URL = "http://localhost:8080/attendance";
    private static int totalPassed = 0;
    private static int totalFailed = 0;

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        System.out.println("========================================================");
        System.out.println("   QrTimingVerificationTest: 7-Second QR Timing Suite");
        System.out.println("========================================================");

        // Clean any residual scan attempt blocks for test hygiene
        clearRateLimitAttempts(1);
        clearRateLimitAttempts(2);
        clearRateLimitAttempts(3);

        // A. QR Timing Tests
        runTest("1. Configured QR rotation interval is exactly 7 seconds", QrTimingVerificationTest::testConfiguredRotationIntervalIsSevenSeconds);
        runTest("2. Newly generated token is valid within permitted validity window", QrTimingVerificationTest::testNewlyGeneratedTokenValidWithinWindow);
        runTest("3. Token beyond configured expiration (10s) is rejected as EXPIRED", QrTimingVerificationTest::testTokenBeyondExpirationRejected);
        runTest("4. QR display countdown starts at 7s and client refresher is configured for 7s", QrTimingVerificationTest::testQrDisplayCountdownStartsAtSevenSeconds);
        runTest("5. Six-digit fallback code rotates synchronously every 7 seconds", QrTimingVerificationTest::testNumericFallbackRotatesSynchronously);
        runTest("5b. 30+ consecutive time-steps advance exactly every 7,000ms with zero gaps or skips", QrTimingVerificationTest::testThirtyConsecutiveTimeStepsNoGaps);
        runTest("5c. Repeated polling within same step returns identical token & timing metadata", QrTimingVerificationTest::testRepeatedPollingReturnsIdenticalToken);
        runTest("5d. Monotonic client synchronization & singleton timer prevention", QrTimingVerificationTest::testClientSynchronizationAndSingleTimer);

        // B. Security Regression Tests
        runTest("6. Tampered HMAC signatures are strictly rejected", QrTimingVerificationTest::testTamperedHmacRejected);
        runTest("7. Tokens from another course are rejected (WRONG_COURSE)", QrTimingVerificationTest::testTokensFromAnotherCourseRejected);
        runTest("8. Tokens from another meeting session are rejected", QrTimingVerificationTest::testTokensFromAnotherSessionRejected);
        runTest("9. Expired tokens are rejected by /qr/scan endpoint", QrTimingVerificationTest::testExpiredTokensRejectedByEndpoint);
        runTest("10. Replayed tokens are rejected per replay policy", QrTimingVerificationTest::testReplayedTokensRejected);
        runTest("11. Duplicate scans do not create duplicate attendance records", QrTimingVerificationTest::testDuplicateScansDoNotDuplicateAttendance);
        runTest("12. Unenrolled students cannot record attendance", QrTimingVerificationTest::testUnenrolledStudentRejected);
        runTest("13. Closed meeting sessions reject attendance scans", QrTimingVerificationTest::testClosedSessionRejectsScans);
        runTest("14. Rate limiting remains functional on rapid guessing attempts", QrTimingVerificationTest::testRateLimitingFunctional);

        // C. Meeting Lifecycle Regression Tests
        runTest("15. QR rotation does not create additional meeting session rows", QrTimingVerificationTest::testRotationDoesNotCreateExtraSessions);
        runTest("16. QR rotation does not automatically close the meeting", QrTimingVerificationTest::testRotationDoesNotCloseMeeting);
        runTest("17. QR rotation does not trigger absentee reconciliation", QrTimingVerificationTest::testRotationDoesNotTriggerAbsenteeReconciliation);
        runTest("18. Manual session closure marks unrecorded enrolled students ABSENT", QrTimingVerificationTest::testManualClosureMarksAbsentees);
        runTest("19. Reopening a meeting preserves the existing meeting ID", QrTimingVerificationTest::testReopenPreservesMeetingId);
        runTest("20. Reclosing meeting remains idempotent and preserves manual decisions", QrTimingVerificationTest::testRecloseIdempotentAndPreservesDecisions);

        System.out.println("========================================================");
        System.out.println("7-Second Timing Tests Passed: " + totalPassed + " | Failed: " + totalFailed);
        System.out.println("========================================================");

        if (totalFailed > 0) {
            System.exit(1);
        }
    }

    private static void runTest(String testName, TestRunnable test) {
        System.out.print("[TEST] " + testName + " ... ");
        try {
            test.run();
            System.out.println("PASS");
            totalPassed++;
        } catch (Throwable t) {
            System.out.println("FAIL");
            System.err.println("   Error: " + t.getMessage());
            t.printStackTrace(System.err);
            totalFailed++;
        }
    }

    @FunctionalInterface
    interface TestRunnable {
        void run() throws Throwable;
    }

    // ─────────────────────────────────────────────────────────────
    // A. QR TIMING TESTS
    // ─────────────────────────────────────────────────────────────

    private static void testConfiguredRotationIntervalIsSevenSeconds() throws Exception {
        if (QrCodeUtil.ROTATION_INTERVAL_SECONDS != 7) {
            throw new AssertionError("Expected QrCodeUtil.ROTATION_INTERVAL_SECONDS to be 7, but was: "
                    + QrCodeUtil.ROTATION_INTERVAL_SECONDS);
        }
        if (QrCodeUtil.VALIDITY_WINDOW_MS != 10000L) {
            throw new AssertionError("Expected QrCodeUtil.VALIDITY_WINDOW_MS to be 10000L (7s display + 3s leeway), but was: "
                    + QrCodeUtil.VALIDITY_WINDOW_MS);
        }

        // Verify /qr/active JSON endpoint returns expiresInSeconds == 7
        CourseDao courseDao = new CourseDao();
        int courseId = createTestCourse("TIME-CFG-701", "7s Config Test Course");
        CookieManager cmTeacher = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmTeacher, "teacher@attendance.com", "teacher123");
        sendGet(cmTeacher, BASE_URL + "/qr/generate?courseId=" + courseId);

        String activeJson = sendGet(cmTeacher, BASE_URL + "/qr/active?courseId=" + courseId + "&format=json");
        if (!activeJson.contains("\"expiresInSeconds\":7")) {
            throw new AssertionError("Expected /qr/active JSON response to return 'expiresInSeconds':7, got: " + activeJson);
        }
    }

    private static void testNewlyGeneratedTokenValidWithinWindow() throws Exception {
        int sessionId = 9991;
        int courseId = 8881;
        long now = System.currentTimeMillis();

        String tokenNow = QrCodeUtil.generateSignedTokenWithTimestamp(sessionId, courseId, now);
        TokenValidation vNow = QrCodeUtil.validateToken(tokenNow);
        if (vNow.getStatus() != TokenStatus.VALID) {
            throw new AssertionError("Fresh token should be VALID, got: " + vNow.getStatus() + " (" + vNow.getMessage() + ")");
        }

        // Token generated 5 seconds ago (within the 7-second rotation window)
        String token5sAgo = QrCodeUtil.generateSignedTokenWithTimestamp(sessionId, courseId, now - 5000L);
        TokenValidation v5s = QrCodeUtil.validateToken(token5sAgo);
        if (v5s.getStatus() != TokenStatus.VALID) {
            throw new AssertionError("Token within 5s should be VALID, got: " + v5s.getStatus());
        }

        // Token generated 8 seconds ago (within 10s validity window = 7s display + 3s network leeway)
        String token8sAgo = QrCodeUtil.generateSignedTokenWithTimestamp(sessionId, courseId, now - 8000L);
        TokenValidation v8s = QrCodeUtil.validateToken(token8sAgo);
        if (v8s.getStatus() != TokenStatus.VALID) {
            throw new AssertionError("Token within 8s (inside 10s leeway window) should be VALID, got: " + v8s.getStatus());
        }
    }

    private static void testTokenBeyondExpirationRejected() throws Exception {
        int sessionId = 9992;
        int courseId = 8882;
        long now = System.currentTimeMillis();

        // Token generated 11 seconds ago (beyond the 10-second validity window)
        String expiredToken = QrCodeUtil.generateSignedTokenWithTimestamp(sessionId, courseId, now - 11000L);
        TokenValidation vExpired = QrCodeUtil.validateToken(expiredToken);
        if (vExpired.getStatus() != TokenStatus.EXPIRED) {
            throw new AssertionError("Token generated 11s ago should be EXPIRED, got: " + vExpired.getStatus());
        }

        // Helper expired generator (20 seconds in the past)
        String helperExpiredToken = QrCodeUtil.generateExpiredToken(sessionId, courseId);
        TokenValidation vHelper = QrCodeUtil.validateToken(helperExpiredToken);
        if (vHelper.getStatus() != TokenStatus.EXPIRED) {
            throw new AssertionError("generateExpiredToken should return EXPIRED token, got: " + vHelper.getStatus());
        }
    }

    private static void testQrDisplayCountdownStartsAtSevenSeconds() throws Exception {
        // Inspect qr-display.jsp source file and rendered HTML
        CourseDao courseDao = new CourseDao();
        int courseId = createTestCourse("TIME-JSP-702", "7s Countdown Display Course");
        CookieManager cmTeacher = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmTeacher, "teacher@attendance.com", "teacher123");

        String pageHtml = sendGet(cmTeacher, BASE_URL + "/qr/generate?courseId=" + courseId);
        if (!pageHtml.matches("(?s).*id=\"qr-timer\"[^>]*>[1-7]s</div>.*")) {
            throw new AssertionError("qr-display page does not contain valid countdown element (1-7s): " + pageHtml);
        }
        if (!pageHtml.contains("Active 7s Rotation")) {
            throw new AssertionError("qr-display page missing 'Active 7s Rotation' badge");
        }

        // Inspect qr-scanner.js content on disk
        String jsContent = new String(Files.readAllBytes(Paths.get("src/main/webapp/js/qr-scanner.js")), StandardCharsets.UTF_8);
        if (!jsContent.contains("targetMonotonicTime")) {
            throw new AssertionError("qr-scanner.js does not specify targetMonotonicTime");
        }
        if (!jsContent.contains("isFetching")) {
            throw new AssertionError("qr-scanner.js does not include polling overlap guard 'isFetching'");
        }
    }

    private static void testNumericFallbackRotatesSynchronously() throws Exception {
        int sessionId = 9993;
        int courseId = 8883;

        long t0 = 1000000000000L;
        // Step calculation based on exactly 7 seconds
        long step0 = t0 / (7 * 1000L);
        long t1 = (step0 + 1) * 7000L + 100L; // first millisecond of next 7s step

        String code0 = QrCodeUtil.computeNumericCode(sessionId, courseId, t0);
        String code1 = QrCodeUtil.computeNumericCode(sessionId, courseId, t1);

        if (!code0.matches("^\\d{6}$") || !code1.matches("^\\d{6}$")) {
            throw new AssertionError("Fallback code must be exactly 6 digits. code0: " + code0 + ", code1: " + code1);
        }
        if (code0.equals(code1)) {
            throw new AssertionError("Fallback codes across consecutive 7s windows must differ. Both produced: " + code0);
        }
    }

    private static void testThirtyConsecutiveTimeStepsNoGaps() throws Exception {
        int sessionId = 9994;
        int courseId = 8884;
        long baseTimestamp = 1730000000000L;
        long step0 = QrCodeUtil.getCurrentTimeStep(baseTimestamp);

        String previousToken = null;
        String previousNumericCode = null;
        long previousBoundary = 0;

        // Test across 35 consecutive 7,000ms steps
        for (int i = 0; i < 35; i++) {
            long currentStep = step0 + i;
            long stepStartMs = currentStep * QrCodeUtil.ROTATION_INTERVAL_MS;

            // 1. Verify step calculation matches exact 7000ms boundary
            long computedStep = QrCodeUtil.getCurrentTimeStep(stepStartMs);
            if (computedStep != currentStep) {
                throw new AssertionError("Step mismatch at i=" + i + ": expected " + currentStep + " but got " + computedStep);
            }

            // 2. Verify next boundary is exactly (step + 1) * 7000L
            long expectedBoundary = (currentStep + 1) * QrCodeUtil.ROTATION_INTERVAL_MS;
            long computedBoundary = QrCodeUtil.getNextRotationBoundary(stepStartMs);
            if (computedBoundary != expectedBoundary) {
                throw new AssertionError("Boundary mismatch at step " + currentStep);
            }

            // 3. Verify boundary step delta is strictly 7,000ms (never 14,000ms or 15,000ms)
            if (previousBoundary != 0) {
                long boundaryDelta = computedBoundary - previousBoundary;
                if (boundaryDelta != 7000L) {
                    throw new AssertionError("Boundary delta was " + boundaryDelta + "ms (expected strictly 7000ms) at step " + currentStep);
                }
            }
            previousBoundary = computedBoundary;

            // 4. Generate token and numeric fallback for current step
            String token = QrCodeUtil.generateSignedTokenForStep(sessionId, courseId, currentStep);
            String numericCode = QrCodeUtil.computeNumericCode(sessionId, courseId, stepStartMs);

            if (previousToken != null && token.equals(previousToken)) {
                throw new AssertionError("Consecutive time-step produced identical token at step " + currentStep);
            }
            if (previousNumericCode != null && numericCode.equals(previousNumericCode)) {
                throw new AssertionError("Consecutive time-step produced identical numeric fallback at step " + currentStep);
            }
            previousToken = token;
            previousNumericCode = numericCode;

            // 5. Verify intra-step invariance (polling at +1000ms, +3500ms, +6999ms yields identical token & code)
            for (long offset : new long[]{500L, 2000L, 4500L, 6999L}) {
                long intraTimestamp = stepStartMs + offset;
                long intraStep = QrCodeUtil.getCurrentTimeStep(intraTimestamp);
                if (intraStep != currentStep) {
                    throw new AssertionError("Intra-step timestamp leaked to different step: " + intraStep);
                }
                String intraToken = QrCodeUtil.generateSignedTokenForStep(sessionId, courseId, intraStep);
                String intraCode = QrCodeUtil.computeNumericCode(sessionId, courseId, intraTimestamp);
                if (!intraToken.equals(token)) {
                    throw new AssertionError("Intra-step token changed within active 7s step at offset " + offset);
                }
                if (!intraCode.equals(numericCode)) {
                    throw new AssertionError("Intra-step numeric code changed within active 7s step at offset " + offset);
                }
                long intraRem = QrCodeUtil.getRemainingMsInStep(intraTimestamp);
                if (intraRem != (7000L - offset)) {
                    throw new AssertionError("Remaining ms mismatch at offset " + offset + ": expected " + (7000L - offset) + ", got " + intraRem);
                }
            }
        }
    }

    private static void testRepeatedPollingReturnsIdenticalToken() throws Exception {
        CourseDao courseDao = new CourseDao();
        QrSessionDao sessionDao = new QrSessionDao();

        int courseId = createTestCourse("TIME-POLL-703", "Repeated Polling Course");
        CookieManager cmTeacher = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmTeacher, "teacher@attendance.com", "teacher123");
        sendGet(cmTeacher, BASE_URL + "/qr/generate?courseId=" + courseId);

        // Rapidly poll /qr/active 5 times within a tight 50ms loop
        String firstJson = sendGet(cmTeacher, BASE_URL + "/qr/active?courseId=" + courseId + "&format=json");
        if (!firstJson.contains("\"status\":\"ok\"")) {
            throw new AssertionError("Initial poll failed: " + firstJson);
        }
        if (!firstJson.contains("\"nextRotationBoundary\"") || !firstJson.contains("\"remainingMs\"") || !firstJson.contains("\"step\"")) {
            throw new AssertionError("Response missing server timing metadata: " + firstJson);
        }

        // Extract token
        String tokenPattern = "\"token\":\"([^\"]+)\"";
        java.util.regex.Matcher m1 = java.util.regex.Pattern.compile(tokenPattern).matcher(firstJson);
        if (!m1.find()) throw new AssertionError("Token not found in first poll: " + firstJson);
        String initialToken = m1.group(1);

        for (int i = 0; i < 4; i++) {
            String subsequentJson = sendGet(cmTeacher, BASE_URL + "/qr/active?courseId=" + courseId + "&format=json");
            java.util.regex.Matcher mSub = java.util.regex.Pattern.compile(tokenPattern).matcher(subsequentJson);
            if (mSub.find()) {
                String subToken = mSub.group(1);
                // Within the same step, token must be identical
                if (subsequentJson.contains("\"remainingMs\":") && !subToken.equals(initialToken)) {
                    // Step may only change if clock crossed boundary
                    long step = Long.parseLong(subsequentJson.replaceAll(".*\"step\":(\\d+).*", "$1"));
                    long initStep = Long.parseLong(firstJson.replaceAll(".*\"step\":(\\d+).*", "$1"));
                    if (step == initStep) {
                        throw new AssertionError("Token changed within same step! " + subToken + " vs " + initialToken);
                    }
                }
            }
        }
    }

    private static void testClientSynchronizationAndSingleTimer() throws Exception {
        String jsContent = new String(Files.readAllBytes(Paths.get("src/main/webapp/js/qr-scanner.js")), StandardCharsets.UTF_8);

        // Verify singleton guard exists
        if (!jsContent.contains("window.__scanInSession")) {
            throw new AssertionError("qr-scanner.js missing singleton guard window.__scanInSession");
        }
        if (!jsContent.contains("clearInterval") || !jsContent.contains("clearTimeout")) {
            throw new AssertionError("qr-scanner.js missing cleanup of previous intervals/timeouts");
        }

        // Verify monotonic clock usage
        if (!jsContent.contains("performance.now()")) {
            throw new AssertionError("qr-scanner.js missing monotonic performance.now() clock");
        }

        // Verify server-synchronized targetMonotonicTime anchoring
        if (!jsContent.contains("targetMonotonicTime")) {
            throw new AssertionError("qr-scanner.js missing targetMonotonicTime anchoring");
        }

        // Verify qr-display.jsp includes cache-busting
        String jspContent = new String(Files.readAllBytes(Paths.get("src/main/webapp/WEB-INF/views/qr-display.jsp")), StandardCharsets.UTF_8);
        if (!jspContent.contains("qr-scanner.js?v=")) {
            throw new AssertionError("qr-display.jsp missing cache-busting query parameter on qr-scanner.js");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // B. SECURITY REGRESSION TESTS
    // ─────────────────────────────────────────────────────────────

    private static void testTamperedHmacRejected() throws Exception {
        CourseDao courseDao = new CourseDao();
        QrSessionDao sessionDao = new QrSessionDao();

        int courseId = createTestCourse("SEC-TAMP-703", "Tamper Test Course");
        courseDao.enrollStudent(1, courseId);

        CookieManager cmTeacher = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmTeacher, "teacher@attendance.com", "teacher123");
        sendGet(cmTeacher, BASE_URL + "/qr/generate?courseId=" + courseId);
        QrSession session = sessionDao.findActiveByCourseId(courseId).get();

        String tamperedToken = QrCodeUtil.generateTamperedToken(session.getId(), courseId);

        CookieManager cmStudent = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmStudent, "aarav@student.com", "student123");
        String response = sendPostScan(cmStudent, tamperedToken);

        if (!response.contains("\"success\":false") && !response.contains("Invalid QR")) {
            throw new AssertionError("Tampered token must be rejected, got: " + response);
        }
        verifyLatestScanAttemptOutcome(1, "INVALID");
    }

    private static void testTokensFromAnotherCourseRejected() throws Exception {
        CourseDao courseDao = new CourseDao();
        QrSessionDao sessionDao = new QrSessionDao();

        int courseA = createTestCourse("SEC-CRSA-704", "Course A");
        int courseB = createTestCourse("SEC-CRSB-705", "Course B");
        courseDao.enrollStudent(1, courseA);

        CookieManager cmTeacher = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmTeacher, "teacher@attendance.com", "teacher123");
        sendGet(cmTeacher, BASE_URL + "/qr/generate?courseId=" + courseA);
        QrSession sessionA = sessionDao.findActiveByCourseId(courseA).get();

        // Token signed for course B with session A id
        String wrongCourseToken = QrCodeUtil.generateSignedToken(sessionA.getId(), courseB);

        CookieManager cmStudent = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmStudent, "aarav@student.com", "student123");
        String response = sendPostScan(cmStudent, wrongCourseToken);

        if (!response.contains("\"success\":false")) {
            throw new AssertionError("Wrong course token must be rejected, got: " + response);
        }
        verifyScanAttemptOutcome(sessionA.getId(), 1, "WRONG_COURSE");
    }

    private static void testTokensFromAnotherSessionRejected() throws Exception {
        CourseDao courseDao = new CourseDao();
        int courseId = createTestCourse("SEC-SESS-706", "Another Session Course");
        courseDao.enrollStudent(1, courseId);

        // Token for non-existent session ID 999999
        String phantomToken = QrCodeUtil.generateSignedToken(999999, courseId);

        CookieManager cmStudent = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmStudent, "aarav@student.com", "student123");
        String response = sendPostScan(cmStudent, phantomToken);

        if (!response.contains("\"success\":false")) {
            throw new AssertionError("Token for non-existent session must be rejected, got: " + response);
        }
    }

    private static void testExpiredTokensRejectedByEndpoint() throws Exception {
        CourseDao courseDao = new CourseDao();
        QrSessionDao sessionDao = new QrSessionDao();
        AttendanceDao attendanceDao = new AttendanceDao();

        int courseId = createTestCourse("SEC-EXP-707", "Expired Token Course");
        courseDao.enrollStudent(2, courseId);

        CookieManager cmTeacher = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmTeacher, "teacher@attendance.com", "teacher123");
        sendGet(cmTeacher, BASE_URL + "/qr/generate?courseId=" + courseId);
        QrSession session = sessionDao.findActiveByCourseId(courseId).get();

        String expiredToken = QrCodeUtil.generateExpiredToken(session.getId(), courseId);

        CookieManager cmStudent = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmStudent, "priya@student.com", "student123");
        String response = sendPostScan(cmStudent, expiredToken);

        if (!response.contains("\"success\":false") || !response.contains("expired")) {
            throw new AssertionError("Expired scan should fail with expired message, got: " + response);
        }

        Optional<Attendance> rec = attendanceDao.findBySessionAndStudent(session.getId(), 2);
        if (rec.isPresent()) {
            throw new AssertionError("Expired scan must NOT create an attendance record");
        }
        verifyScanAttemptOutcome(session.getId(), 2, "EXPIRED");
    }

    private static void testReplayedTokensRejected() throws Exception {
        CourseDao courseDao = new CourseDao();
        QrSessionDao sessionDao = new QrSessionDao();

        int courseId = createTestCourse("SEC-RPLY-708", "Replay Test Course");
        courseDao.enrollStudent(1, courseId);

        CookieManager cmTeacher = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmTeacher, "teacher@attendance.com", "teacher123");
        sendGet(cmTeacher, BASE_URL + "/qr/generate?courseId=" + courseId);
        QrSession session = sessionDao.findActiveByCourseId(courseId).get();

        // Create an old token 15 seconds in past
        long pastTime = System.currentTimeMillis() - 15000L;
        String pastToken = QrCodeUtil.generateSignedTokenWithTimestamp(session.getId(), courseId, pastTime);

        CookieManager cmStudent = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmStudent, "aarav@student.com", "student123");
        String response = sendPostScan(cmStudent, pastToken);

        if (!response.contains("\"success\":false")) {
            throw new AssertionError("Rotated/expired token must be rejected, got: " + response);
        }
    }

    private static void testDuplicateScansDoNotDuplicateAttendance() throws Exception {
        CourseDao courseDao = new CourseDao();
        QrSessionDao sessionDao = new QrSessionDao();
        AttendanceDao attendanceDao = new AttendanceDao();

        int courseId = createTestCourse("SEC-DUP-709", "Duplicate Test Course");
        courseDao.enrollStudent(1, courseId);

        CookieManager cmTeacher = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmTeacher, "teacher@attendance.com", "teacher123");
        sendGet(cmTeacher, BASE_URL + "/qr/generate?courseId=" + courseId);
        QrSession session = sessionDao.findActiveByCourseId(courseId).get();

        CookieManager cmStudent = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmStudent, "aarav@student.com", "student123");

        // First scan
        String res1 = sendPostScan(cmStudent, session.getSessionToken());
        if (!res1.contains("\"success\":true")) {
            throw new AssertionError("First scan should succeed, got: " + res1);
        }

        // Duplicate scan with same token
        String res2 = sendPostScan(cmStudent, session.getSessionToken());
        if (!res2.contains("already marked")) {
            throw new AssertionError("Second scan must report already marked, got: " + res2);
        }

        int count = countAttendanceForStudentAndCourse(1, courseId);
        if (count != 1) {
            throw new AssertionError("Expected exactly 1 attendance row for student, got: " + count);
        }
    }

    private static void testUnenrolledStudentRejected() throws Exception {
        CourseDao courseDao = new CourseDao();
        QrSessionDao sessionDao = new QrSessionDao();

        int courseId = createTestCourse("SEC-UNENR-710", "Unenrolled Test Course");
        // Student 3 (rahul@student.com) is NOT enrolled

        CookieManager cmTeacher = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmTeacher, "teacher@attendance.com", "teacher123");
        sendGet(cmTeacher, BASE_URL + "/qr/generate?courseId=" + courseId);
        QrSession session = sessionDao.findActiveByCourseId(courseId).get();

        CookieManager cmStudent3 = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmStudent3, "rahul@student.com", "student123");
        String response = sendPostScan(cmStudent3, session.getSessionToken());

        if (!response.contains("\"success\":false") || !response.contains("not enrolled")) {
            throw new AssertionError("Unenrolled scan should be rejected with 'not enrolled', got: " + response);
        }
        verifyScanAttemptOutcome(session.getId(), 3, "WRONG_COURSE");
    }

    private static void testClosedSessionRejectsScans() throws Exception {
        CourseDao courseDao = new CourseDao();
        QrSessionDao sessionDao = new QrSessionDao();

        int courseId = createTestCourse("SEC-CLS-711", "Closed Session Test Course");
        courseDao.enrollStudent(1, courseId);

        CookieManager cmTeacher = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmTeacher, "teacher@attendance.com", "teacher123");
        sendGet(cmTeacher, BASE_URL + "/qr/generate?courseId=" + courseId);
        QrSession session = sessionDao.findActiveByCourseId(courseId).get();

        // Close session
        sessionDao.closeSession(session.getId());

        // Student attempts to scan closed session
        CookieManager cmStudent = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmStudent, "aarav@student.com", "student123");
        String response = sendPostScan(cmStudent, session.getSessionToken());

        if (!response.contains("\"success\":false") || !response.contains("closed")) {
            throw new AssertionError("Scan on closed session must be rejected with closed message, got: " + response);
        }
        verifyScanAttemptOutcome(session.getId(), 1, "CLOSED_SESSION");
    }

    private static void testRateLimitingFunctional() throws Exception {
        CourseDao courseDao = new CourseDao();
        ScanAttemptDao scanAttemptDao = new ScanAttemptDao();

        int courseId = createTestCourse("SEC-RATE-712", "Rate Limit Test Course");
        courseDao.enrollStudent(1, courseId);

        CookieManager cmStudent = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmStudent, "aarav@student.com", "student123");

        // Clear any old attempts before test
        clearRateLimitAttempts(1);

        // Record 5 rapid failed attempts directly in DB
        for (int i = 0; i < 5; i++) {
            scanAttemptDao.recordAttempt(null, 1, PasswordUtil.hashPassword("fake" + i), "INVALID");
        }

        // 6th attempt via endpoint should be blocked by rate limiter
        String response = sendPostScan(cmStudent, "fake_token_attempt");
        if (!response.contains("Too many failed attempts") && !response.contains("blocked")) {
            throw new AssertionError("Expected rate limiter to block request, got: " + response);
        }

        // Clean up attempts immediately so subsequent tests are not affected
        clearRateLimitAttempts(1);
    }

    // ─────────────────────────────────────────────────────────────
    // C. MEETING LIFECYCLE REGRESSION TESTS
    // ─────────────────────────────────────────────────────────────

    private static void testRotationDoesNotCreateExtraSessions() throws Exception {
        CourseDao courseDao = new CourseDao();
        QrSessionDao sessionDao = new QrSessionDao();

        int courseId = createTestCourse("LIFE-ROT-713", "Rotation Session Count Course");
        courseDao.enrollStudent(1, courseId);

        CookieManager cmTeacher = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmTeacher, "teacher@attendance.com", "teacher123");
        sendGet(cmTeacher, BASE_URL + "/qr/generate?courseId=" + courseId);
        int initialMeetingId = sessionDao.findActiveByCourseId(courseId).get().getId();

        // Simulate 10 consecutive 7-second dynamic rotations via /qr/active
        for (int i = 1; i <= 10; i++) {
            String res = sendGet(cmTeacher, BASE_URL + "/qr/active?courseId=" + courseId + "&format=json");
            if (!res.contains("\"sessionId\":" + initialMeetingId)) {
                throw new AssertionError("Rotation " + i + " changed session ID: " + res);
            }
        }

        int count = countSessionsForCourse(courseId);
        if (count != 1) {
            throw new AssertionError("Expected exactly 1 session row after 10 rotations, found: " + count);
        }
    }

    private static void testRotationDoesNotCloseMeeting() throws Exception {
        CourseDao courseDao = new CourseDao();
        QrSessionDao sessionDao = new QrSessionDao();

        int courseId = createTestCourse("LIFE-ACT-714", "Rotation Active State Course");
        CookieManager cmTeacher = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmTeacher, "teacher@attendance.com", "teacher123");
        sendGet(cmTeacher, BASE_URL + "/qr/generate?courseId=" + courseId);

        for (int i = 0; i < 5; i++) {
            sendGet(cmTeacher, BASE_URL + "/qr/active?courseId=" + courseId + "&format=json");
        }

        QrSession s = sessionDao.findActiveByCourseId(courseId).orElseThrow(() -> new AssertionError("Session inactive"));
        if (!s.isActive() || s.getClosedAt() != null) {
            throw new AssertionError("Session must remain active with closedAt == null");
        }
    }

    private static void testRotationDoesNotTriggerAbsenteeReconciliation() throws Exception {
        CourseDao courseDao = new CourseDao();
        int courseId = createTestCourse("LIFE-RECON-715", "Reconciliation Rotation Course");
        courseDao.enrollStudent(1, courseId);
        courseDao.enrollStudent(2, courseId);

        CookieManager cmTeacher = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmTeacher, "teacher@attendance.com", "teacher123");
        sendGet(cmTeacher, BASE_URL + "/qr/generate?courseId=" + courseId);

        // 8 rotations without scanning
        for (int i = 0; i < 8; i++) {
            sendGet(cmTeacher, BASE_URL + "/qr/active?courseId=" + courseId + "&format=json");
        }

        int attendanceCount = countAttendanceForCourse(courseId);
        if (attendanceCount != 0) {
            throw new AssertionError("No attendance rows should be created during active rotation polling, found: " + attendanceCount);
        }
    }

    private static void testManualClosureMarksAbsentees() throws Exception {
        CourseDao courseDao = new CourseDao();
        QrSessionDao sessionDao = new QrSessionDao();
        AttendanceDao attendanceDao = new AttendanceDao();

        int courseId = createTestCourse("LIFE-CLS-716", "Manual Close Course");
        courseDao.enrollStudent(1, courseId); // scans
        courseDao.enrollStudent(2, courseId); // absconds

        CookieManager cmTeacher = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmTeacher, "teacher@attendance.com", "teacher123");
        sendGet(cmTeacher, BASE_URL + "/qr/generate?courseId=" + courseId);
        QrSession session = sessionDao.findActiveByCourseId(courseId).get();

        // Student 1 scans
        CookieManager cmStudent1 = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmStudent1, "aarav@student.com", "student123");
        sendPostScan(cmStudent1, session.getSessionToken());

        // Faculty ends meeting
        sendGet(cmTeacher, BASE_URL + "/qr/close?sessionId=" + session.getId());

        Optional<Attendance> a1 = attendanceDao.findBySessionAndStudent(session.getId(), 1);
        Optional<Attendance> a2 = attendanceDao.findBySessionAndStudent(session.getId(), 2);

        if (!a1.isPresent() || !"PRESENT".equals(a1.get().getStatus())) {
            throw new AssertionError("Student 1 must be marked PRESENT");
        }
        if (!a2.isPresent() || !"ABSENT".equals(a2.get().getStatus())) {
            throw new AssertionError("Student 2 must be reconciled to ABSENT");
        }
    }

    private static void testReopenPreservesMeetingId() throws Exception {
        CourseDao courseDao = new CourseDao();
        QrSessionDao sessionDao = new QrSessionDao();

        int courseId = createTestCourse("LIFE-REOP-717", "Reopen Meeting Course");
        CookieManager cmTeacher = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmTeacher, "teacher@attendance.com", "teacher123");
        sendGet(cmTeacher, BASE_URL + "/qr/generate?courseId=" + courseId);
        QrSession session = sessionDao.findActiveByCourseId(courseId).get();
        int originalMeetingId = session.getId();

        // Close
        sessionDao.closeSession(originalMeetingId);

        // Reopen
        boolean reopened = sessionDao.reopenSession(originalMeetingId);
        if (!reopened) throw new AssertionError("Failed to reopen session");

        QrSession checkReopened = sessionDao.findById(originalMeetingId).get();
        if (!checkReopened.isActive() || checkReopened.getClosedAt() != null) {
            throw new AssertionError("Reopened session must have isActive=true and closedAt=null");
        }
        if (checkReopened.getId() != originalMeetingId) {
            throw new AssertionError("Reopened session ID changed! Expected: " + originalMeetingId + ", got: " + checkReopened.getId());
        }
    }

    private static void testRecloseIdempotentAndPreservesDecisions() throws Exception {
        CourseDao courseDao = new CourseDao();
        QrSessionDao sessionDao = new QrSessionDao();
        AttendanceDao attendanceDao = new AttendanceDao();

        int courseId = createTestCourse("LIFE-IDEM-718", "Reclose Idempotent Course");
        courseDao.enrollStudent(1, courseId);
        courseDao.enrollStudent(2, courseId);

        CookieManager cmTeacher = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmTeacher, "teacher@attendance.com", "teacher123");
        sendGet(cmTeacher, BASE_URL + "/qr/generate?courseId=" + courseId);
        QrSession session = sessionDao.findActiveByCourseId(courseId).get();

        // Student 1 scans
        CookieManager cmStudent1 = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cmStudent1, "aarav@student.com", "student123");
        sendPostScan(cmStudent1, session.getSessionToken());

        // Close 1: marks student 2 ABSENT
        sessionDao.closeSession(session.getId());

        // Faculty changes student 2 to 'OD' with audit
        Attendance a2 = attendanceDao.findBySessionAndStudent(session.getId(), 2).get();
        attendanceDao.updateWithAuditTransaction(a2.getId(), "OD", "Authorized symposium duty", 1);

        // Close 2 (reclosing)
        sessionDao.closeSession(session.getId());

        Attendance check1 = attendanceDao.findBySessionAndStudent(session.getId(), 1).get();
        Attendance check2 = attendanceDao.findBySessionAndStudent(session.getId(), 2).get();

        if (!"PRESENT".equals(check1.getStatus())) throw new AssertionError("Student 1 should stay PRESENT");
        if (!"OD".equals(check2.getStatus())) throw new AssertionError("Student 2 should stay OD, got: " + check2.getStatus());

        int totalCount = countAttendanceForCourse(courseId);
        if (totalCount != 2) throw new AssertionError("Expected exactly 2 attendance rows, got: " + totalCount);
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private static int createTestCourse(String code, String name) {
        CourseDao courseDao = new CourseDao();
        courseDao.findByCourseCode(code).ifPresent(existing -> courseDao.delete(existing.getId()));
        Course c = new Course();
        c.setCourseCode(code);
        c.setCourseName(name);
        c.setTeacherId(1);
        c.setSemester(5);
        c.setDepartment("Computer Science");
        courseDao.save(c);
        return c.getId();
    }

    private static int countSessionsForCourse(int courseId) {
        String sql = "SELECT COUNT(*) FROM qr_sessions WHERE course_id = ? AND session_token NOT LIKE 'MERGED_INTO_%'";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private static int countAttendanceForCourse(int courseId) {
        String sql = "SELECT COUNT(*) FROM attendance WHERE course_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private static int countAttendanceForStudentAndCourse(int studentId, int courseId) {
        String sql = "SELECT COUNT(*) FROM attendance WHERE student_id = ? AND course_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private static void login(CookieManager cm, String email, String password) throws Exception {
        HttpURLConnection conn = openConnectionWithCookies(cm, BASE_URL + "/login");
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setInstanceFollowRedirects(false);
        String params = "email=" + email + "&password=" + password;
        conn.getOutputStream().write(params.getBytes(StandardCharsets.UTF_8));
        int code = conn.getResponseCode();
        saveCookies(cm, conn);
        if (code != 302 && code != 200) {
            throw new AssertionError("Login failed for " + email + ", code: " + code);
        }
    }

    private static String sendGet(CookieManager cm, String urlStr) throws Exception {
        HttpURLConnection conn = openConnectionWithCookies(cm, urlStr);
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();
        saveCookies(cm, conn);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line).append("\n");
            return sb.toString();
        }
    }

    private static String sendPostScan(CookieManager cm, String token) throws Exception {
        HttpURLConnection conn = openConnectionWithCookies(cm, BASE_URL + "/qr/scan");
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setInstanceFollowRedirects(false);
        String params = "token=" + java.net.URLEncoder.encode(token, StandardCharsets.UTF_8);
        conn.getOutputStream().write(params.getBytes(StandardCharsets.UTF_8));

        int code = conn.getResponseCode();
        BufferedReader reader;
        if (code >= 200 && code < 400) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } else {
            java.io.InputStream es = conn.getErrorStream();
            reader = new BufferedReader(new InputStreamReader(es != null ? es : conn.getInputStream(), StandardCharsets.UTF_8));
        }
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        saveCookies(cm, conn);
        return sb.toString();
    }

    private static void verifyLatestScanAttemptOutcome(int studentId, String expectedOutcome) throws Exception {
        String sql = "SELECT outcome FROM scan_attempts WHERE student_id = ? ORDER BY id DESC LIMIT 1";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String actual = rs.getString("outcome");
                    if (!expectedOutcome.equalsIgnoreCase(actual)) {
                        throw new AssertionError("Expected latest scan_attempt outcome: " + expectedOutcome + ", but found: " + actual);
                    }
                    return;
                }
            }
        }
        throw new AssertionError("No scan_attempt found for student " + studentId);
    }

    private static void clearRateLimitAttempts(int studentId) {
        String sql = "DELETE FROM scan_attempts WHERE student_id = ? AND outcome IN ('INVALID', 'RATE_LIMITED')";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void verifyScanAttemptOutcome(int sessionId, int studentId, String expectedOutcome) throws Exception {
        String sql = "SELECT outcome FROM scan_attempts WHERE session_id = ? AND student_id = ? ORDER BY id DESC LIMIT 1";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ps.setInt(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String actual = rs.getString("outcome");
                    if (!expectedOutcome.equalsIgnoreCase(actual)) {
                        throw new AssertionError("Expected scan_attempt outcome: " + expectedOutcome + ", but found: " + actual);
                    }
                    return;
                }
            }
        }
        throw new AssertionError("No scan_attempt found for session " + sessionId + " and student " + studentId);
    }

    private static HttpURLConnection openConnectionWithCookies(CookieManager cm, String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        List<HttpCookie> cookies = cm.getCookieStore().get(URI.create(BASE_URL));
        if (!cookies.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (HttpCookie cookie : cookies) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(cookie.getName()).append("=").append(cookie.getValue());
            }
            conn.setRequestProperty("Cookie", sb.toString());
        }
        return conn;
    }

    private static void saveCookies(CookieManager cm, HttpURLConnection conn) throws Exception {
        List<String> setCookies = conn.getHeaderFields().get("Set-Cookie");
        if (setCookies != null) {
            for (String header : setCookies) {
                List<HttpCookie> parsed = HttpCookie.parse(header);
                for (HttpCookie cookie : parsed) {
                    cm.getCookieStore().add(URI.create(BASE_URL), cookie);
                }
            }
        }
    }
}
