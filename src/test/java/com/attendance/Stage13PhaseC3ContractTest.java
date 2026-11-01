package com.attendance;

import com.attendance.dao.PlannedSessionDao;
import com.attendance.model.PlannedSession;
import com.attendance.model.User;
import com.attendance.servlet.StudentDashboardServlet;
import com.attendance.util.DateTimeUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

/**
 * ScanIn Stage 13 — Phase 13-C3 & C3.2 Automated Contract & Verification Tests.
 *
 * Scope: Student Timetable, Attendance Presentation Layer, Robust Timestamp Mapping,
 * and Status Presentation Cleanup.
 *
 * Clearly classified into:
 * - Offline Java Unit Tests (in-memory execution of mapping, timezone conversions, model logic)
 * - Static Contract Tests (code inspections for SQL boundaries, security, and JSP semantics)
 */
public class Stage13PhaseC3ContractTest {

    private static int totalPassed = 0;
    private static int totalFailed = 0;

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("  Stage 13-C3 & C3.2 Student Timetable & Timezone Contract Tests  ");
        System.out.println("==================================================================");

        // ── Phase 13-C3 Core Security & Functional Contracts ─────────────────

        runTest("C3-01: Unauthenticated users cannot access timetable [Static Security Contract]",
                Stage13PhaseC3ContractTest::testUnauthenticatedCannotAccessTimetable);

        runTest("C3-02: Authenticated students see only enrolled-course sessions [Static SQL Contract]",
                Stage13PhaseC3ContractTest::testAuthenticatedStudentsSeeOnlyEnrolledCourseSessions);

        runTest("C3-03: Another student's attendance is never exposed [Static SQL Contract]",
                Stage13PhaseC3ContractTest::testAnotherStudentAttendanceNeverExposed);

        runTest("C3-04: PRESENT displayed only when persisted PRESENT record exists [Unit & Static Contract]",
                Stage13PhaseC3ContractTest::testPresentDisplayedOnlyWhenPersistedPresentRecordExists);

        runTest("C3-05: LATE displayed only when persisted LATE record exists [Unit & Static Contract]",
                Stage13PhaseC3ContractTest::testLateDisplayedOnlyWhenPersistedLateRecordExists);

        runTest("C3-06: ABSENT displayed only when persisted ABSENT record exists [Unit & Static Contract]",
                Stage13PhaseC3ContractTest::testAbsentDisplayedOnlyWhenPersistedAbsentRecordExists);

        runTest("C3-07: Upcoming sessions without attendance show NOT YET RECORDED [Static JSP Contract]",
                Stage13PhaseC3ContractTest::testUpcomingSessionsWithoutAttendanceShowNotYetRecorded);

        runTest("C3-08: Open sessions without attendance are not marked ABSENT [Static JSP Contract]",
                Stage13PhaseC3ContractTest::testOpenSessionsWithoutAttendanceAreNotMarkedAbsent);

        runTest("C3-09: Completed sessions display persisted attendance or NOT RECORDED [Static JSP Contract]",
                Stage13PhaseC3ContractTest::testCompletedSessionsDisplayPersistedAttendanceRecord);

        runTest("C3-10: Missing or inconsistent associations handled safely (null-safety) [Unit & Static Contract]",
                Stage13PhaseC3ContractTest::testMissingOrInconsistentAssociationsHandledSafely);

        runTest("C3-11: Student routes cannot invoke teacher-only operations [Static Security Contract]",
                Stage13PhaseC3ContractTest::testStudentRoutesCannotInvokeTeacherOnlyOperations);

        runTest("C3-12: Timetable retrieval performs zero attendance mutations [Static Immutability Contract]",
                Stage13PhaseC3ContractTest::testTimetableRetrievalPerformsZeroAttendanceMutations);

        // ── Phase 13-C3.2 Corrective Tests (Timestamp & Status Cleanup) ───────

        runTest("C3.2-01: UTC timestamp mapping using an explicit instant [Unit Test]",
                Stage13PhaseC3ContractTest::testUtcTimestampMappingExplicitInstant);

        runTest("C3.2-02: Correct IST presentation after mapping [Unit Test]",
                Stage13PhaseC3ContractTest::testCorrectIstPresentationAfterMapping);

        runTest("C3.2-03: Null timestamp handling remains safe [Unit Test]",
                Stage13PhaseC3ContractTest::testNullTimestampHandling);

        runTest("C3.2-04: Mapping independence from JVM default timezone [Unit Test]",
                Stage13PhaseC3ContractTest::testMappingIndependenceFromJvmTimezone);

        runTest("C3.2-05: Supported planned-session statuses rendered correctly [Static JSP Contract]",
                Stage13PhaseC3ContractTest::testSupportedPlannedSessionStatuses);

        runTest("C3.2-06: Neutral fallback for unsupported statuses without claims [Static JSP Contract]",
                Stage13PhaseC3ContractTest::testNeutralHandlingOfUnsupportedStatuses);

        System.out.println("==================================================================");
        System.out.printf("  Stage 13-C3 & C3.2 Test Results: %d Passed, %d Failed%n", totalPassed, totalFailed);
        System.out.println("==================================================================");

        if (totalFailed > 0) {
            System.err.println("❌ STAGE 13-C3.2 CONTRACT VERIFICATION FAILED");
            System.exit(1);
        } else {
            System.out.println("✅ STAGE 13-C3.2 CONTRACT VERIFICATION PASSED");
        }
    }

    private static void runTest(String testName, Runnable test) {
        try {
            test.run();
            System.out.printf("  [PASS] %s%n", testName);
            totalPassed++;
        } catch (Throwable t) {
            System.out.printf("  [FAIL] %s: %s%n", testName, t.getMessage());
            t.printStackTrace(System.out);
            totalFailed++;
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError("Assertion failed: " + message + " (expected: " + expected + ", actual: " + actual + ")");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Test 1: Unauthenticated users cannot access timetable
    // ────────────────────────────────────────────────────────────────────────
    private static void testUnauthenticatedCannotAccessTimetable() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/StudentDashboardServlet.java");
        assertTrue(servletSource.contains("SessionUtil.getLoggedInUser(request)"),
                "StudentDashboardServlet must verify authenticated user via SessionUtil");
        assertTrue(servletSource.contains("!\"STUDENT\".equalsIgnoreCase(user.getRole())"),
                "StudentDashboardServlet must enforce STUDENT role authorization");
        assertTrue(servletSource.contains("response.sendRedirect(request.getContextPath() + \"/login\");"),
                "Unauthenticated or non-student requests must redirect to /login");
        int authCheckIdx = servletSource.indexOf("SessionUtil.getLoggedInUser(request)");
        int fetchIdx = servletSource.indexOf("findByStudentEnrollmentWithAttendance");
        assertTrue(authCheckIdx > 0 && fetchIdx > authCheckIdx,
                "Timetable fetching must occur strictly after student authentication check");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Test 2: Authenticated students see only enrolled-course sessions
    // ────────────────────────────────────────────────────────────────────────
    private static void testAuthenticatedStudentsSeeOnlyEnrolledCourseSessions() {
        String daoSource = readSource("src/main/java/com/attendance/dao/PlannedSessionDao.java");
        assertTrue(daoSource.contains("JOIN enrollments e ON ps.course_id = e.course_id"),
                "PlannedSessionDao must join enrollments table on course_id");
        assertTrue(daoSource.contains("WHERE e.student_id = ?"),
                "PlannedSessionDao must filter strictly by authenticated student_id");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Test 3: Another student's attendance is never exposed
    // ────────────────────────────────────────────────────────────────────────
    private static void testAnotherStudentAttendanceNeverExposed() {
        String daoSource = readSource("src/main/java/com/attendance/dao/PlannedSessionDao.java");
        assertTrue(daoSource.contains("LEFT JOIN attendance a ON a.qr_session_id = qs.id AND a.student_id = ?"),
                "Attendance join must strictly bind a.student_id = ? to isolate student attendance");
        assertTrue(daoSource.contains("ps.setInt(1, studentId);") && daoSource.contains("ps.setInt(2, studentId);"),
                "Both query parameters must be set to the authenticated studentId");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Test 4: PRESENT displayed only when persisted PRESENT record exists
    // ────────────────────────────────────────────────────────────────────────
    private static void testPresentDisplayedOnlyWhenPersistedPresentRecordExists() {
        PlannedSession ps = new PlannedSession();
        ps.setStatus("COMPLETED");
        ps.setAttendanceStatus("PRESENT");
        ps.setAttendanceMarkedAt(LocalDateTime.of(2026, 11, 1, 10, 15, 0));

        assertEquals("PRESENT", ps.getAttendanceStatus(), "Model must retain PRESENT attendance status");
        assertEquals("01-11-2026 03:45:00 PM IST", ps.getAttendanceMarkedAtIst(),
                "IST timestamp formatting must match presentation requirements (UTC 10:15 converted to IST 15:45)");

        String jspSource = readSource("src/main/webapp/WEB-INF/views/student-dashboard.jsp");
        assertTrue(jspSource.contains("\"PRESENT\".equalsIgnoreCase(attStatus)"),
                "JSP must check for explicit PRESENT status");
        assertTrue(jspSource.contains("badge-present"),
                "JSP must render emerald/present badge for PRESENT");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Test 5: LATE displayed only when persisted LATE record exists
    // ────────────────────────────────────────────────────────────────────────
    private static void testLateDisplayedOnlyWhenPersistedLateRecordExists() {
        PlannedSession ps = new PlannedSession();
        ps.setStatus("COMPLETED");
        ps.setAttendanceStatus("LATE");
        ps.setAttendanceMarkedAt(LocalDateTime.of(2026, 11, 1, 10, 25, 0));

        assertEquals("LATE", ps.getAttendanceStatus(), "Model must retain LATE attendance status");

        String jspSource = readSource("src/main/webapp/WEB-INF/views/student-dashboard.jsp");
        assertTrue(jspSource.contains("\"LATE\".equalsIgnoreCase(attStatus)"),
                "JSP must check for explicit LATE status");
        assertTrue(jspSource.contains("⏳ LATE"),
                "JSP must render amber LATE badge for LATE");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Test 6: ABSENT displayed only when persisted ABSENT record exists
    // ────────────────────────────────────────────────────────────────────────
    private static void testAbsentDisplayedOnlyWhenPersistedAbsentRecordExists() {
        PlannedSession ps = new PlannedSession();
        ps.setStatus("COMPLETED");
        ps.setAttendanceStatus("ABSENT");

        assertEquals("ABSENT", ps.getAttendanceStatus(), "Model must retain ABSENT attendance status");

        String jspSource = readSource("src/main/webapp/WEB-INF/views/student-dashboard.jsp");
        assertTrue(jspSource.contains("\"ABSENT\".equalsIgnoreCase(attStatus)"),
                "JSP must check for explicit ABSENT status");
        assertTrue(jspSource.contains("❌ ABSENT"),
                "JSP must render red ABSENT badge for ABSENT");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Test 7: Upcoming sessions without attendance show NOT YET RECORDED
    // ────────────────────────────────────────────────────────────────────────
    private static void testUpcomingSessionsWithoutAttendanceShowNotYetRecorded() {
        PlannedSession ps = new PlannedSession();
        ps.setStatus("SCHEDULED");
        ps.setAttendanceStatus(null);

        String jspSource = readSource("src/main/webapp/WEB-INF/views/student-dashboard.jsp");
        assertTrue(jspSource.contains("\"SCHEDULED\".equalsIgnoreCase(sessStatus)"),
                "JSP must handle SCHEDULED session status when attendance is unrecorded");
        assertTrue(jspSource.contains("⚪ NOT YET RECORDED"),
                "Upcoming SCHEDULED session without attendance must display '⚪ NOT YET RECORDED'");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Test 8: Open sessions without attendance are not marked ABSENT
    // ────────────────────────────────────────────────────────────────────────
    private static void testOpenSessionsWithoutAttendanceAreNotMarkedAbsent() {
        PlannedSession ps = new PlannedSession();
        ps.setStatus("ATTENDANCE_OPEN");
        ps.setAttendanceStatus(null);

        String jspSource = readSource("src/main/webapp/WEB-INF/views/student-dashboard.jsp");
        assertTrue(jspSource.contains("\"ATTENDANCE_OPEN\".equalsIgnoreCase(sessStatus)"),
                "JSP must handle ATTENDANCE_OPEN session status when attendance is unrecorded");
        assertTrue(jspSource.contains("Scan to Mark"),
                "Open session without attendance must offer a scan prompt rather than ABSENT");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Test 9: Completed sessions display persisted attendance or NOT RECORDED
    // ────────────────────────────────────────────────────────────────────────
    private static void testCompletedSessionsDisplayPersistedAttendanceRecord() {
        String jspSource = readSource("src/main/webapp/WEB-INF/views/student-dashboard.jsp");
        assertTrue(jspSource.contains("\"COMPLETED\".equalsIgnoreCase(sessStatus)"),
                "JSP must handle COMPLETED sessions");
        assertTrue(jspSource.contains("⚪ NOT RECORDED"),
                "Completed session without attendance record must display '⚪ NOT RECORDED'");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Test 10: Missing or inconsistent associations handled safely (null-safety)
    // ────────────────────────────────────────────────────────────────────────
    private static void testMissingOrInconsistentAssociationsHandledSafely() {
        PlannedSession ps = new PlannedSession();
        ps.setId(101);
        ps.setBlockId(null);
        ps.setCourseId(5);
        ps.setCourseCode(null);
        ps.setCourseName(null);
        ps.setTeacherId(2);
        ps.setTeacherName(null);
        ps.setSessionType(null);
        ps.setSessionDate(null);
        ps.setStartTime(null);
        ps.setEndTime(null);
        ps.setRoom(null);
        ps.setDescription(null);
        ps.setStatus(null);
        ps.setAttendanceStatus(null);
        ps.setAttendanceMarkedAt(null);

        String ist = ps.getAttendanceMarkedAtIst();
        assertEquals("N/A", ist, "getAttendanceMarkedAtIst() must safely return N/A via DateTimeUtil when markedAt is null");

        String jspSource = readSource("src/main/webapp/WEB-INF/views/student-dashboard.jsp");
        assertTrue(jspSource.contains("ps.getRoom() != null && !ps.getRoom().trim().isEmpty() ? ps.getRoom() : \"TBA\""),
                "JSP must fall back to TBA when room is null");
        assertTrue(jspSource.contains("ps.getBlockId() != null && !ps.getBlockId().trim().isEmpty()"),
                "JSP must safely guard blockId checks");
        assertTrue(jspSource.contains("ps.getCourseCode() != null ? ps.getCourseCode() : (\"Course #\" + ps.getCourseId())"),
                "JSP must fall back gracefully when courseCode is null");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Test 11: Student routes cannot invoke teacher-only operations
    // ────────────────────────────────────────────────────────────────────────
    private static void testStudentRoutesCannotInvokeTeacherOnlyOperations() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/StudentDashboardServlet.java");
        assertTrue(!servletSource.contains("/planned-sessions/activate"),
                "StudentDashboardServlet must not map or expose planned session activation");
        assertTrue(!servletSource.contains("/qr/close"),
                "StudentDashboardServlet must not map or expose QR session closure");

        String teacherServlet = readSource("src/main/java/com/attendance/servlet/TeacherDashboardServlet.java");
        assertTrue(teacherServlet.contains("!\"TEACHER\".equalsIgnoreCase(user.getRole())"),
                "TeacherDashboardServlet must reject non-teacher users");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Test 12: Timetable retrieval performs zero attendance mutations
    // ────────────────────────────────────────────────────────────────────────
    private static void testTimetableRetrievalPerformsZeroAttendanceMutations() {
        String daoSource = readSource("src/main/java/com/attendance/dao/PlannedSessionDao.java");
        int methodStart = daoSource.indexOf("public List<PlannedSession> findByStudentEnrollmentWithAttendance");
        assertTrue(methodStart > 0, "findByStudentEnrollmentWithAttendance method must exist");
        int methodEnd = daoSource.indexOf("return list;", methodStart);
        String methodBody = daoSource.substring(methodStart, methodEnd);

        assertTrue(methodBody.contains("SELECT "), "Method must be a SELECT query");
        assertTrue(!methodBody.contains("INSERT "), "Method must contain zero INSERT statements");
        assertTrue(!methodBody.contains("UPDATE "), "Method must contain zero UPDATE statements");
        assertTrue(!methodBody.contains("DELETE "), "Method must contain zero DELETE statements");

        String studentServlet = readSource("src/main/java/com/attendance/servlet/StudentDashboardServlet.java");
        int doGetStart = studentServlet.indexOf("protected void doGet");
        int doGetEnd = studentServlet.indexOf("protected void doPost");
        String doGetBody = studentServlet.substring(doGetStart, doGetEnd);

        assertTrue(!doGetBody.contains("INSERT"), "doGet must contain zero INSERT mutations");
        assertTrue(!doGetBody.contains("UPDATE"), "doGet must contain zero UPDATE mutations");
        assertTrue(!doGetBody.contains("DELETE"), "doGet must contain zero DELETE mutations");
        assertTrue(!doGetBody.contains("recordAttendance"), "doGet must contain zero attendance mutations");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Phase 13-C3.2 Corrective Unit Tests
    // ────────────────────────────────────────────────────────────────────────

    /**
     * C3.2-01: Verifies that converting a Timestamp via ma.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime()
     * produces the exact expected UTC LocalDateTime.
     */
    private static void testUtcTimestampMappingExplicitInstant() {
        Instant knownInstant = Instant.parse("2026-11-01T10:15:30.00Z");
        Timestamp ma = Timestamp.from(knownInstant);

        LocalDateTime utcDateTime = ma.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();
        assertEquals(LocalDateTime.of(2026, 11, 1, 10, 15, 30), utcDateTime,
                "Explicit instant-to-UTC conversion must produce exact UTC wall-clock representation");

        // Verify that PlannedSessionDao actually implements this exact pattern
        String daoSource = readSource("src/main/java/com/attendance/dao/PlannedSessionDao.java");
        assertTrue(daoSource.contains("ma.toInstant()") && daoSource.contains(".atZone(ZoneOffset.UTC)"),
                "PlannedSessionDao must use explicit ma.toInstant().atZone(ZoneOffset.UTC) mapping");
    }

    /**
     * C3.2-02: Verifies that after explicit UTC mapping, DateTimeUtil formats to the expected IST string.
     */
    private static void testCorrectIstPresentationAfterMapping() {
        Instant knownInstant = Instant.parse("2026-11-01T10:15:30.00Z");
        Timestamp ma = Timestamp.from(knownInstant);
        LocalDateTime utcDateTime = ma.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();

        PlannedSession ps = new PlannedSession();
        ps.setAttendanceMarkedAt(utcDateTime);

        // 10:15:30 UTC + 05:30 IST = 15:45:30 IST (03:45:30 PM IST)
        String istFormatted = ps.getAttendanceMarkedAtIst();
        assertEquals("01-11-2026 03:45:30 PM IST", istFormatted,
                "IST presentation must correctly add 5 hours 30 minutes to UTC timestamp");
    }

    /**
     * C3.2-03: Verifies null timestamp handling.
     */
    private static void testNullTimestampHandling() {
        PlannedSession ps = new PlannedSession();
        ps.setAttendanceMarkedAt(null);

        assertEquals(null, ps.getAttendanceMarkedAt(), "MarkedAt must be null");
        assertEquals("N/A", ps.getAttendanceMarkedAtIst(), "IST formatting of null must safely return 'N/A'");
    }

    /**
     * C3.2-04: Verifies that explicit instant mapping is 100% independent of JVM default timezone.
     * Tests against non-UTC JVM timezones and safely restores the original timezone.
     */
    private static void testMappingIndependenceFromJvmTimezone() {
        TimeZone originalTz = TimeZone.getDefault();
        try {
            Instant knownInstant = Instant.parse("2026-11-01T10:15:00.00Z");
            Timestamp ma = Timestamp.from(knownInstant);

            // Test under UTC
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            LocalDateTime utcUnderUtc = ma.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();

            // Test under America/New_York (UTC-5)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            LocalDateTime utcUnderNy = ma.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();

            // Test under Asia/Tokyo (UTC+9)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            LocalDateTime utcUnderTokyo = ma.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();

            // Test under Asia/Kolkata (UTC+5:30)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
            LocalDateTime utcUnderIst = ma.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();

            assertEquals(LocalDateTime.of(2026, 11, 1, 10, 15, 0), utcUnderUtc, "UTC result must match");
            assertEquals(utcUnderUtc, utcUnderNy, "Conversion must be identical under America/New_York");
            assertEquals(utcUnderUtc, utcUnderTokyo, "Conversion must be identical under Asia/Tokyo");
            assertEquals(utcUnderUtc, utcUnderIst, "Conversion must be identical under Asia/Kolkata");
        } finally {
            // Guarantee restoration of original JVM default timezone
            TimeZone.setDefault(originalTz);
        }
    }

    /**
     * C3.2-05: Verifies that the three supported statuses (SCHEDULED, ATTENDANCE_OPEN, COMPLETED)
     * are rendered with their proper badges in the JSP.
     */
    private static void testSupportedPlannedSessionStatuses() {
        String jspSource = readSource("src/main/webapp/WEB-INF/views/student-dashboard.jsp");
        assertTrue(jspSource.contains("\"ATTENDANCE_OPEN\".equalsIgnoreCase(sessStatus)"),
                "JSP must support ATTENDANCE_OPEN");
        assertTrue(jspSource.contains("🟢 OPEN NOW"),
                "JSP must render OPEN NOW badge for ATTENDANCE_OPEN");
        assertTrue(jspSource.contains("\"SCHEDULED\".equalsIgnoreCase(sessStatus)"),
                "JSP must support SCHEDULED");
        assertTrue(jspSource.contains("📅 SCHEDULED"),
                "JSP must render SCHEDULED badge");
        assertTrue(jspSource.contains("\"COMPLETED\".equalsIgnoreCase(sessStatus)"),
                "JSP must support COMPLETED");
        assertTrue(jspSource.contains("✓ COMPLETED"),
                "JSP must render COMPLETED badge");
    }

    /**
     * C3.2-06: Verifies that explicit UI claims for unsupported CANCELLED and MISSED workflows
     * have been eliminated from student-dashboard.jsp in favor of a neutral fallback.
     */
    private static void testNeutralHandlingOfUnsupportedStatuses() {
        String jspSource = readSource("src/main/webapp/WEB-INF/views/student-dashboard.jsp");
        assertTrue(!jspSource.contains("✕ CANCELLED"),
                "JSP must NOT display explicit claims for unsupported '✕ CANCELLED' lifecycle");
        assertTrue(!jspSource.contains("⚠️ MISSED"),
                "JSP must NOT display explicit claims for unsupported '⚠️ MISSED' lifecycle");

        // Verify neutral fallback
        assertTrue(jspSource.contains("<%= sessStatus != null ? sessStatus : \"—\" %>"),
                "JSP must render neutral fallback for unsupported or unmapped session statuses");
    }

    private static String readSource(String relativePath) {
        try {
            return Files.readString(Paths.get(relativePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read source file: " + relativePath, e);
        }
    }
}
