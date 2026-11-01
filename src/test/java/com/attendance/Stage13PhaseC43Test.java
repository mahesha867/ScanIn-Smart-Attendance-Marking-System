package com.attendance;

import com.attendance.model.SessionRosterItem;
import com.attendance.model.SessionRosterMetrics;
import com.attendance.util.DateTimeUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * ScanIn Stage 13 — Phase 13-C4.3: Teacher Attendance Roster Backend Test Suite.
 *
 * Scope:
 * - Authorization & Teacher Ownership Verification
 * - Parameter Input Validation & Error Mappings
 * - Parameterized SQL Contracts & Cardinality Verification
 * - Attendance Presentation State Machine (Valid & Inconsistent combinations)
 * - Deterministic Metrics Calculations & Boundary Conditions
 * - UTC-to-IST Instant Timestamp Formatting
 * - Read-Only Immutability Verification
 *
 * Classifications:
 * - Pure Java Unit Test: Deterministic evaluation of state machines, models, and metric algorithms.
 * - Static Contract Test: Structural invariant validation against application source code.
 */
public class Stage13PhaseC43Test {

    private static int totalPassed = 0;
    private static int totalFailed = 0;

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("  Stage 13-C4.3 Teacher Attendance Roster Backend Tests           ");
        System.out.println("==================================================================");

        // ── 1. Authorization Contracts ──────────────────────────────────────
        runTest("C4.3-01: Unauthenticated request rejected [Static Contract Test]",
                Stage13PhaseC43Test::testUnauthenticatedRequestRejected);

        runTest("C4.3-02: Student role rejected with HTTP 403 [Static Contract Test]",
                Stage13PhaseC43Test::testStudentRoleRejectedWith403);

        runTest("C4.3-03: Authenticated teacher can request an owned session [Static Contract Test]",
                Stage13PhaseC43Test::testTeacherCanRequestOwnedSession);

        runTest("C4.3-04: Teacher cannot request another teacher's session (HTTP 403) [Static Contract Test]",
                Stage13PhaseC43Test::testTeacherCannotRequestAnotherTeachersSession);

        runTest("C4.3-05: Client-supplied teacher ID cannot override session identity [Static Contract Test]",
                Stage13PhaseC43Test::testClientTeacherIdCannotOverrideSessionIdentity);

        // ── 2. Input Validation Contracts ───────────────────────────────────
        runTest("C4.3-06: Missing plannedSessionId rejected with HTTP 400 [Static Contract Test]",
                Stage13PhaseC43Test::testMissingPlannedSessionIdRejectedWith400);

        runTest("C4.3-07: Malformed plannedSessionId rejected with HTTP 400 [Static Contract Test]",
                Stage13PhaseC43Test::testMalformedPlannedSessionIdRejectedWith400);

        runTest("C4.3-08: Zero and negative identifiers rejected with HTTP 400 [Static Contract Test]",
                Stage13PhaseC43Test::testZeroAndNegativeIdentifiersRejectedWith400);

        runTest("C4.3-09: Nonexistent session returns HTTP 404 [Static Contract Test]",
                Stage13PhaseC43Test::testNonexistentSessionReturns404);

        // ── 3. SQL Contracts & Safety ───────────────────────────────────────
        runTest("C4.3-10: Query uses planned session ID and teacher ID placeholders [Static Contract Test]",
                Stage13PhaseC43Test::testQueryUsesSessionAndTeacherPlaceholders);

        runTest("C4.3-11: Query joins only enrolled students [Static Contract Test]",
                Stage13PhaseC43Test::testQueryJoinsOnlyEnrolledStudents);

        runTest("C4.3-12: QR and attendance are LEFT JOINed [Static Contract Test]",
                Stage13PhaseC43Test::testQrAndAttendanceAreLeftJoined);

        runTest("C4.3-13: Attendance is filtered by both QR session and student [Static Contract Test]",
                Stage13PhaseC43Test::testAttendanceFilteredByQrSessionAndStudent);

        runTest("C4.3-14: Student email is not retrieved in roster query [Static Contract Test]",
                Stage13PhaseC43Test::testStudentEmailNotRetrievedInQuery);

        runTest("C4.3-15: No mutation statements are present in roster DAO [Static Contract Test]",
                Stage13PhaseC43Test::testNoMutationStatementsPresentInRosterDao);

        // ── 4. Presentation State Machine (Unit Tests) ──────────────────────
        runTest("C4.3-16: SCHEDULED without QR maps to NOT ACTIVATED [Pure Java Unit Test]",
                Stage13PhaseC43Test::testScheduledWithoutQrMapsToNotActivated);

        runTest("C4.3-17: ATTENDANCE_OPEN without attendance maps to NOT YET RECORDED [Pure Java Unit Test]",
                Stage13PhaseC43Test::testAttendanceOpenWithoutAttendanceMapsToNotYetRecorded);

        runTest("C4.3-18: PRESENT remains PRESENT [Pure Java Unit Test]",
                Stage13PhaseC43Test::testPresentRemainsPresent);

        runTest("C4.3-19: LATE remains LATE [Pure Java Unit Test]",
                Stage13PhaseC43Test::testLateRemainsLate);

        runTest("C4.3-20: ABSENT remains ABSENT [Pure Java Unit Test]",
                Stage13PhaseC43Test::testAbsentRemainsAbsent);

        runTest("C4.3-21: COMPLETED without attendance maps to NOT RECORDED [Pure Java Unit Test]",
                Stage13PhaseC43Test::testCompletedWithoutAttendanceMapsToNotRecorded);

        runTest("C4.3-22: Invalid lifecycle combinations identified as inconsistent [Pure Java Unit Test]",
                Stage13PhaseC43Test::testInvalidLifecycleCombinationsIdentifiedAsInconsistent);

        // ── 5. Metrics Calculations (Unit Tests) ────────────────────────────
        runTest("C4.3-23: Present, Late, Absent, and Not Recorded counts are correct [Pure Java Unit Test]",
                Stage13PhaseC43Test::testMetricsCountsCorrect);

        runTest("C4.3-24: Percentage calculation is (Present + Late) / Total * 100 [Pure Java Unit Test]",
                Stage13PhaseC43Test::testPercentageCalculationCorrect);

        runTest("C4.3-25: Zero enrollment returns N/A percentage [Pure Java Unit Test]",
                Stage13PhaseC43Test::testZeroEnrollmentReturnsNa);

        runTest("C4.3-26: SCHEDULED without QR returns N/A percentage [Pure Java Unit Test]",
                Stage13PhaseC43Test::testScheduledWithoutQrReturnsNa);

        runTest("C4.3-27: ATTENDANCE_OPEN indicates in-progress attendance [Pure Java Unit Test]",
                Stage13PhaseC43Test::testAttendanceOpenIndicatesInProgress);

        runTest("C4.3-28: Inconsistent records flagged without inventing ABSENT [Pure Java Unit Test]",
                Stage13PhaseC43Test::testInconsistentRecordsFlaggedWithoutInventingAbsent);

        // ── 6. Timestamp & Immutability ─────────────────────────────────────
        runTest("C4.3-29: Timestamp mapping uses UTC instant conversion [Pure Java Unit & Static Test]",
                Stage13PhaseC43Test::testTimestampMappingUsesUtcInstantConversion);

        runTest("C4.3-30: IST formatting reuses DateTimeUtil.formatToIst() [Pure Java Unit & Static Test]",
                Stage13PhaseC43Test::testIstFormattingReusesDateTimeUtil);

        runTest("C4.3-31: The roster retrieval path performs zero writes [Static Contract Test]",
                Stage13PhaseC43Test::testRosterRetrievalPerformsNoWrites);

        runTest("C4.3-32: Existing C4.2 closure methods remain unchanged [Static Contract Test]",
                Stage13PhaseC43Test::testExistingC42ClosureMethodsUnchanged);

        // ── 7. C4.3.4 Consistency Corrections ────────────────────────────────
        runTest("C4.3-33: QR active state is selected and mapped in roster DAO [Static Contract Test]",
                Stage13PhaseC43Test::testQrActiveStateSelectedAndMappedInDao);

        runTest("C4.3-34: Inconsistency invalidates attendance percentage [Pure Java Unit Test]",
                Stage13PhaseC43Test::testRosterInconsistencyInvalidatesPercentage);

        runTest("C4.3-35: Course metadata resolved from trusted CourseDao [Static Contract Test]",
                Stage13PhaseC43Test::testCourseMetadataResolvedFromCourseDao);

        runTest("C4.3-36: Null attendance percentage serialized as literal null in JSON [Static Contract Test]",
                Stage13PhaseC43Test::testNullPercentageSerializedAsLiteralNullInJson);

        runTest("C4.3-37: ATTENDANCE_OPEN with inconsistencies preserves inProgress=true and null percentage [Pure Java Unit Test]",
                Stage13PhaseC43Test::testAttendanceOpenWithInconsistencyPreservesInProgress);

        System.out.println("==================================================================");
        System.out.printf("  Stage 13-C4.3 Test Results: %d Passed, %d Failed%n", totalPassed, totalFailed);
        System.out.println("==================================================================");

        if (totalFailed > 0) {
            System.err.println("❌ STAGE 13-C4.3 CONTRACT VERIFICATION FAILED");
            System.exit(1);
        } else {
            System.out.println("✅ STAGE 13-C4.3 CONTRACT VERIFICATION PASSED");
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
    // 1. Authorization Contracts
    // ────────────────────────────────────────────────────────────────────────

    private static void testUnauthenticatedRequestRejected() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/TeacherDashboardServlet.java");
        int doGetIdx = servletSource.indexOf("protected void doGet(");
        int userCheckIdx = servletSource.indexOf("SessionUtil.getLoggedInUser(request);", doGetIdx);
        assertTrue(userCheckIdx > doGetIdx, "doGet must query session user");

        int nullCheckIdx = servletSource.indexOf("if (user == null)", userCheckIdx);
        int redirectIdx = servletSource.indexOf("response.sendRedirect(request.getContextPath() + \"/login\");", nullCheckIdx);
        assertTrue(nullCheckIdx > userCheckIdx && redirectIdx > nullCheckIdx,
                "Unauthenticated user must be redirected to /login");
    }

    private static void testStudentRoleRejectedWith403() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/TeacherDashboardServlet.java");
        int doGetIdx = servletSource.indexOf("protected void doGet(");
        int roleCheckIdx = servletSource.indexOf("if (!\"TEACHER\".equalsIgnoreCase(user.getRole()))", doGetIdx);
        assertTrue(roleCheckIdx > doGetIdx, "doGet must verify TEACHER role");

        int forbiddenIdx = servletSource.indexOf("response.sendError(HttpServletResponse.SC_FORBIDDEN", roleCheckIdx);
        assertTrue(forbiddenIdx > roleCheckIdx, "Non-teacher role must receive HTTP 403 Forbidden");
    }

    private static void testTeacherCanRequestOwnedSession() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/TeacherDashboardServlet.java");
        int handlerIdx = servletSource.indexOf("private void handlePlannedSessionRoster(");
        assertTrue(handlerIdx > 0, "handlePlannedSessionRoster must exist");

        int ownershipCheckIdx = servletSource.indexOf("if (ps.getTeacherId() != teacher.getTeacherId())", handlerIdx);
        assertTrue(ownershipCheckIdx > handlerIdx, "Teacher ownership must be validated against persisted session");
    }

    private static void testTeacherCannotRequestAnotherTeachersSession() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/TeacherDashboardServlet.java");
        int handlerIdx = servletSource.indexOf("private void handlePlannedSessionRoster(");
        int ownershipCheckIdx = servletSource.indexOf("if (ps.getTeacherId() != teacher.getTeacherId())", handlerIdx);

        int forbiddenIdx = servletSource.indexOf("response.setStatus(HttpServletResponse.SC_FORBIDDEN);", ownershipCheckIdx);
        int returnIdx = servletSource.indexOf("return;", forbiddenIdx);
        assertTrue(forbiddenIdx > ownershipCheckIdx && returnIdx > forbiddenIdx,
                "Mismatched teacher ownership must return HTTP 403 and halt execution");
    }

    private static void testClientTeacherIdCannotOverrideSessionIdentity() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/TeacherDashboardServlet.java");
        int handlerIdx = servletSource.indexOf("private void handlePlannedSessionRoster(");
        int endHandlerIdx = servletSource.indexOf("private void sendJson(", handlerIdx);
        String handlerBlock = servletSource.substring(handlerIdx, endHandlerIdx);

        assertTrue(!handlerBlock.contains("request.getParameter(\"teacherId\")"),
                "Teacher ID must never be read from request parameters");
        assertTrue(handlerBlock.contains("teacher.getTeacherId()"),
                "Teacher ID must be obtained exclusively from server-side Teacher object");
    }

    // ────────────────────────────────────────────────────────────────────────
    // 2. Input Validation Contracts
    // ────────────────────────────────────────────────────────────────────────

    private static void testMissingPlannedSessionIdRejectedWith400() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/TeacherDashboardServlet.java");
        int handlerIdx = servletSource.indexOf("private void handlePlannedSessionRoster(");
        int nullCheckIdx = servletSource.indexOf("if (psIdStr == null || psIdStr.trim().isEmpty())", handlerIdx);
        assertTrue(nullCheckIdx > handlerIdx, "Null or empty plannedSessionId check must exist");

        int badReqIdx = servletSource.indexOf("response.setStatus(HttpServletResponse.SC_BAD_REQUEST);", nullCheckIdx);
        assertTrue(badReqIdx > nullCheckIdx, "Missing plannedSessionId must set HTTP 400");
    }

    private static void testMalformedPlannedSessionIdRejectedWith400() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/TeacherDashboardServlet.java");
        int handlerIdx = servletSource.indexOf("private void handlePlannedSessionRoster(");
        int catchNfeIdx = servletSource.indexOf("catch (NumberFormatException e)", handlerIdx);
        assertTrue(catchNfeIdx > handlerIdx, "NumberFormatException catch must exist in handlePlannedSessionRoster");

        int badReqIdx = servletSource.indexOf("response.setStatus(HttpServletResponse.SC_BAD_REQUEST);", catchNfeIdx);
        assertTrue(badReqIdx > catchNfeIdx, "Malformed plannedSessionId must set HTTP 400");
    }

    private static void testZeroAndNegativeIdentifiersRejectedWith400() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/TeacherDashboardServlet.java");
        int handlerIdx = servletSource.indexOf("private void handlePlannedSessionRoster(");
        int boundsCheckIdx = servletSource.indexOf("if (plannedSessionId <= 0)", handlerIdx);
        assertTrue(boundsCheckIdx > handlerIdx, "Zero or negative check must exist in handlePlannedSessionRoster");

        int badReqIdx = servletSource.indexOf("response.setStatus(HttpServletResponse.SC_BAD_REQUEST);", boundsCheckIdx);
        assertTrue(badReqIdx > boundsCheckIdx, "Zero or negative plannedSessionId must set HTTP 400");
    }

    private static void testNonexistentSessionReturns404() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/TeacherDashboardServlet.java");
        int handlerIdx = servletSource.indexOf("private void handlePlannedSessionRoster(");
        int notFoundCheckIdx = servletSource.indexOf("if (!psOpt.isPresent())", handlerIdx);
        assertTrue(notFoundCheckIdx > handlerIdx, "Session existence check must exist");

        int notFoundStatusIdx = servletSource.indexOf("response.setStatus(HttpServletResponse.SC_NOT_FOUND);", notFoundCheckIdx);
        assertTrue(notFoundStatusIdx > notFoundCheckIdx, "Nonexistent session must set HTTP 404");
    }

    // ────────────────────────────────────────────────────────────────────────
    // 3. SQL Contracts & Safety
    // ────────────────────────────────────────────────────────────────────────

    private static void testQueryUsesSessionAndTeacherPlaceholders() {
        String daoSource = readSource("src/main/java/com/attendance/dao/PlannedSessionDao.java");
        int methodIdx = daoSource.indexOf("findRosterByPlannedSessionId(int plannedSessionId, int teacherId)");
        assertTrue(methodIdx > 0, "findRosterByPlannedSessionId method must exist in PlannedSessionDao");

        int whereIdx = daoSource.indexOf("WHERE ps.id = ? AND ps.teacher_id = ?", methodIdx);
        assertTrue(whereIdx > methodIdx, "Query must filter by WHERE ps.id = ? AND ps.teacher_id = ?");

        int param1 = daoSource.indexOf("ps.setInt(1, plannedSessionId);", methodIdx);
        int param2 = daoSource.indexOf("ps.setInt(2, teacherId);", methodIdx);
        assertTrue(param1 > methodIdx && param2 > param1, "Prepared statement must bind plannedSessionId and teacherId");
    }

    private static void testQueryJoinsOnlyEnrolledStudents() {
        String daoSource = readSource("src/main/java/com/attendance/dao/PlannedSessionDao.java");
        int methodIdx = daoSource.indexOf("findRosterByPlannedSessionId(");
        int fromIdx = daoSource.indexOf("FROM enrollments e", methodIdx);
        int joinStudents = daoSource.indexOf("JOIN students s ON e.student_id = s.student_id", methodIdx);
        int joinUsers = daoSource.indexOf("JOIN users u ON s.user_id = u.id", methodIdx);
        int joinPlanned = daoSource.indexOf("JOIN planned_sessions ps ON ps.course_id = e.course_id", methodIdx);

        assertTrue(fromIdx > methodIdx && joinStudents > fromIdx && joinUsers > joinStudents && joinPlanned > joinUsers,
                "Query must cleanly join enrollments, students, users, and planned_sessions by course_id");
    }

    private static void testQrAndAttendanceAreLeftJoined() {
        String daoSource = readSource("src/main/java/com/attendance/dao/PlannedSessionDao.java");
        int methodIdx = daoSource.indexOf("findRosterByPlannedSessionId(");
        int leftJoinQr = daoSource.indexOf("LEFT JOIN qr_sessions qs ON qs.planned_session_id = ps.id", methodIdx);
        int leftJoinAtt = daoSource.indexOf("LEFT JOIN attendance a ON", methodIdx);

        assertTrue(leftJoinQr > methodIdx && leftJoinAtt > leftJoinQr,
                "Both qr_sessions and attendance must be LEFT JOINed so unmarked students remain visible");
    }

    private static void testAttendanceFilteredByQrSessionAndStudent() {
        String daoSource = readSource("src/main/java/com/attendance/dao/PlannedSessionDao.java");
        int methodIdx = daoSource.indexOf("findRosterByPlannedSessionId(");
        int attJoinIdx = daoSource.indexOf("LEFT JOIN attendance a ON a.qr_session_id = qs.id AND a.student_id = s.student_id", methodIdx);
        assertTrue(attJoinIdx > methodIdx, "Attendance join condition must bind both qr_session_id and student_id");
    }

    private static void testStudentEmailNotRetrievedInQuery() {
        String daoSource = readSource("src/main/java/com/attendance/dao/PlannedSessionDao.java");
        int methodIdx = daoSource.indexOf("findRosterByPlannedSessionId(");
        int endQueryIdx = daoSource.indexOf("WHERE ps.id = ?", methodIdx);
        String queryBlock = daoSource.substring(methodIdx, endQueryIdx);

        assertTrue(!queryBlock.contains("u.email"), "Student email must NOT be retrieved in roster query");
        assertTrue(queryBlock.contains("u.name AS student_name"), "Student name must be retrieved");
    }

    private static void testNoMutationStatementsPresentInRosterDao() {
        String daoSource = readSource("src/main/java/com/attendance/dao/PlannedSessionDao.java");
        int methodIdx = daoSource.indexOf("findRosterByPlannedSessionId(");
        int endMethodIdx = daoSource.indexOf("return roster;", methodIdx);
        String methodBlock = daoSource.substring(methodIdx, endMethodIdx);

        assertTrue(!methodBlock.contains("INSERT "), "findRosterByPlannedSessionId must not contain INSERT");
        assertTrue(!methodBlock.contains("UPDATE "), "findRosterByPlannedSessionId must not contain UPDATE");
        assertTrue(!methodBlock.contains("DELETE "), "findRosterByPlannedSessionId must not contain DELETE");
    }

    // ────────────────────────────────────────────────────────────────────────
    // 4. Presentation State Machine (Unit Tests)
    // ────────────────────────────────────────────────────────────────────────

    private static void testScheduledWithoutQrMapsToNotActivated() {
        String status = SessionRosterItem.determinePresentationStatus("SCHEDULED", null, null, null);
        assertEquals("NOT ACTIVATED", status, "SCHEDULED without QR must be NOT ACTIVATED");
    }

    private static void testAttendanceOpenWithoutAttendanceMapsToNotYetRecorded() {
        String status = SessionRosterItem.determinePresentationStatus("ATTENDANCE_OPEN", 10, true, null);
        assertEquals("NOT YET RECORDED", status, "ATTENDANCE_OPEN with active QR and no attendance must be NOT YET RECORDED");
    }

    private static void testPresentRemainsPresent() {
        String statusOpen = SessionRosterItem.determinePresentationStatus("ATTENDANCE_OPEN", 10, true, "PRESENT");
        assertEquals("PRESENT", statusOpen, "ATTENDANCE_OPEN with active QR and PRESENT record must be PRESENT");

        String statusComp = SessionRosterItem.determinePresentationStatus("COMPLETED", 10, false, "PRESENT");
        assertEquals("PRESENT", statusComp, "COMPLETED with inactive QR and PRESENT record must be PRESENT");
    }

    private static void testLateRemainsLate() {
        String statusOpen = SessionRosterItem.determinePresentationStatus("ATTENDANCE_OPEN", 10, true, "LATE");
        assertEquals("LATE", statusOpen, "ATTENDANCE_OPEN with active QR and LATE record must be LATE");

        String statusComp = SessionRosterItem.determinePresentationStatus("COMPLETED", 10, false, "LATE");
        assertEquals("LATE", statusComp, "COMPLETED with inactive QR and LATE record must be LATE");
    }

    private static void testAbsentRemainsAbsent() {
        String status = SessionRosterItem.determinePresentationStatus("COMPLETED", 10, false, "ABSENT");
        assertEquals("ABSENT", status, "COMPLETED with inactive QR and ABSENT record must be ABSENT");
    }

    private static void testCompletedWithoutAttendanceMapsToNotRecorded() {
        String status = SessionRosterItem.determinePresentationStatus("COMPLETED", 10, false, null);
        assertEquals("NOT RECORDED", status, "COMPLETED with inactive QR without attendance record must be NOT RECORDED (not ABSENT)");
    }

    private static void testInvalidLifecycleCombinationsIdentifiedAsInconsistent() {
        // ATTENDANCE_OPEN without QR
        assertEquals("DATA INCONSISTENCY",
                SessionRosterItem.determinePresentationStatus("ATTENDANCE_OPEN", null, null, null),
                "ATTENDANCE_OPEN without QR must be DATA INCONSISTENCY");

        // COMPLETED without QR
        assertEquals("DATA INCONSISTENCY",
                SessionRosterItem.determinePresentationStatus("COMPLETED", null, null, null),
                "COMPLETED without QR must be DATA INCONSISTENCY");

        // SCHEDULED with QR session (active = true)
        assertEquals("DATA INCONSISTENCY",
                SessionRosterItem.determinePresentationStatus("SCHEDULED", 10, true, null),
                "SCHEDULED with active QR session must be DATA INCONSISTENCY");

        // SCHEDULED with QR session (active = false)
        assertEquals("DATA INCONSISTENCY",
                SessionRosterItem.determinePresentationStatus("SCHEDULED", 10, false, null),
                "SCHEDULED with inactive QR session must be DATA INCONSISTENCY");

        // SCHEDULED with attendance record
        assertEquals("DATA INCONSISTENCY",
                SessionRosterItem.determinePresentationStatus("SCHEDULED", null, null, "PRESENT"),
                "SCHEDULED with attendance record must be DATA INCONSISTENCY");

        // ATTENDANCE_OPEN with QR but inactive (false)
        assertEquals("DATA INCONSISTENCY",
                SessionRosterItem.determinePresentationStatus("ATTENDANCE_OPEN", 10, false, "PRESENT"),
                "ATTENDANCE_OPEN with inactive QR must be DATA INCONSISTENCY");

        // COMPLETED with QR but active (true)
        assertEquals("DATA INCONSISTENCY",
                SessionRosterItem.determinePresentationStatus("COMPLETED", 10, true, "PRESENT"),
                "COMPLETED with active QR must be DATA INCONSISTENCY");

        // ATTENDANCE_OPEN with ABSENT record (ABSENT reconciliation only happens on closure)
        assertEquals("DATA INCONSISTENCY",
                SessionRosterItem.determinePresentationStatus("ATTENDANCE_OPEN", 10, true, "ABSENT"),
                "ATTENDANCE_OPEN with ABSENT record must be DATA INCONSISTENCY");

        // QR exists but active state is null
        assertEquals("DATA INCONSISTENCY",
                SessionRosterItem.determinePresentationStatus("ATTENDANCE_OPEN", 10, null, "PRESENT"),
                "Null qrIsActive when QR exists must be DATA INCONSISTENCY");

        // Unknown planned status
        assertEquals("DATA INCONSISTENCY",
                SessionRosterItem.determinePresentationStatus("UNKNOWN", 10, false, "PRESENT"),
                "UNKNOWN planned status must be DATA INCONSISTENCY");
    }

    // ────────────────────────────────────────────────────────────────────────
    // 5. Metrics Calculations (Unit Tests)
    // ────────────────────────────────────────────────────────────────────────

    private static void testMetricsCountsCorrect() {
        List<SessionRosterItem> items = new ArrayList<>();
        items.add(new SessionRosterItem(1, "101", "Student A", "PRESENT", LocalDateTime.now(), "PRESENT", false));
        items.add(new SessionRosterItem(2, "102", "Student B", "LATE", LocalDateTime.now(), "LATE", false));
        items.add(new SessionRosterItem(3, "103", "Student C", "ABSENT", null, "ABSENT", false));
        items.add(new SessionRosterItem(4, "104", "Student D", null, null, "NOT RECORDED", false));

        SessionRosterMetrics m = SessionRosterMetrics.compute("COMPLETED", 10, items);

        assertEquals(4, m.getTotalEnrolled(), "Total enrolled count");
        assertEquals(1, m.getPresentCount(), "Present count");
        assertEquals(1, m.getLateCount(), "Late count");
        assertEquals(1, m.getAbsentCount(), "Absent count");
        assertEquals(1, m.getNotRecordedCount(), "Not recorded count");
        assertEquals(false, m.isContainsInconsistencies(), "Should not contain inconsistencies");
    }

    private static void testPercentageCalculationCorrect() {
        List<SessionRosterItem> items = new ArrayList<>();
        // 2 present, 1 late, 1 absent -> (2 + 1) / 4 * 100 = 75.0%
        items.add(new SessionRosterItem(1, "101", "Student A", "PRESENT", LocalDateTime.now(), "PRESENT", false));
        items.add(new SessionRosterItem(2, "102", "Student B", "PRESENT", LocalDateTime.now(), "PRESENT", false));
        items.add(new SessionRosterItem(3, "103", "Student C", "LATE", LocalDateTime.now(), "LATE", false));
        items.add(new SessionRosterItem(4, "104", "Student D", "ABSENT", null, "ABSENT", false));

        SessionRosterMetrics m = SessionRosterMetrics.compute("COMPLETED", 10, items);
        assertEquals(true, m.isPercentageApplicable(), "Percentage must be applicable");
        assertEquals(75.0, m.getAttendancePercentage(), "Attendance percentage");
    }

    private static void testZeroEnrollmentReturnsNa() {
        List<SessionRosterItem> empty = new ArrayList<>();
        SessionRosterMetrics m = SessionRosterMetrics.compute("COMPLETED", 10, empty);

        assertEquals(0, m.getTotalEnrolled(), "Total enrolled must be 0");
        assertEquals(false, m.isPercentageApplicable(), "Percentage applicable must be false");
        assertEquals(null, m.getAttendancePercentage(), "Attendance percentage must be null");
    }

    private static void testScheduledWithoutQrReturnsNa() {
        List<SessionRosterItem> items = new ArrayList<>();
        items.add(new SessionRosterItem(1, "101", "Student A", null, null, "NOT ACTIVATED", false));
        items.add(new SessionRosterItem(2, "102", "Student B", null, null, "NOT ACTIVATED", false));

        SessionRosterMetrics m = SessionRosterMetrics.compute("SCHEDULED", null, items);
        assertEquals(2, m.getTotalEnrolled(), "Total enrolled");
        assertEquals(2, m.getNotRecordedCount(), "Not recorded count");
        assertEquals(false, m.isPercentageApplicable(), "Percentage must not be applicable for unactivated scheduled session");
        assertEquals(null, m.getAttendancePercentage(), "Attendance percentage must be null");
    }

    private static void testAttendanceOpenIndicatesInProgress() {
        List<SessionRosterItem> items = new ArrayList<>();
        items.add(new SessionRosterItem(1, "101", "Student A", "PRESENT", LocalDateTime.now(), "PRESENT", false));
        items.add(new SessionRosterItem(2, "102", "Student B", null, null, "NOT YET RECORDED", false));

        SessionRosterMetrics m = SessionRosterMetrics.compute("ATTENDANCE_OPEN", 10, items);
        assertEquals(true, m.isInProgress(), "inProgress must be true for ATTENDANCE_OPEN");
        assertEquals(true, m.isPercentageApplicable(), "Percentage is applicable in-progress");
        assertEquals(50.0, m.getAttendancePercentage(), "Attendance percentage in-progress");
    }

    private static void testInconsistentRecordsFlaggedWithoutInventingAbsent() {
        List<SessionRosterItem> items = new ArrayList<>();
        items.add(new SessionRosterItem(1, "101", "Student A", "PRESENT", LocalDateTime.now(), "PRESENT", false));
        items.add(new SessionRosterItem(2, "102", "Student B", "INVALID_STATE", null, "DATA INCONSISTENCY", true));

        SessionRosterMetrics m = SessionRosterMetrics.compute("COMPLETED", 10, items);
        assertEquals(true, m.isContainsInconsistencies(), "containsInconsistencies must be true");
        assertEquals(1, m.getInconsistencyCount(), "inconsistencyCount must be 1");
        assertEquals(0, m.getAbsentCount(), "Inconsistent record must NOT be counted as ABSENT");
        assertEquals(false, m.isPercentageApplicable(), "Percentage must be inapplicable when inconsistencies exist");
        assertEquals(null, m.getAttendancePercentage(), "Attendance percentage must be null when inconsistencies exist");
    }

    // ────────────────────────────────────────────────────────────────────────
    // 6. Timestamp & Immutability
    // ────────────────────────────────────────────────────────────────────────

    private static void testTimestampMappingUsesUtcInstantConversion() {
        Instant epochInstant = Instant.parse("2026-11-01T10:00:00Z");
        LocalDateTime mappedUtc = epochInstant.atZone(ZoneOffset.UTC).toLocalDateTime();

        assertEquals(2026, mappedUtc.getYear(), "Year");
        assertEquals(11, mappedUtc.getMonthValue(), "Month");
        assertEquals(1, mappedUtc.getDayOfMonth(), "Day");
        assertEquals(10, mappedUtc.getHour(), "Hour in UTC");

        String daoSource = readSource("src/main/java/com/attendance/dao/PlannedSessionDao.java");
        int rosterMethodIdx = daoSource.indexOf("findRosterByPlannedSessionId(");
        assertTrue(rosterMethodIdx > 0, "findRosterByPlannedSessionId must exist");

        int endMethodIdx = daoSource.indexOf("return roster;", rosterMethodIdx);
        String methodBlock = daoSource.substring(rosterMethodIdx, endMethodIdx);

        assertTrue(methodBlock.contains("ma.toInstant()"), "Must call ma.toInstant()");
        assertTrue(methodBlock.contains("ZoneOffset.UTC"), "Must convert with ZoneOffset.UTC");
        assertTrue(methodBlock.contains("toLocalDateTime()"), "Must convert toLocalDateTime()");
    }

    private static void testIstFormattingReusesDateTimeUtil() {
        LocalDateTime utcDateTime = LocalDateTime.of(2026, 11, 1, 10, 0, 0); // 10:00 UTC = 15:30 IST
        String formatted = DateTimeUtil.formatToIst(utcDateTime);
        assertTrue(formatted.contains("03:30:00 PM IST"), "Formatted IST must be 03:30:00 PM IST");

        String servletSource = readSource("src/main/java/com/attendance/servlet/TeacherDashboardServlet.java");
        int rosterHandlerIdx = servletSource.indexOf("private void handlePlannedSessionRoster(");
        assertTrue(rosterHandlerIdx > 0, "handlePlannedSessionRoster must exist in servlet");

        int formatCallIdx = servletSource.indexOf("DateTimeUtil.formatToIst(item.getMarkedAt())", rosterHandlerIdx);
        assertTrue(formatCallIdx > rosterHandlerIdx, "Servlet must format timestamp using DateTimeUtil.formatToIst()");
    }

    private static void testRosterRetrievalPerformsNoWrites() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/TeacherDashboardServlet.java");
        int handlerIdx = servletSource.indexOf("private void handlePlannedSessionRoster(");
        assertTrue(handlerIdx > 0, "handlePlannedSessionRoster method definition must exist");
        int endHandlerIdx = servletSource.indexOf("private void sendJson(", handlerIdx);
        String handlerBlock = servletSource.substring(handlerIdx, endHandlerIdx);

        assertTrue(!handlerBlock.contains(".save("), "Roster retrieval must not invoke save()");
        assertTrue(!handlerBlock.contains(".update("), "Roster retrieval must not invoke update()");
        assertTrue(!handlerBlock.contains(".delete("), "Roster retrieval must not invoke delete()");
        assertTrue(!handlerBlock.contains(".recordAttendanceTransaction("), "Roster retrieval must not invoke recordAttendanceTransaction()");
        assertTrue(!handlerBlock.contains(".closeMeetingAndCompleteSession("), "Roster retrieval must not invoke closeMeetingAndCompleteSession()");
    }

    private static void testExistingC42ClosureMethodsUnchanged() {
        String daoSource = readSource("src/main/java/com/attendance/dao/PlannedSessionDao.java");
        int closeMethodIdx = daoSource.indexOf("public boolean closeMeetingAndCompleteSession");
        assertTrue(closeMethodIdx > 0, "closeMeetingAndCompleteSession must exist");

        int conditionIdx = daoSource.indexOf("if (!isActive || !\"ATTENDANCE_OPEN\".equals(plannedStatus))", closeMethodIdx);
        assertTrue(conditionIdx > closeMethodIdx, "C4.2.2 QR active-state matrix in PlannedSessionDao must remain intact");

        String servletSource = readSource("src/main/java/com/attendance/servlet/QrGenerateServlet.java");
        int safeMsgIdx = servletSource.indexOf("String safeMsg = \"Invalid session closure request.\";");
        assertTrue(safeMsgIdx > 0, "C4.2.4 safe closure message patch in QrGenerateServlet must remain intact");
    }

    // ────────────────────────────────────────────────────────────────────────
    // 7. C4.3.4 Consistency Corrections
    // ────────────────────────────────────────────────────────────────────────

    private static void testQrActiveStateSelectedAndMappedInDao() {
        String daoSource = readSource("src/main/java/com/attendance/dao/PlannedSessionDao.java");
        int rosterMethodIdx = daoSource.indexOf("findRosterByPlannedSessionId(");
        assertTrue(rosterMethodIdx > 0, "findRosterByPlannedSessionId must exist");

        int endMethodIdx = daoSource.indexOf("return roster;", rosterMethodIdx);
        String methodBlock = daoSource.substring(rosterMethodIdx, endMethodIdx);

        assertTrue(methodBlock.contains("qs.is_active AS qr_is_active"),
                "Roster query must select qs.is_active AS qr_is_active");
        assertTrue(methodBlock.contains("item.setQrIsActive(qrIsActive);"),
                "Roster mapping must set qrIsActive on SessionRosterItem");
    }

    private static void testRosterInconsistencyInvalidatesPercentage() {
        List<SessionRosterItem> items = new ArrayList<>();
        items.add(new SessionRosterItem(1, "101", "Student A", "PRESENT", LocalDateTime.now(), "PRESENT", false, true));
        items.add(new SessionRosterItem(2, "102", "Student B", "LATE", LocalDateTime.now(), "LATE", false, true));
        items.add(new SessionRosterItem(3, "103", "Student C", "INVALID", null, "DATA INCONSISTENCY", true, false));

        SessionRosterMetrics m = SessionRosterMetrics.compute("COMPLETED", 10, items);
        assertEquals(3, m.getTotalEnrolled(), "Total enrolled");
        assertEquals(1, m.getPresentCount(), "Present count");
        assertEquals(1, m.getLateCount(), "Late count");
        assertEquals(1, m.getInconsistencyCount(), "Inconsistency count must be 1");
        assertEquals(true, m.isContainsInconsistencies(), "containsInconsistencies must be true");
        assertEquals(false, m.isPercentageApplicable(), "percentageApplicable must be false when inconsistencyCount > 0");
        assertEquals(null, m.getAttendancePercentage(), "attendancePercentage must be null when inconsistencyCount > 0");
    }

    private static void testCourseMetadataResolvedFromCourseDao() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/TeacherDashboardServlet.java");
        int handlerIdx = servletSource.indexOf("private void handlePlannedSessionRoster(");
        assertTrue(handlerIdx > 0, "handlePlannedSessionRoster must exist");

        int endHandlerIdx = servletSource.indexOf("private void sendJson(", handlerIdx);
        String handlerBlock = servletSource.substring(handlerIdx, endHandlerIdx);

        assertTrue(handlerBlock.contains("courseDao.findById(ps.getCourseId())"),
                "handlePlannedSessionRoster must query courseDao.findById(ps.getCourseId())");
        assertTrue(handlerBlock.contains("ps.setCourseCode(course.getCourseCode())"),
                "handlePlannedSessionRoster must set courseCode on PlannedSession");
        assertTrue(handlerBlock.contains("ps.setCourseName(course.getCourseName())"),
                "handlePlannedSessionRoster must set courseName on PlannedSession");
    }

    private static void testNullPercentageSerializedAsLiteralNullInJson() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/TeacherDashboardServlet.java");
        int handlerIdx = servletSource.indexOf("private void handlePlannedSessionRoster(");
        assertTrue(handlerIdx > 0, "handlePlannedSessionRoster must exist");

        int endHandlerIdx = servletSource.indexOf("private void sendJson(", handlerIdx);
        String handlerBlock = servletSource.substring(handlerIdx, endHandlerIdx);

        assertTrue(handlerBlock.contains("attendancePercentage"),
                "JSON must contain attendancePercentage key");
        assertTrue(handlerBlock.contains("metrics.getAttendancePercentage() != null ? metrics.getAttendancePercentage() : \"null\""),
                "attendancePercentage must be serialized as literal null when null");
    }

    private static void testAttendanceOpenWithInconsistencyPreservesInProgress() {
        List<SessionRosterItem> items = new ArrayList<>();
        items.add(new SessionRosterItem(1, "101", "Student A", "PRESENT", LocalDateTime.now(), "PRESENT", false, true));
        items.add(new SessionRosterItem(2, "102", "Student B", "INVALID", null, "DATA INCONSISTENCY", true, true));

        SessionRosterMetrics m = SessionRosterMetrics.compute("ATTENDANCE_OPEN", 10, items);
        assertEquals(2, m.getTotalEnrolled(), "Total enrolled");
        assertEquals(1, m.getPresentCount(), "Present count");
        assertEquals(1, m.getInconsistencyCount(), "Inconsistency count");
        assertEquals(true, m.isContainsInconsistencies(), "containsInconsistencies must be true");
        assertEquals(true, m.isInProgress(), "inProgress must be true for ATTENDANCE_OPEN even when inconsistencies exist");
        assertEquals(false, m.isPercentageApplicable(), "percentageApplicable must be false when inconsistencyCount > 0");
        assertEquals(null, m.getAttendancePercentage(), "attendancePercentage must be null when inconsistencyCount > 0");
    }

    private static String readSource(String relativePath) {
        try {
            return Files.readString(Paths.get(relativePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read source file: " + relativePath, e);
        }
    }
}
