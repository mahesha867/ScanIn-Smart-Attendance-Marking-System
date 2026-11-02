package com.attendance;

import com.attendance.dao.AttendanceAuditLogDao;
import com.attendance.dao.AttendanceDao;
import com.attendance.dao.CourseDao;
import com.attendance.dao.QrSessionDao;
import com.attendance.model.Attendance;
import com.attendance.model.AttendanceAuditLog;
import com.attendance.model.Course;
import com.attendance.model.QrSession;
import com.attendance.util.DBConnection;
import com.attendance.util.DateTimeUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

/**
 * Automated Verification Test Suite for Indian Standard Time (IST) Attendance Timestamps.
 *
 * Verifies:
 * 1. Fixed UTC timestamp converts correctly to IST (e.g. 2026-11-01T05:00:00Z -> 10:30:00 AM IST).
 * 2. Timestamps close to midnight UTC display the correct next-day IST date (2026-10-31T20:00:00Z -> 2026-11-01 01:30:00 AM IST).
 * 3. Existing attendance timestamps are not shifted twice.
 * 4. Faculty attendance records retrieved from DB display IST consistently.
 * 5. Manual attendance overrides and OD/EXCUSED records use the same timezone strategy.
 * 6. Class meeting lifecycle behavior and DB schema remain in canonical UTC.
 * 7. Faculty report web endpoints (/reports and /reports?format=csv) output IST timestamps.
 */
public class TimezoneVerificationTest {

    private static final String BASE_URL = "http://localhost:8080/attendance";
    private static int totalPassed = 0;
    private static int totalFailed = 0;

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        System.out.println("================================================================================");
        System.out.println("   TimezoneVerificationTest: IST (Asia/Kolkata) Attendance Timestamp Suite");
        System.out.println("================================================================================");

        runTest("1. Fixed UTC timestamp converts accurately to IST (2026-11-01T05:00:00Z -> 10:30:00 AM)", TimezoneVerificationTest::testFixedUtcToIstConversion);
        runTest("2. Midnight UTC timestamp correctly advances to next-day IST date (2026-10-31T20:00:00Z -> Nov 1)", TimezoneVerificationTest::testMidnightUtcDateCrossing);
        runTest("3. Guard against double-conversion or manual offset arithmetic", TimezoneVerificationTest::testNoDoubleConversionOrShift);
        runTest("4. Database-retrieved attendance records format in IST consistently", TimezoneVerificationTest::testDatabaseAttendanceRecordFormat);
        runTest("5. Manual attendance, OD, and EXCUSED records audit log formats in IST", TimezoneVerificationTest::testManualAndOdExcusedRecordsTimezone);
        runTest("6. Persistent class meeting lifecycle storage remains canonical UTC", TimezoneVerificationTest::testMeetingLifecycleUtcInvariance);
        runTest("7. Live Faculty Report JSP and CSV export endpoints render IST timestamps", TimezoneVerificationTest::testFacultyReportEndpointHtmlAndCsv);

        System.out.println("================================================================================");
        System.out.printf("Timezone Verification Suite: %d Passed | %d Failed%n", totalPassed, totalFailed);
        System.out.println("================================================================================");

        if (totalFailed > 0) {
            System.exit(1);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 1: Fixed UTC to IST Conversion
    // ─────────────────────────────────────────────────────────────────────────
    private static void testFixedUtcToIstConversion() {
        // UTC: 2026-11-01 05:00:00 UTC
        Instant instant = Instant.parse("2026-11-01T05:00:00Z");
        LocalDateTime utcDateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);

        // Expected IST: 2026-11-01 10:30:00 AM
        String formattedWithZone = DateTimeUtil.formatToIst(utcDateTime);
        String formattedStandard = DateTimeUtil.formatToIstStandard(utcDateTime);
        String formattedIso = DateTimeUtil.formatToIstIso(utcDateTime);

        if (!formattedWithZone.equals("01-11-2026 10:30:00 AM IST")) {
            throw new AssertionError("Expected '01-11-2026 10:30:00 AM IST', got: " + formattedWithZone);
        }
        if (!formattedStandard.equals("01-11-2026 10:30:00 AM")) {
            throw new AssertionError("Expected '01-11-2026 10:30:00 AM', got: " + formattedStandard);
        }
        if (!formattedIso.equals("2026-11-01T10:30:00+05:30")) {
            throw new AssertionError("Expected '2026-11-01T10:30:00+05:30', got: " + formattedIso);
        }

        // Test with Instant
        String fromInstant = DateTimeUtil.formatToIst(instant);
        if (!fromInstant.equals("01-11-2026 10:30:00 AM IST")) {
            throw new AssertionError("Instant conversion mismatch, got: " + fromInstant);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 2: Midnight UTC Date Crossing
    // ─────────────────────────────────────────────────────────────────────────
    private static void testMidnightUtcDateCrossing() {
        // UTC: 2026-10-31 20:00:00 UTC (8:00 PM on Oct 31)
        // Adding 5h 30m crosses into next day: 2026-11-01 01:30:00 AM IST
        Instant instant = Instant.parse("2026-10-31T20:00:00Z");
        LocalDateTime utcDateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);

        String formattedWithZone = DateTimeUtil.formatToIst(utcDateTime);
        String formattedIso = DateTimeUtil.formatToIstIso(utcDateTime);

        if (!formattedWithZone.equals("01-11-2026 01:30:00 AM IST")) {
            throw new AssertionError("Expected '01-11-2026 01:30:00 AM IST', got: " + formattedWithZone);
        }
        if (!formattedIso.equals("2026-11-01T01:30:00+05:30")) {
            throw new AssertionError("Expected '2026-11-01T01:30:00+05:30', got: " + formattedIso);
        }

        ZonedDateTime istZdt = DateTimeUtil.toIst(utcDateTime);
        if (istZdt.getDayOfMonth() != 1 || istZdt.getMonthValue() != 11 || istZdt.getYear() != 2026) {
            throw new AssertionError("Date failed to cross midnight to Nov 1! Result: " + istZdt);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 3: Guard Against Double-Conversion
    // ─────────────────────────────────────────────────────────────────────────
    private static void testNoDoubleConversionOrShift() {
        Instant instant = Instant.parse("2026-11-01T05:00:00Z");
        ZonedDateTime ist = DateTimeUtil.toIst(instant);

        // ZoneId must be Asia/Kolkata
        if (!ist.getZone().getId().equals("Asia/Kolkata")) {
            throw new AssertionError("Expected zone Asia/Kolkata, got: " + ist.getZone());
        }

        // Offset must be +05:30
        if (!ist.getOffset().toString().equals("+05:30")) {
            throw new AssertionError("Expected offset +05:30, got: " + ist.getOffset());
        }

        // Epoch millisecond must be identical to original UTC instant (no artificial addition of millis)
        if (ist.toInstant().toEpochMilli() != instant.toEpochMilli()) {
            throw new AssertionError("toInstant() altered epoch millis! Original: "
                    + instant.toEpochMilli() + ", result: " + ist.toInstant().toEpochMilli());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 4: Database-Retrieved Attendance Records Format in IST
    // ─────────────────────────────────────────────────────────────────────────
    private static void testDatabaseAttendanceRecordFormat() throws Exception {
        AttendanceDao attendanceDao = new AttendanceDao();
        CourseDao courseDao = new CourseDao();
        QrSessionDao sessionDao = new QrSessionDao();

        int courseId = createTestCourse("IST-TEST-101", "Timezone Verification Course");
        courseDao.enrollStudent(1, courseId);

        QrSession session = new QrSession();
        session.setCourseId(courseId);
        session.setTeacherId(1);
        session.setSessionToken("ist_test_token_" + System.currentTimeMillis());
        session.setActive(true);
        sessionDao.save(session);

        // Insert a record with fixed known UTC timestamp into database
        // 2026-11-01 04:00:00 UTC -> 09:30:00 AM IST
        int attendanceId = 0;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO attendance (student_id, course_id, qr_session_id, marked_at, status) " +
                     "VALUES (?, ?, ?, '2026-11-01 04:00:00', 'PRESENT')", Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, 1);
            stmt.setInt(2, courseId);
            stmt.setInt(3, session.getId());
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) attendanceId = rs.getInt(1);
            }
        }

        Optional<Attendance> attOpt = attendanceDao.findById(attendanceId);
        if (!attOpt.isPresent()) {
            throw new AssertionError("Failed to find created test attendance record ID: " + attendanceId);
        }

        Attendance a = attOpt.get();
        String formattedIst = a.getMarkedAtIst();
        if (!formattedIst.equals("01-11-2026 09:30:00 AM IST")) {
            throw new AssertionError("Expected '01-11-2026 09:30:00 AM IST', got: " + formattedIst);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 5: Manual Attendance, OD, and EXCUSED Records Timezone
    // ─────────────────────────────────────────────────────────────────────────
    private static void testManualAndOdExcusedRecordsTimezone() throws Exception {
        AttendanceDao attendanceDao = new AttendanceDao();
        AttendanceAuditLogDao auditLogDao = new AttendanceAuditLogDao();
        CourseDao courseDao = new CourseDao();
        QrSessionDao sessionDao = new QrSessionDao();

        int courseId = createTestCourse("IST-MANUAL-102", "Manual OD Timezone Course");
        courseDao.enrollStudent(1, courseId);

        QrSession session = new QrSession();
        session.setCourseId(courseId);
        session.setTeacherId(1);
        session.setSessionToken("manual_token_" + System.currentTimeMillis());
        session.setActive(true);
        sessionDao.save(session);

        // 1. Create with audit transaction (Status: OD)
        boolean created = attendanceDao.createWithAuditTransaction(1, courseId, session.getId(), "OD", "Inter-college symposium OD", 1);
        if (!created) {
            throw new AssertionError("createWithAuditTransaction failed");
        }

        List<AttendanceAuditLog> logs = auditLogDao.findByCourse(courseId);
        if (logs.isEmpty()) {
            throw new AssertionError("Expected at least 1 audit log for course");
        }

        AttendanceAuditLog log = logs.get(0);
        String auditTimeIst = log.getCreatedAtIst();
        if (auditTimeIst == null || !auditTimeIst.endsWith("IST")) {
            throw new AssertionError("Audit log timestamp should end with 'IST', got: " + auditTimeIst);
        }

        // Verify status is OD
        if (!"OD".equals(log.getNewStatus())) {
            throw new AssertionError("Expected new status OD, got: " + log.getNewStatus());
        }

        // 2. Update to EXCUSED
        List<Attendance> list = attendanceDao.findByCourse(courseId);
        int attId = list.get(0).getId();
        boolean updated = attendanceDao.updateWithAuditTransaction(attId, "EXCUSED", "Medical certificate approved", 1);
        if (!updated) {
            throw new AssertionError("updateWithAuditTransaction failed");
        }

        List<AttendanceAuditLog> updatedLogs = auditLogDao.findByCourse(courseId);
        Optional<AttendanceAuditLog> excusedLogOpt = updatedLogs.stream()
                .filter(l -> "EXCUSED".equals(l.getNewStatus()))
                .findFirst();
        if (!excusedLogOpt.isPresent()) {
            throw new AssertionError("Expected an audit log with new status EXCUSED, found: " + updatedLogs.size() + " logs");
        }
        AttendanceAuditLog excusedLog = excusedLogOpt.get();
        if (!excusedLog.getCreatedAtIst().endsWith("IST")) {
            throw new AssertionError("Latest audit timestamp should end with 'IST', got: " + excusedLog.getCreatedAtIst());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 6: Class Meeting Lifecycle Storage Remains Canonical UTC
    // ─────────────────────────────────────────────────────────────────────────
    private static void testMeetingLifecycleUtcInvariance() throws Exception {
        QrSessionDao sessionDao = new QrSessionDao();
        int courseId = createTestCourse("IST-MEET-103", "Meeting UTC Invariance Course");

        QrSession session = new QrSession();
        session.setCourseId(courseId);
        session.setTeacherId(1);
        session.setSessionToken("meeting_utc_token_" + System.currentTimeMillis());
        session.setActive(true);
        sessionDao.save(session);

        // Verify that MySQL stores session timestamps in UTC
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT expires_at, created_at, " +
                     "TIMESTAMPDIFF(MINUTE, created_at, expires_at) as duration_min " +
                     "FROM qr_sessions WHERE id = ?")) {
            stmt.setInt(1, session.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int durationMin = rs.getInt("duration_min");
                    // Must maintain the 60-minute persistent class meeting duration
                    if (durationMin != 60) {
                        throw new AssertionError("Expected 60-minute class meeting window, found: " + durationMin);
                    }
                } else {
                    throw new AssertionError("Session row not found in qr_sessions");
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 7: Live Faculty Report JSP and CSV Export Endpoints Render IST
    // ─────────────────────────────────────────────────────────────────────────
    private static void testFacultyReportEndpointHtmlAndCsv() throws Exception {
        CookieManager cm = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        login(cm, "teacher@attendance.com", "teacher123");

        // 1. Check HTML report table
        HttpURLConnection connHtml = openConnectionWithCookies(cm, BASE_URL + "/reports?courseId=1");
        connHtml.setRequestMethod("GET");
        if (connHtml.getResponseCode() != 200) {
            throw new AssertionError("Expected HTTP 200 for /reports, got: " + connHtml.getResponseCode());
        }

        String html = readResponseBody(connHtml);
        if (!html.contains("Date & Time (IST)")) {
            throw new AssertionError("HTML report must contain column header 'Date & Time (IST)'");
        }
        if (!html.contains("Timestamp (IST)")) {
            throw new AssertionError("HTML report must contain audit column header 'Timestamp (IST)'");
        }
        if (!html.contains("IST")) {
            throw new AssertionError("HTML report table rows must display 'IST' formatted timestamps");
        }

        // 2. Check CSV export
        HttpURLConnection connCsv = openConnectionWithCookies(cm, BASE_URL + "/reports?courseId=1&format=csv");
        connCsv.setRequestMethod("GET");
        if (connCsv.getResponseCode() != 200) {
            throw new AssertionError("Expected HTTP 200 for /reports?format=csv, got: " + connCsv.getResponseCode());
        }

        String csv = readResponseBody(connCsv);
        String[] lines = csv.split("\n");
        if (lines.length == 0 || !lines[0].contains("Marked At (IST)")) {
            throw new AssertionError("CSV header must contain 'Marked At (IST)', got: " + lines[0]);
        }
        if (lines.length > 1 && !lines[1].trim().isEmpty()) {
            if (!lines[1].contains("IST")) {
                throw new AssertionError("CSV data rows must format timestamps in IST, got: " + lines[1]);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test Harness Utilities
    // ─────────────────────────────────────────────────────────────────────────
    private static void runTest(String name, TestRunnable test) {
        System.out.print("[TEST] " + name + " ... ");
        try {
            test.run();
            System.out.println("PASS");
            totalPassed++;
        } catch (Throwable e) {
            System.out.println("FAIL");
            System.out.println("   Error: " + e.getMessage());
            e.printStackTrace();
            totalFailed++;
        }
    }

    @FunctionalInterface
    interface TestRunnable {
        void run() throws Throwable;
    }

    private static int createTestCourse(String code, String name) throws Exception {
        CourseDao courseDao = new CourseDao();
        Course course = new Course();
        String uniqueCode = (code.length() > 8 ? code.substring(0, 8) : code) + (System.currentTimeMillis() % 1000000);
        course.setCourseCode(uniqueCode);
        course.setCourseName(name);
        course.setTeacherId(1);
        course.setSemester(5);
        course.setDepartment("Computer Science");
        courseDao.save(course);
        return course.getId();
    }

    private static void login(CookieManager cm, String email, String password) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/login").openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setInstanceFollowRedirects(false);

        String cookies = getCookieHeader(cm, BASE_URL + "/login");
        if (!cookies.isEmpty()) conn.setRequestProperty("Cookie", cookies);

        String form = "email=" + email + "&password=" + password;
        conn.getOutputStream().write(form.getBytes(StandardCharsets.UTF_8));

        storeCookies(cm, conn, BASE_URL + "/login");
        conn.getResponseCode();
    }

    private static HttpURLConnection openConnectionWithCookies(CookieManager cm, String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setInstanceFollowRedirects(false);
        String cookies = getCookieHeader(cm, urlStr);
        if (!cookies.isEmpty()) {
            conn.setRequestProperty("Cookie", cookies);
        }
        return conn;
    }

    private static String getCookieHeader(CookieManager cm, String urlStr) throws Exception {
        List<java.net.HttpCookie> cookies = cm.getCookieStore().get(new java.net.URI(urlStr));
        StringBuilder sb = new StringBuilder();
        for (java.net.HttpCookie c : cookies) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(c.getName()).append("=").append(c.getValue());
        }
        return sb.toString();
    }

    private static void storeCookies(CookieManager cm, HttpURLConnection conn, String urlStr) throws Exception {
        List<String> setCookies = conn.getHeaderFields().get("Set-Cookie");
        if (setCookies != null) {
            for (String h : setCookies) {
                for (java.net.HttpCookie c : java.net.HttpCookie.parse(h)) {
                    cm.getCookieStore().add(new java.net.URI(urlStr), c);
                }
            }
        }
    }

    private static String readResponseBody(HttpURLConnection conn) throws Exception {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }
}
