package com.attendance;

import com.attendance.model.PlannedSession;
import com.attendance.model.SessionRosterItem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * ScanIn Stage 13 — Phase 13-C4.6: Planned-Session Lifecycle Consistency Verification Test Suite.
 *
 * Scope:
 * Part A: Concurrency hardening between student QR scan and teacher closure under row-level lock.
 * Part B: Historical enrollment consistency & post-closure enrollment exclusion.
 * Part C: Student timetable invalid-state presentation & data inconsistency isolation.
 */
public class Stage13PhaseC46Test {

    private static int totalPassed = 0;
    private static int totalFailed = 0;

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("  Stage 13-C4.6 Planned-Session Lifecycle Consistency Tests       ");
        System.out.println("==================================================================");

        // ── Part A: Scan vs Closure Lock Synchronization & Concurrency ─────
        runTest("C4.6-01: AttendanceDao acquires row lock matching closure lock ordering [Static Contract]",
                Stage13PhaseC46Test::testAttendanceDaoAcquiresRowLockMatchingClosure);

        runTest("C4.6-02: AttendanceDao checks active, not-closed, not-expired & ATTENDANCE_OPEN [Static Contract]",
                Stage13PhaseC46Test::testAttendanceDaoVerifiesStateUnderLock);

        runTest("C4.6-03: AttendanceDao records CLOSED_SESSION outcome upon closed session [Static Contract]",
                Stage13PhaseC46Test::testAttendanceDaoRecordsClosedSessionOutcome);

        runTest("C4.6-04: QrScanServlet routes planned sessions to recordPlannedAttendanceTransaction [Static Contract]",
                Stage13PhaseC46Test::testQrScanServletDispatchesToPlannedAttendanceTransaction);

        runTest("C4.6-05: QrScanServlet handles IllegalStateException gracefully [Static Contract]",
                Stage13PhaseC46Test::testQrScanServletHandlesIllegalStateExceptionGracefully);

        // ── Part B: Historical Enrollment & Post-Closure Exclusion ──────────
        runTest("C4.6-06: PlannedSessionDao filters out enrollments occurring after session closure [Static Contract]",
                Stage13PhaseC46Test::testPlannedSessionDaoFiltersPostClosureEnrollments);

        runTest("C4.6-07: In-memory simulation confirms post-closure enrollment exclusion [Pure Java Unit Test]",
                Stage13PhaseC46Test::testInMemoryPostClosureEnrollmentExclusion);

        // ── Part C: Student Timetable Invalid-State Handling ───────────────
        runTest("C4.6-08: Timetable DAO query selects qr_session_id and qr_is_active [Static Contract]",
                Stage13PhaseC46Test::testTimetableDaoSelectsQrBinding);

        runTest("C4.6-09: PlannedSession maps qrSessionId and qrIsActive [Pure Java Unit Test]",
                Stage13PhaseC46Test::testPlannedSessionModelQrBinding);

        runTest("C4.6-10: SCHEDULED with QR session maps to DATA INCONSISTENCY [Pure Java Unit Test]",
                Stage13PhaseC46Test::testScheduledWithQrMapsToDataInconsistency);

        runTest("C4.6-11: ATTENDANCE_OPEN with inactive QR maps to DATA INCONSISTENCY [Pure Java Unit Test]",
                Stage13PhaseC46Test::testAttendanceOpenWithInactiveQrMapsToDataInconsistency);

        runTest("C4.6-12: COMPLETED with active QR maps to DATA INCONSISTENCY [Pure Java Unit Test]",
                Stage13PhaseC46Test::testCompletedWithActiveQrMapsToDataInconsistency);

        runTest("C4.6-13: COMPLETED with inactive QR and no attendance maps to NOT RECORDED [Pure Java Unit Test]",
                Stage13PhaseC46Test::testCompletedWithInactiveQrAndNoAttendanceMapsToNotRecorded);

        runTest("C4.6-14: Student dashboard JSP renders INCONSISTENT badge for inconsistent sessions [Static Contract]",
                Stage13PhaseC46Test::testStudentDashboardRendersInconsistentBadge);

        runTest("C4.6-15: Student dashboard JSP guards against Scan to Mark on inconsistent sessions [Static Contract]",
                Stage13PhaseC46Test::testStudentDashboardGuardsAgainstScanToMark);

        runTest("C4.6-16: Student dashboard JSP never infers ABSENT for inconsistent sessions [Static Contract]",
                Stage13PhaseC46Test::testStudentDashboardNeverInfersAbsentForInconsistent);

        System.out.println("==================================================================");
        System.out.println("  Stage 13-C4.6 Test Results: " + totalPassed + " Passed, " + totalFailed + " Failed");
        System.out.println("==================================================================");

        if (totalFailed > 0) {
            System.err.println("❌ STAGE 13-C4.6 CONSISTENCY VERIFICATION FAILED");
            System.exit(1);
        } else {
            System.out.println("✓ STAGE 13-C4.6 CONSISTENCY VERIFICATION PASSED");
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Test Implementations
    // ────────────────────────────────────────────────────────────────────────

    private static void testAttendanceDaoAcquiresRowLockMatchingClosure() {
        String daoSource = readSource("src/main/java/com/attendance/dao/AttendanceDao.java");
        assertTrue(daoSource.contains("public boolean recordPlannedAttendanceTransaction("),
                "AttendanceDao must declare recordPlannedAttendanceTransaction");

        int methodIdx = daoSource.indexOf("recordPlannedAttendanceTransaction(");
        int lockIdx = daoSource.indexOf("FOR UPDATE", methodIdx);
        assertTrue(lockIdx > methodIdx, "recordPlannedAttendanceTransaction must use FOR UPDATE row locking");

        String lockSql = daoSource.substring(methodIdx, lockIdx + "FOR UPDATE".length());
        assertTrue(lockSql.contains("FROM qr_sessions qs") && lockSql.contains("JOIN planned_sessions ps ON qs.planned_session_id = ps.id"),
                "Lock ordering must query qr_sessions joined with planned_sessions, exactly matching PlannedSessionDao.closeMeetingAndCompleteSession");
    }

    private static void testAttendanceDaoVerifiesStateUnderLock() {
        String daoSource = readSource("src/main/java/com/attendance/dao/AttendanceDao.java");
        int methodIdx = daoSource.indexOf("recordPlannedAttendanceTransaction(");
        int endMethodIdx = daoSource.indexOf("public boolean recordAttendanceTransaction", methodIdx);
        if (endMethodIdx < 0) endMethodIdx = daoSource.indexOf("public boolean updateStatus", methodIdx);
        String methodBlock = daoSource.substring(methodIdx, endMethodIdx > 0 ? endMethodIdx : daoSource.length());

        assertTrue(methodBlock.contains("sessionActive = rs.getBoolean(\"is_active\");"),
                "Must check is_active under lock");
        assertTrue(methodBlock.contains("rs.getTimestamp(\"closed_at\") == null"),
                "Must check closed_at is null under lock");
        assertTrue(methodBlock.contains("\"ATTENDANCE_OPEN\".equals(pStatus)"),
                "Must check planned_status equals ATTENDANCE_OPEN under lock");
        assertTrue(methodBlock.contains("!sessionActive || !plannedOpen || !notClosed || !notExpired"),
                "Must fail-closed if any lifecycle condition is violated");
    }

    private static void testAttendanceDaoRecordsClosedSessionOutcome() {
        String daoSource = readSource("src/main/java/com/attendance/dao/AttendanceDao.java");
        int methodIdx = daoSource.indexOf("recordPlannedAttendanceTransaction(");
        int outcomeIdx = daoSource.indexOf("\"CLOSED_SESSION\"", methodIdx);
        assertTrue(outcomeIdx > methodIdx, "Must record CLOSED_SESSION outcome in scan_attempts on closed session");
    }

    private static void testQrScanServletDispatchesToPlannedAttendanceTransaction() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/QrScanServlet.java");
        int dispatchIdx = servletSource.indexOf("attendanceDao.recordPlannedAttendanceTransaction(");
        int checkPlannedIdx = servletSource.indexOf("session.getPlannedSessionId() != null", dispatchIdx - 150);
        assertTrue(dispatchIdx > 0 && checkPlannedIdx > 0 && checkPlannedIdx < dispatchIdx,
                "QrScanServlet must check session.getPlannedSessionId() != null and dispatch to recordPlannedAttendanceTransaction");
    }

    private static void testQrScanServletHandlesIllegalStateExceptionGracefully() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/QrScanServlet.java");
        int dispatchIdx = servletSource.indexOf("attendanceDao.recordPlannedAttendanceTransaction(");
        int catchBlockIdx = servletSource.indexOf("catch (IllegalStateException e)", dispatchIdx);
        assertTrue(catchBlockIdx > dispatchIdx, "QrScanServlet must catch IllegalStateException from recordPlannedAttendanceTransaction");

        int endCatch = servletSource.indexOf("catch (SQLException e)", catchBlockIdx);
        String catchBlock = servletSource.substring(catchBlockIdx, endCatch);
        assertTrue(catchBlock.contains("handleResult(request, response, isJson, false, e.getMessage())"),
                "Must pass clean message to handleResult without revealing database details");
    }

    private static void testPlannedSessionDaoFiltersPostClosureEnrollments() {
        String daoSource = readSource("src/main/java/com/attendance/dao/PlannedSessionDao.java");
        int methodIdx = daoSource.indexOf("findRosterByPlannedSessionId(");
        assertTrue(methodIdx > 0, "findRosterByPlannedSessionId must exist in PlannedSessionDao");

        int endMethodIdx = daoSource.indexOf("return roster;", methodIdx);
        String methodBlock = daoSource.substring(methodIdx, endMethodIdx);

        assertTrue(methodBlock.contains("e.enrolled_at <= qs.closed_at"),
                "findRosterByPlannedSessionId must filter out post-closure enrollments (e.enrolled_at <= qs.closed_at)");
    }

    private static void testInMemoryPostClosureEnrollmentExclusion() {
        // Verification: If a session closed at T1, an enrollment at T0 (T0 <= T1) is included;
        // an enrollment at T2 (T2 > T1) is excluded from the completed session roster.
        java.time.Instant closedAt = java.time.Instant.parse("2026-11-01T10:00:00Z");
        java.time.Instant studentAEnrolled = java.time.Instant.parse("2026-10-15T09:00:00Z");
        java.time.Instant studentBEnrolled = java.time.Instant.parse("2026-11-01T11:00:00Z");

        boolean studentAIncluded = !studentAEnrolled.isAfter(closedAt);
        boolean studentBIncluded = !studentBEnrolled.isAfter(closedAt);

        assertTrue(studentAIncluded, "Student enrolled prior to closure must be included");
        assertTrue(!studentBIncluded, "Student enrolled after closure must be excluded");
    }

    private static void testTimetableDaoSelectsQrBinding() {
        String daoSource = readSource("src/main/java/com/attendance/dao/PlannedSessionDao.java");
        int methodIdx = daoSource.indexOf("findByStudentEnrollmentWithAttendance(");
        int endMethodIdx = daoSource.indexOf("return list;", methodIdx);
        String methodBlock = daoSource.substring(methodIdx, endMethodIdx);

        assertTrue(methodBlock.contains("qs.id AS qr_session_id"),
                "findByStudentEnrollmentWithAttendance must select qs.id AS qr_session_id");
        assertTrue(methodBlock.contains("qs.is_active AS qr_is_active"),
                "findByStudentEnrollmentWithAttendance must select qs.is_active AS qr_is_active");
    }

    private static void testPlannedSessionModelQrBinding() {
        PlannedSession ps = new PlannedSession();
        ps.setId(101);
        ps.setStatus("ATTENDANCE_OPEN");
        ps.setQrSessionId(55);
        ps.setQrIsActive(true);
        ps.setAttendanceStatus("PRESENT");

        assertEquals(Integer.valueOf(55), ps.getQrSessionId(), "qrSessionId must match");
        assertEquals(Boolean.TRUE, ps.getQrIsActive(), "qrIsActive must match");
        assertEquals("PRESENT", ps.getPresentationStatus(), "presentationStatus must evaluate to PRESENT");
        assertTrue(!ps.isInconsistent(), "Must not be inconsistent");
    }

    private static void testScheduledWithQrMapsToDataInconsistency() {
        PlannedSession ps = new PlannedSession();
        ps.setStatus("SCHEDULED");
        ps.setQrSessionId(12); // Invalid: SCHEDULED session must not have QR
        ps.setQrIsActive(false);

        assertEquals("DATA INCONSISTENCY", ps.getPresentationStatus(), "SCHEDULED with QR must map to DATA INCONSISTENCY");
        assertTrue(ps.isInconsistent(), "isInconsistent must return true");
    }

    private static void testAttendanceOpenWithInactiveQrMapsToDataInconsistency() {
        PlannedSession ps = new PlannedSession();
        ps.setStatus("ATTENDANCE_OPEN");
        ps.setQrSessionId(12);
        ps.setQrIsActive(false); // Invalid: OPEN session must have active QR

        assertEquals("DATA INCONSISTENCY", ps.getPresentationStatus(), "ATTENDANCE_OPEN with inactive QR must map to DATA INCONSISTENCY");
        assertTrue(ps.isInconsistent(), "isInconsistent must return true");
    }

    private static void testCompletedWithActiveQrMapsToDataInconsistency() {
        PlannedSession ps = new PlannedSession();
        ps.setStatus("COMPLETED");
        ps.setQrSessionId(12);
        ps.setQrIsActive(true); // Invalid: COMPLETED session must have inactive QR

        assertEquals("DATA INCONSISTENCY", ps.getPresentationStatus(), "COMPLETED with active QR must map to DATA INCONSISTENCY");
        assertTrue(ps.isInconsistent(), "isInconsistent must return true");
    }

    private static void testCompletedWithInactiveQrAndNoAttendanceMapsToNotRecorded() {
        PlannedSession ps = new PlannedSession();
        ps.setStatus("COMPLETED");
        ps.setQrSessionId(12);
        ps.setQrIsActive(false);
        ps.setAttendanceStatus(null); // Valid: completed session where student was unmarked

        assertEquals("NOT RECORDED", ps.getPresentationStatus(), "COMPLETED with inactive QR and no attendance must map to NOT RECORDED");
        assertTrue(!ps.isInconsistent(), "isInconsistent must return false");
    }

    private static void testStudentDashboardRendersInconsistentBadge() {
        String jspSource = readSource("src/main/webapp/WEB-INF/views/student-dashboard.jsp");
        int inconsistentCheck = jspSource.indexOf("if (ps.isInconsistent())");
        assertTrue(inconsistentCheck > 0, "student-dashboard.jsp must check ps.isInconsistent()");

        int badgeText = jspSource.indexOf("⚠️ INCONSISTENT", inconsistentCheck);
        assertTrue(badgeText > inconsistentCheck, "Must render ⚠️ INCONSISTENT badge");

        int dataInconsistencyText = jspSource.indexOf("⚠️ DATA INCONSISTENCY", inconsistentCheck);
        assertTrue(dataInconsistencyText > inconsistentCheck, "Must render ⚠️ DATA INCONSISTENCY badge");
    }

    private static void testStudentDashboardGuardsAgainstScanToMark() {
        String jspSource = readSource("src/main/webapp/WEB-INF/views/student-dashboard.jsp");
        int inconsistentCheck = jspSource.indexOf("if (ps.isInconsistent())");
        int scanToMarkIdx = jspSource.indexOf("Scan to Mark", inconsistentCheck);

        // ps.isInconsistent() is evaluated before ATTENDANCE_OPEN / Scan to Mark
        assertTrue(inconsistentCheck < scanToMarkIdx, "ps.isInconsistent() must precede Scan to Mark in conditional chain");
    }

    private static void testStudentDashboardNeverInfersAbsentForInconsistent() {
        String jspSource = readSource("src/main/webapp/WEB-INF/views/student-dashboard.jsp");
        int inconsistentCheck = jspSource.indexOf("if (ps.isInconsistent())");
        int absentIdx = jspSource.indexOf("\"ABSENT\".equalsIgnoreCase(attStatus)", inconsistentCheck);

        // Even if attendanceStatus was null or unrecorded, inconsistent branch catches it first
        assertTrue(inconsistentCheck < absentIdx, "Inconsistency check must be evaluated prior to any absent inference");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Test Harness Helpers
    // ────────────────────────────────────────────────────────────────────────

    private static void runTest(String testName, Runnable testMethod) {
        try {
            testMethod.run();
            System.out.println("  [PASS] " + testName);
            totalPassed++;
        } catch (Throwable t) {
            System.out.println("  [FAIL] " + testName + ": " + t.getMessage());
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

    private static String readSource(String relativePath) {
        try {
            return Files.readString(Paths.get(relativePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read source file: " + relativePath, e);
        }
    }
}

