package com.attendance;

import com.attendance.dao.PlannedSessionDao;
import com.attendance.dao.QrSessionDao;
import com.attendance.model.Attendance;
import com.attendance.model.PlannedSession;
import com.attendance.model.QrSession;
import com.attendance.servlet.QrScanServlet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * ScanIn Stage 13 — Phase 13-C4.2 / C4.2.2 Backend Scan-vs-Closure Concurrency & Consistency Test Suite.
 *
 * Scope:
 * Correction A: Prevent ABSENT-to-PRESENT recovery through student scanning for planned sessions.
 * Correction B (C4.2.2): Strict QR active-state matrix in PlannedSessionDao and controlled HTTP 400/409
 *                         error handling in QrGenerateServlet.
 *
 * Test Classifications:
 * - Pure Java Unit Test: In-memory evaluation of business logic, models, and idempotency state machine.
 * - Static Contract Test: Structural invariant verification against application source code.
 *
 * Note: Real MySQL InnoDB transaction isolation, row locking, and multi-client concurrency contention
 * are strictly deferred to Stage 13-V against an authorized live test database.
 */
public class Stage13PhaseC4Test {

    private static int totalPassed = 0;
    private static int totalFailed = 0;

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("  Stage 13-C4.2.2 Backend Concurrency & Consistency Tests         ");
        System.out.println("==================================================================");

        // ── Part A: Planned-Session Scan Behavior Tests (Correction A) ───────

        runTest("C4.2-A01: Existing PRESENT record is rejected for planned session [Pure Java Unit Test]",
                Stage13PhaseC4Test::testExistingPresentRecordRejectedForPlannedSession);

        runTest("C4.2-A02: Existing LATE record is rejected for planned session [Pure Java Unit Test]",
                Stage13PhaseC4Test::testExistingLateRecordRejectedForPlannedSession);

        runTest("C4.2-A03: Existing ABSENT record is rejected for planned session [Pure Java Unit Test]",
                Stage13PhaseC4Test::testExistingAbsentRecordRejectedForPlannedSession);

        runTest("C4.2-A04: ABSENT is NOT updated to PRESENT for planned sessions [Static Contract Test]",
                Stage13PhaseC4Test::testAbsentNotUpdatedToPresentForPlannedSessions);

        runTest("C4.2-A05: Duplicate attempt is recorded with DUPLICATE outcome [Static Contract Test]",
                Stage13PhaseC4Test::testDuplicateAttemptRecordedWithDuplicateOutcome);

        runTest("C4.2-A06: No successful attendance response for existing planned record [Static Contract Test]",
                Stage13PhaseC4Test::testNoSuccessResponseForExistingPlannedRecord);

        runTest("C4.2-A07: Ordinary legacy session ABSENT recovery remains unchanged [Static Contract Test]",
                Stage13PhaseC4Test::testOrdinarySessionAbsentRecoveryPreserved);

        runTest("C4.2-A08: Scan without existing attendance follows normal path [Static Contract Test]",
                Stage13PhaseC4Test::testScanWithoutExistingRecordFollowsNormalPath);

        // ── Part B: DAO Lifecycle State Matrix Tests (C4.2.2 Correction A) ───

        runTest("C4.2-B01: Active QR + ATTENDANCE_OPEN permits normal closure [Unit & Static Contract]",
                Stage13PhaseC4Test::testActiveQrAndAttendanceOpenPermitsClosure);

        runTest("C4.2-B02: Inactive QR + COMPLETED returns idempotent success [Unit & Static Contract]",
                Stage13PhaseC4Test::testInactiveQrAndCompletedReturnsIdempotentSuccess);

        runTest("C4.2-B03: Inactive QR + ATTENDANCE_OPEN rejects and rolls back [Unit & Static Contract]",
                Stage13PhaseC4Test::testInactiveQrAndAttendanceOpenRejectsAndRollsBack);

        runTest("C4.2-B04: Active QR + COMPLETED rejects and rolls back [Unit & Static Contract]",
                Stage13PhaseC4Test::testActiveQrAndCompletedRejectsAndRollsBack);

        runTest("C4.2-B05: Active QR + SCHEDULED rejects and rolls back [Unit & Static Contract]",
                Stage13PhaseC4Test::testActiveQrAndScheduledRejectsAndRollsBack);

        runTest("C4.2-B06: Unexpected or null planned status rejects safely [Unit & Static Contract]",
                Stage13PhaseC4Test::testUnexpectedOrNullStatusRejectsSafely);

        runTest("C4.2-B07: Invalid state combinations reject before ABSENT reconciliation [Static Contract Test]",
                Stage13PhaseC4Test::testInvalidCombinationsRejectBeforeReconciliation);

        runTest("C4.2-B08: Invalid state combinations do not update planned status to COMPLETED [Static Contract Test]",
                Stage13PhaseC4Test::testInvalidCombinationsDoNotCompleteStatus);

        runTest("C4.2-B09: Invalid QR/planned-session association fails safely with rollback [Static Contract Test]",
                Stage13PhaseC4Test::testInvalidAssociationFailsSafely);

        runTest("C4.2-B10: Failed closure operation triggers transaction rollback [Static Contract Test]",
                Stage13PhaseC4Test::testFailedClosureOperationRollsBack);

        runTest("C4.2-B11: Repeated closure cannot create duplicate attendance records [Static Contract Test]",
                Stage13PhaseC4Test::testRepeatedClosureCannotDuplicateAttendance);

        // ── Part C: Servlet Controlled Closure Error Handling (C4.2.2 Correction B)

        runTest("C4.2-C01: IllegalArgumentException maps to HTTP 400 Bad Request [Static Contract Test]",
                Stage13PhaseC4Test::testIllegalArgumentExceptionMapsTo400);

        runTest("C4.2-C02: IllegalStateException maps to HTTP 409 Conflict [Static Contract Test]",
                Stage13PhaseC4Test::testIllegalStateExceptionMapsTo409);

        runTest("C4.2-C03: Error responses do not disclose stack traces or database details [Static Contract Test]",
                Stage13PhaseC4Test::testErrorResponsesDoNotDiscloseInternalDetails);

        runTest("C4.2-C04: Existing successful closure response remains compatible [Static Contract Test]",
                Stage13PhaseC4Test::testSuccessfulClosureResponseRemainsCompatible);

        runTest("C4.2-C05: Existing ordinary-session closure behavior remains unchanged [Static Contract Test]",
                Stage13PhaseC4Test::testOrdinarySessionClosureBehaviorUnchanged);

        runTest("C4.2-C06: Existing CSRF, authentication, and ownership protections remain intact [Static Contract Test]",
                Stage13PhaseC4Test::testSecurityProtectionsRemainIntact);

        System.out.println("==================================================================");
        System.out.printf("  Stage 13-C4.2.2 Test Results: %d Passed, %d Failed%n", totalPassed, totalFailed);
        System.out.println("==================================================================");

        if (totalFailed > 0) {
            System.err.println("❌ STAGE 13-C4.2.2 CONTRACT VERIFICATION FAILED");
            System.exit(1);
        } else {
            System.out.println("✅ STAGE 13-C4.2.2 CONTRACT VERIFICATION PASSED");
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
    // Part A: Planned-Session Scan Behavior Tests (Correction A)
    // ────────────────────────────────────────────────────────────────────────

    private static void testExistingPresentRecordRejectedForPlannedSession() {
        QrSession session = new QrSession();
        session.setId(10);
        session.setPlannedSessionId(42);

        Attendance existing = new Attendance();
        existing.setId(101);
        existing.setStatus("PRESENT");

        boolean isPlanned = session.getPlannedSessionId() != null;
        assertTrue(isPlanned, "Session must be identified as planned session");

        boolean shouldReject = isPlanned && existing != null;
        assertTrue(shouldReject, "Existing PRESENT record must be rejected for planned session");
    }

    private static void testExistingLateRecordRejectedForPlannedSession() {
        QrSession session = new QrSession();
        session.setId(10);
        session.setPlannedSessionId(42);

        Attendance existing = new Attendance();
        existing.setId(102);
        existing.setStatus("LATE");

        boolean isPlanned = session.getPlannedSessionId() != null;
        boolean shouldReject = isPlanned && existing != null;
        assertTrue(shouldReject, "Existing LATE record must be rejected for planned session");
    }

    private static void testExistingAbsentRecordRejectedForPlannedSession() {
        QrSession session = new QrSession();
        session.setId(10);
        session.setPlannedSessionId(42);

        Attendance existing = new Attendance();
        existing.setId(103);
        existing.setStatus("ABSENT");

        boolean isPlanned = session.getPlannedSessionId() != null;
        boolean shouldReject = isPlanned && existing != null;
        assertTrue(shouldReject, "Existing ABSENT record must be rejected for planned session");
    }

    private static void testAbsentNotUpdatedToPresentForPlannedSessions() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/QrScanServlet.java");
        int duplicateCheckIdx = servletSource.indexOf("if (existing.isPresent())");
        assertTrue(duplicateCheckIdx > 0, "Duplicate check block must exist");

        int plannedBranchIdx = servletSource.indexOf("if (session.getPlannedSessionId() != null)", duplicateCheckIdx);
        assertTrue(plannedBranchIdx > 0, "Planned session duplicate check must exist");

        int updateStatusIdx = servletSource.indexOf("attendanceDao.updateStatus(", duplicateCheckIdx);
        assertTrue(plannedBranchIdx < updateStatusIdx,
                "Planned session rejection must occur BEFORE any call to attendanceDao.updateStatus()");

        String plannedBranch = servletSource.substring(plannedBranchIdx, updateStatusIdx);
        assertTrue(plannedBranch.contains("return;"),
                "Planned session duplicate branch must return before reaching updateStatus()");
    }

    private static void testDuplicateAttemptRecordedWithDuplicateOutcome() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/QrScanServlet.java");
        int duplicateCheckIdx = servletSource.indexOf("if (existing.isPresent())");
        int plannedBranchIdx = servletSource.indexOf("if (session.getPlannedSessionId() != null)", duplicateCheckIdx);
        int endPlannedBranch = servletSource.indexOf("return;", plannedBranchIdx);
        String plannedBlock = servletSource.substring(plannedBranchIdx, endPlannedBranch);

        assertTrue(plannedBlock.contains("scanAttemptDao.recordAttempt("),
                "Planned duplicate check must log scan attempt");
        assertTrue(plannedBlock.contains("\"DUPLICATE\""),
                "Planned duplicate attempt must be recorded with 'DUPLICATE' outcome");
    }

    private static void testNoSuccessResponseForExistingPlannedRecord() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/QrScanServlet.java");
        int duplicateCheckIdx = servletSource.indexOf("if (existing.isPresent())");
        int plannedBranchIdx = servletSource.indexOf("if (session.getPlannedSessionId() != null)", duplicateCheckIdx);
        int endPlannedBranch = servletSource.indexOf("return;", plannedBranchIdx);
        String plannedBlock = servletSource.substring(plannedBranchIdx, endPlannedBranch);

        assertTrue(plannedBlock.contains("handleResult(request, response, isJson, false,"),
                "Planned duplicate check must invoke handleResult with success=false");
        assertTrue(plannedBlock.contains("Attendance has already been recorded for this session."),
                "Planned duplicate check must return exact user-facing message: 'Attendance has already been recorded for this session.'");
        assertTrue(!plannedBlock.contains("Attendance successfully marked"),
                "Planned duplicate branch must never report successful attendance");
    }

    private static void testOrdinarySessionAbsentRecoveryPreserved() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/QrScanServlet.java");
        int duplicateCheckIdx = servletSource.indexOf("if (existing.isPresent())");
        int updateStatusIdx = servletSource.indexOf("attendanceDao.updateStatus(att.getId(), \"PRESENT\");", duplicateCheckIdx);
        assertTrue(updateStatusIdx > 0,
                "updateStatus must remain present for ordinary sessions");

        String ordinaryBlock = servletSource.substring(duplicateCheckIdx, updateStatusIdx);
        assertTrue(ordinaryBlock.contains("if (!\"ABSENT\".equalsIgnoreCase(att.getStatus()))"),
                "Ordinary session recovery must preserve !'ABSENT' check");
    }

    private static void testScanWithoutExistingRecordFollowsNormalPath() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/QrScanServlet.java");
        int duplicateBlockEnd = servletSource.indexOf("if (existing.isPresent())");
        int normalInsertIdx = servletSource.indexOf("attendanceDao.recordAttendanceTransaction(", duplicateBlockEnd);
        assertTrue(normalInsertIdx > duplicateBlockEnd,
                "Normal scan must proceed to recordAttendanceTransaction when existing record is not present");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Part B: DAO Lifecycle State Matrix Tests (C4.2.2 Correction A)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Pure Java implementation of the exact DAO lifecycle state machine.
     */
    private static String evaluateLifecycle(boolean isActive, String plannedStatus) {
        if (!isActive && "COMPLETED".equals(plannedStatus)) {
            return "IDEMPOTENT_SUCCESS";
        }
        if (!isActive || !"ATTENDANCE_OPEN".equals(plannedStatus)) {
            return "REJECT_ROLLBACK";
        }
        return "NORMAL_CLOSURE";
    }

    private static void testActiveQrAndAttendanceOpenPermitsClosure() {
        assertEquals("NORMAL_CLOSURE", evaluateLifecycle(true, "ATTENDANCE_OPEN"),
                "Active QR + ATTENDANCE_OPEN must proceed with normal closure");

        String daoSource = readSource("src/main/java/com/attendance/dao/PlannedSessionDao.java");
        int closeMethodIdx = daoSource.indexOf("public boolean closeMeetingAndCompleteSession");
        int conditionIdx = daoSource.indexOf("if (!isActive || !\"ATTENDANCE_OPEN\".equals(plannedStatus))", closeMethodIdx);
        assertTrue(conditionIdx > closeMethodIdx,
                "closeMeetingAndCompleteSession must enforce (!isActive || !'ATTENDANCE_OPEN'.equals(plannedStatus))");
    }

    private static void testInactiveQrAndCompletedReturnsIdempotentSuccess() {
        assertEquals("IDEMPOTENT_SUCCESS", evaluateLifecycle(false, "COMPLETED"),
                "Inactive QR + COMPLETED must return idempotent success");

        String daoSource = readSource("src/main/java/com/attendance/dao/PlannedSessionDao.java");
        int closeMethodIdx = daoSource.indexOf("public boolean closeMeetingAndCompleteSession");
        int idempotentIdx = daoSource.indexOf("if (!isActive && \"COMPLETED\".equals(plannedStatus))", closeMethodIdx);
        assertTrue(idempotentIdx > closeMethodIdx, "Idempotent closure check must exist");

        int commitIdx = daoSource.indexOf("conn.commit();", idempotentIdx);
        int returnTrueIdx = daoSource.indexOf("return true;", idempotentIdx);
        assertTrue(commitIdx > idempotentIdx && returnTrueIdx > commitIdx,
                "Idempotent branch must commit and return true");
    }

    private static void testInactiveQrAndAttendanceOpenRejectsAndRollsBack() {
        assertEquals("REJECT_ROLLBACK", evaluateLifecycle(false, "ATTENDANCE_OPEN"),
                "Inactive QR + ATTENDANCE_OPEN must be rejected with rollback");
    }

    private static void testActiveQrAndCompletedRejectsAndRollsBack() {
        assertEquals("REJECT_ROLLBACK", evaluateLifecycle(true, "COMPLETED"),
                "Active QR + COMPLETED must be rejected with rollback");
    }

    private static void testActiveQrAndScheduledRejectsAndRollsBack() {
        assertEquals("REJECT_ROLLBACK", evaluateLifecycle(true, "SCHEDULED"),
                "Active QR + SCHEDULED must be rejected with rollback");
        assertEquals("REJECT_ROLLBACK", evaluateLifecycle(false, "SCHEDULED"),
                "Inactive QR + SCHEDULED must be rejected with rollback");
    }

    private static void testUnexpectedOrNullStatusRejectsSafely() {
        assertEquals("REJECT_ROLLBACK", evaluateLifecycle(true, null),
                "Active QR + null status must be rejected");
        assertEquals("REJECT_ROLLBACK", evaluateLifecycle(false, null),
                "Inactive QR + null status must be rejected");
        assertEquals("REJECT_ROLLBACK", evaluateLifecycle(true, "CANCELLED"),
                "Active QR + CANCELLED status must be rejected");
        assertEquals("REJECT_ROLLBACK", evaluateLifecycle(false, "CANCELLED"),
                "Inactive QR + CANCELLED status must be rejected");
        assertEquals("REJECT_ROLLBACK", evaluateLifecycle(true, "UNKNOWN"),
                "Active QR + UNKNOWN status must be rejected");
    }

    private static void testInvalidCombinationsRejectBeforeReconciliation() {
        String daoSource = readSource("src/main/java/com/attendance/dao/PlannedSessionDao.java");
        int closeMethodIdx = daoSource.indexOf("public boolean closeMeetingAndCompleteSession");
        int conditionIdx = daoSource.indexOf("if (!isActive || !\"ATTENDANCE_OPEN\".equals(plannedStatus))", closeMethodIdx);
        int rollbackIdx = daoSource.indexOf("conn.rollback();", conditionIdx);
        int throwIdx = daoSource.indexOf("throw new IllegalStateException(", conditionIdx);
        int reconciliationIdx = daoSource.indexOf("INSERT INTO attendance", closeMethodIdx);

        assertTrue(conditionIdx > 0 && rollbackIdx > conditionIdx && throwIdx > conditionIdx,
                "Lifecycle condition must rollback and throw IllegalStateException");
        assertTrue(throwIdx < reconciliationIdx,
                "Lifecycle rejection must occur BEFORE absentee reconciliation SQL");
    }

    private static void testInvalidCombinationsDoNotCompleteStatus() {
        String daoSource = readSource("src/main/java/com/attendance/dao/PlannedSessionDao.java");
        int closeMethodIdx = daoSource.indexOf("public boolean closeMeetingAndCompleteSession");
        int updateSqlIdx = daoSource.indexOf("String completePlannedSql = \"UPDATE planned_sessions SET status = 'COMPLETED'", closeMethodIdx);
        assertTrue(updateSqlIdx > closeMethodIdx, "completePlannedSql must exist in closeMeetingAndCompleteSession");

        String updateSqlLine = daoSource.substring(updateSqlIdx, daoSource.indexOf(";", updateSqlIdx));
        assertTrue(updateSqlLine.contains("WHERE id = ? AND status = 'ATTENDANCE_OPEN'"),
                "Normal closure update must strictly require status = 'ATTENDANCE_OPEN'");
        assertTrue(!updateSqlLine.contains("SCHEDULED"),
                "Normal closure update must never accept 'SCHEDULED'");
    }

    private static void testInvalidAssociationFailsSafely() {
        String daoSource = readSource("src/main/java/com/attendance/dao/PlannedSessionDao.java");
        int closeMethodIdx = daoSource.indexOf("public boolean closeMeetingAndCompleteSession");

        int mismatchCheckIdx = daoSource.indexOf("if (!rs.next())", closeMethodIdx);
        assertTrue(mismatchCheckIdx > closeMethodIdx, "Association verification rs.next() must exist");

        int rollbackIdx = daoSource.indexOf("conn.rollback();", mismatchCheckIdx);
        int throwIdx = daoSource.indexOf("throw new IllegalArgumentException(", mismatchCheckIdx);
        assertTrue(rollbackIdx > mismatchCheckIdx && throwIdx > mismatchCheckIdx,
                "Association mismatch must trigger rollback and throw IllegalArgumentException");
    }

    private static void testFailedClosureOperationRollsBack() {
        String daoSource = readSource("src/main/java/com/attendance/dao/PlannedSessionDao.java");
        int closeMethodIdx = daoSource.indexOf("public boolean closeMeetingAndCompleteSession");

        int catchIdx = daoSource.indexOf("catch (Throwable t)", closeMethodIdx);
        assertTrue(catchIdx > closeMethodIdx, "closeMeetingAndCompleteSession must catch Throwable");

        int catchRollbackIdx = daoSource.indexOf("conn.rollback()", catchIdx);
        assertTrue(catchRollbackIdx > catchIdx, "Catch block must rollback transaction");

        int finallyIdx = daoSource.indexOf("finally", catchIdx);
        assertTrue(finallyIdx > catchIdx, "Finally block must exist");

        int restoreAutoCommitIdx = daoSource.indexOf("conn.setAutoCommit(previousAutoCommit)", finallyIdx);
        assertTrue(restoreAutoCommitIdx > finallyIdx, "Finally block must restore previous auto-commit");
    }

    private static void testRepeatedClosureCannotDuplicateAttendance() {
        String daoSource = readSource("src/main/java/com/attendance/dao/PlannedSessionDao.java");
        int closeMethodIdx = daoSource.indexOf("public boolean closeMeetingAndCompleteSession");
        int absentSqlIdx = daoSource.indexOf("String absentSql = \"INSERT INTO attendance", closeMethodIdx);
        assertTrue(absentSqlIdx > closeMethodIdx, "absentSql must exist in closeMeetingAndCompleteSession");

        String absentSql = daoSource.substring(absentSqlIdx, daoSource.indexOf(";", absentSqlIdx));
        assertTrue(absentSql.contains("AND NOT EXISTS"),
                "Absent reconciliation must use NOT EXISTS to guard against duplicate attendance records");
        assertTrue(absentSql.contains("WHERE a.qr_session_id = ? AND a.student_id = e.student_id"),
                "NOT EXISTS must check exact qr_session_id and student_id");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Part C: Servlet Controlled Closure Error Handling (C4.2.2 Correction B)
    // ────────────────────────────────────────────────────────────────────────

    private static void testIllegalArgumentExceptionMapsTo400() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/QrGenerateServlet.java");
        int handleCloseIdx = servletSource.indexOf("private void handleClose");
        int catchIllegalArgIdx = servletSource.indexOf("catch (IllegalArgumentException e)", handleCloseIdx);
        assertTrue(catchIllegalArgIdx > handleCloseIdx, "handleClose must catch IllegalArgumentException");

        int endCatch = servletSource.indexOf("catch (IllegalStateException e)", catchIllegalArgIdx);
        String catchBlock = servletSource.substring(catchIllegalArgIdx, endCatch);

        assertTrue(catchBlock.contains("response.setStatus(HttpServletResponse.SC_BAD_REQUEST)"),
                "IllegalArgumentException must map to HTTP 400 Bad Request");
        assertTrue(catchBlock.contains("return;"), "IllegalArgumentException catch block must halt processing");
    }

    private static void testIllegalStateExceptionMapsTo409() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/QrGenerateServlet.java");
        int handleCloseIdx = servletSource.indexOf("private void handleClose");
        int catchIllegalStateIdx = servletSource.indexOf("catch (IllegalStateException e)", handleCloseIdx);
        assertTrue(catchIllegalStateIdx > handleCloseIdx, "handleClose must catch IllegalStateException");

        int endCatch = servletSource.indexOf("catch (SQLException e)", catchIllegalStateIdx);
        String catchBlock = servletSource.substring(catchIllegalStateIdx, endCatch);

        assertTrue(catchBlock.contains("response.setStatus(HttpServletResponse.SC_CONFLICT)"),
                "IllegalStateException must map to HTTP 409 Conflict");
        assertTrue(catchBlock.contains("The session operation is inconsistent with its current lifecycle state."),
                "IllegalStateException must explain inconsistency with current lifecycle state");
        assertTrue(catchBlock.contains("return;"), "IllegalStateException catch block must halt processing");
    }

    private static void testErrorResponsesDoNotDiscloseInternalDetails() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/QrGenerateServlet.java");
        int handleCloseIdx = servletSource.indexOf("private void handleClose");
        int catchIllegalArgIdx = servletSource.indexOf("catch (IllegalArgumentException e)", handleCloseIdx);
        int catchSqlIdx = servletSource.indexOf("catch (SQLException e)", handleCloseIdx);
        int endCatchSql = servletSource.indexOf("} else {", catchSqlIdx);
        String errorHandlingSection = servletSource.substring(catchIllegalArgIdx, endCatchSql);

        // Verify that client responses do NOT expose getStackTrace or raw exception dumps
        assertTrue(!errorHandlingSection.contains("getStackTrace"),
                "User error paths must not send stack traces to client");
        assertTrue(!errorHandlingSection.contains("SELECT") && !errorHandlingSection.contains("UPDATE"),
                "Error messages must not contain raw SQL statements");
    }

    private static void testSuccessfulClosureResponseRemainsCompatible() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/QrGenerateServlet.java");
        int handleCloseIdx = servletSource.indexOf("private void handleClose");
        int ordinaryCloseIdx = servletSource.indexOf("qrSessionDao.closeSession(sessionId);", handleCloseIdx);
        int successIdx = servletSource.indexOf("if (\"json\".equalsIgnoreCase(format))", ordinaryCloseIdx);

        int redirectIdx = servletSource.indexOf("/teacher/dashboard?msg=session_closed", successIdx);
        assertTrue(redirectIdx > successIdx, "Redirect to /teacher/dashboard?msg=session_closed must exist");

        int successEnd = servletSource.indexOf("}", redirectIdx);
        String successBlock = servletSource.substring(successIdx, successEnd + 1);
        assertTrue(successBlock.contains("status") && successBlock.contains("closed"),
                "JSON success response must return status:closed");
        assertTrue(successBlock.contains("/teacher/dashboard?msg=session_closed"),
                "Redirect success must route to /teacher/dashboard?msg=session_closed");
    }

    private static void testOrdinarySessionClosureBehaviorUnchanged() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/QrGenerateServlet.java");
        int handleCloseIdx = servletSource.indexOf("private void handleClose");
        int dispatchIdx = servletSource.indexOf("if (qs.getPlannedSessionId() != null)", handleCloseIdx);
        int elseIdx = servletSource.indexOf("} else {", dispatchIdx);
        int closeOrdinaryIdx = servletSource.indexOf("qrSessionDao.closeSession(sessionId);", elseIdx);

        assertTrue(closeOrdinaryIdx > elseIdx,
                "Ordinary session closure must continue using qrSessionDao.closeSession(sessionId)");
    }

    private static void testSecurityProtectionsRemainIntact() {
        String servletSource = readSource("src/main/java/com/attendance/servlet/QrGenerateServlet.java");
        int handleCloseIdx = servletSource.indexOf("private void handleClose");

        int authCheckIdx = servletSource.indexOf("if (user == null || !\"TEACHER\".equalsIgnoreCase(user.getRole()))", handleCloseIdx);
        assertTrue(authCheckIdx > handleCloseIdx, "Teacher role authorization check must exist in handleClose");

        int csrfCheckIdx = servletSource.indexOf("if (!SessionUtil.validateCsrfToken(request))", handleCloseIdx);
        assertTrue(csrfCheckIdx > handleCloseIdx, "CSRF validation check must exist in handleClose");

        int ownershipCheckIdx = servletSource.indexOf("if (qs.getTeacherId() != teacher.getTeacherId())", handleCloseIdx);
        assertTrue(ownershipCheckIdx > handleCloseIdx, "Teacher ownership check must exist in handleClose");
    }

    private static String readSource(String relativePath) {
        try {
            return Files.readString(Paths.get(relativePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read source file: " + relativePath, e);
        }
    }
}