package com.attendance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * ScanIn Stage 13 — Phase 13-C4.4: Teacher Attendance Roster Frontend Contract Test Suite.
 *
 * Scope:
 * - Teacher Dashboard Action Binding & Ownership Preservation
 * - Endpoint URL & Parameter Contract
 * - Safe DOM Manipulation (Anti-XSS & textContent enforcement)
 * - All Seven Presentation State Badges & Visual Distinction
 * - Attendance Percentage & Independent In-Progress State
 * - Inconsistency Isolation (Never Inferred as ABSENT)
 * - Student Privacy (Zero Student Email in Table)
 * - Positive Integer Parameter Validation
 * - UI States (Loading, Empty, and Error Handling for 400, 403, 404, 500)
 * - Refresh & Request De-duplication
 */
public class Stage13PhaseC44FrontendContractTest {

    private static int totalPassed = 0;
    private static int totalFailed = 0;

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("  Stage 13-C4.4 Teacher Attendance Roster Frontend Contract Tests ");
        System.out.println("==================================================================");

        // ── 1. Dashboard Integration & Parameter Binding ────────────────────
        runTest("C4.4-01: Teacher dashboard contains View Roster action [Static JSP Contract]",
                Stage13PhaseC44FrontendContractTest::testDashboardContainsRosterAction);

        runTest("C4.4-02: Action is bound to planned session ID [Static JSP Contract]",
                Stage13PhaseC44FrontendContractTest::testActionBoundToPlannedSessionId);

        runTest("C4.4-03: Action does not expose or pass client-side teacher ID [Static Security Contract]",
                Stage13PhaseC44FrontendContractTest::testNoClientTeacherIdExposed);

        // ── 2. Endpoint URL & Query Contract ────────────────────────────────
        runTest("C4.4-04: JS fetches from /teacher/planned-sessions/roster [Static JS Contract]",
                Stage13PhaseC44FrontendContractTest::testCorrectEndpointRequested);

        runTest("C4.4-05: Query parameter is strictly plannedSessionId [Static JS Contract]",
                Stage13PhaseC44FrontendContractTest::testQueryParameterStrictlyPlannedSessionId);

        runTest("C4.4-06: plannedSessionId validated as positive integer before fetch [Static JS Contract]",
                Stage13PhaseC44FrontendContractTest::testPositiveIntegerValidationBeforeFetch);

        // ── 3. DOM Safety & Privacy ─────────────────────────────────────────
        runTest("C4.4-07: Untrusted fields inserted via textContent (Zero unsafe innerHTML) [Static Security Contract]",
                Stage13PhaseC44FrontendContractTest::testUntrustedFieldsUseTextContent);

        runTest("C4.4-08: Student email is not exposed in table header or cells [Static Privacy Contract]",
                Stage13PhaseC44FrontendContractTest::testStudentEmailNotExposedInTable);

        // ── 4. Presentation State Badges & Distinction ──────────────────────
        runTest("C4.4-09: All seven presentation statuses supported in mapping [Static JS Contract]",
                Stage13PhaseC44FrontendContractTest::testAllSevenPresentationStatusesSupported);

        runTest("C4.4-10: DATA INCONSISTENCY visually distinguished with dedicated style [Static CSS/JS Contract]",
                Stage13PhaseC44FrontendContractTest::testDataInconsistencyVisuallyDistinguished);

        // ── 5. Metrics & State Decoupling ───────────────────────────────────
        runTest("C4.4-11: Percentage displays N/A when inapplicable or null (Zero JS recomputation) [Static JS Contract]",
                Stage13PhaseC44FrontendContractTest::testPercentageDisplaysNaWhenInapplicable);

        runTest("C4.4-12: In-progress state displayed independently of percentage applicability [Static JS Contract]",
                Stage13PhaseC44FrontendContractTest::testInProgressDisplayedIndependently);

        runTest("C4.4-13: Inconsistency alert communicates reason without treating inconsistent as ABSENT [Static JS Contract]",
                Stage13PhaseC44FrontendContractTest::testInconsistencyAlertCommunicatesReason);

        // ── 6. UI States & Robustness ───────────────────────────────────────
        runTest("C4.4-14: Modal contains loading, empty, and error state containers [Static HTML Contract]",
                Stage13PhaseC44FrontendContractTest::testModalContainsLoadingEmptyAndErrorStates);

        runTest("C4.4-15: HTTP 400, 403, 404, and 500 error messages handled cleanly without DB leak [Static JS Contract]",
                Stage13PhaseC44FrontendContractTest::testHttpErrorHandlingWithoutDbLeak);

        runTest("C4.4-16: Refresh action re-fetches current session without page reload [Static JS Contract]",
                Stage13PhaseC44FrontendContractTest::testRefreshActionRefetchesCurrentSession);

        runTest("C4.4-17: In-flight duplicate requests are guarded by fetch flag [Static JS Contract]",
                Stage13PhaseC44FrontendContractTest::testInFlightRequestDeduplication);

        runTest("C4.4-18: Keyboard accessibility supports Escape key modal dismissal [Static JS Contract]",
                Stage13PhaseC44FrontendContractTest::testKeyboardEscapeModalDismissal);

        // ── 7. C4.4.2 Request Race & Accessibility Corrections ────────────────
        runTest("C4.4-19: Session switching immediately supersedes pending request [Static JS Contract]",
                Stage13PhaseC44FrontendContractTest::testSessionSwitchingSupersedesPendingRequest);

        runTest("C4.4-20: Stale response from earlier session discarded before DOM rendering [Static JS Contract]",
                Stage13PhaseC44FrontendContractTest::testStaleResponseDiscardedBeforeDomRendering);

        runTest("C4.4-21: Modal closure aborts in-flight request and invalidates requestId [Static JS Contract]",
                Stage13PhaseC44FrontendContractTest::testModalClosureAbortsAndInvalidates);

        runTest("C4.4-22: Modal reopening increments generation and cancels previous signal [Static JS Contract]",
                Stage13PhaseC44FrontendContractTest::testModalReopeningIncrementsGeneration);

        runTest("C4.4-23: Older request failure ignored if newer request active [Static JS Contract]",
                Stage13PhaseC44FrontendContractTest::testOlderRequestFailureIgnored);

        runTest("C4.4-24: Invoking button saved and focus restored upon modal close [Static JS Contract]",
                Stage13PhaseC44FrontendContractTest::testFocusRestorationOnClose);

        runTest("C4.4-25: Modal focus trapping implements Tab and Shift+Tab wrapping [Static JS Contract]",
                Stage13PhaseC44FrontendContractTest::testModalFocusTrapping);

        runTest("C4.4-26: Visible focus indicators defined for keyboard navigation [Static CSS Contract]",
                Stage13PhaseC44FrontendContractTest::testVisibleFocusIndicatorsDefined);

        // ── 8. C4.4.2.2 Delayed Focus Hardening ──────────────────────────────
        runTest("C4.4-27: Delayed initial focus callback guarded against closed modal [Static JS Contract]",
                Stage13PhaseC44FrontendContractTest::testDelayedFocusModalVisibilityGuarded);

        System.out.println("==================================================================");
        System.out.printf("  Stage 13-C4.4 Test Results: %d Passed, %d Failed%n", totalPassed, totalFailed);
        System.out.println("==================================================================");

        if (totalFailed > 0) {
            System.err.println("❌ STAGE 13-C4.4 CONTRACT VERIFICATION FAILED");
            System.exit(1);
        } else {
            System.out.println("✅ STAGE 13-C4.4 CONTRACT VERIFICATION PASSED");
        }
    }

    private static void runTest(String testName, Runnable test) {
        try {
            test.run();
            System.out.println("  [PASS] " + testName);
            totalPassed++;
        } catch (Throwable t) {
            System.err.println("  [FAIL] " + testName + ": " + t.getMessage());
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
        throw new AssertionError(message + " (Expected: " + expected + ", Actual: " + actual + ")");
    }

    // ────────────────────────────────────────────────────────────────────────
    // 1. Dashboard Integration & Parameter Binding
    // ────────────────────────────────────────────────────────────────────────

    private static void testDashboardContainsRosterAction() {
        String jspSource = readSource("src/main/webapp/WEB-INF/views/teacher-dashboard.jsp");
        assertTrue(jspSource.contains("btn-roster-view"), "JSP must contain btn-roster-view action button");
        assertTrue(jspSource.contains("openSessionRoster("), "JSP must call openSessionRoster");
    }

    private static void testActionBoundToPlannedSessionId() {
        String jspSource = readSource("src/main/webapp/WEB-INF/views/teacher-dashboard.jsp");
        assertTrue(jspSource.contains("openSessionRoster(<%= ps.getId() %>"),
                "Action must bind ps.getId() to openSessionRoster");
        assertTrue(jspSource.contains("data-session-id=\"<%= ps.getId() %>\""),
                "Action button must declare data-session-id attribute");
    }

    private static void testNoClientTeacherIdExposed() {
        String jspSource = readSource("src/main/webapp/WEB-INF/views/teacher-dashboard.jsp");
        String jsSource = readSource("src/main/webapp/js/session-roster.js");

        assertTrue(!jspSource.contains("openSessionRoster(<%= ps.getId() %>, <%= teacher"),
                "openSessionRoster must not accept teacherId argument");
        assertTrue(!jsSource.contains("teacherId"),
                "session-roster.js must not reference or pass teacherId");
    }

    // ────────────────────────────────────────────────────────────────────────
    // 2. Endpoint URL & Query Contract
    // ────────────────────────────────────────────────────────────────────────

    private static void testCorrectEndpointRequested() {
        String jsSource = readSource("src/main/webapp/js/session-roster.js");
        assertTrue(jsSource.contains("'/teacher/planned-sessions/roster?plannedSessionId='"),
                "JS must query '/teacher/planned-sessions/roster?plannedSessionId='");
    }

    private static void testQueryParameterStrictlyPlannedSessionId() {
        String jsSource = readSource("src/main/webapp/js/session-roster.js");
        assertTrue(jsSource.contains("plannedSessionId="), "Query parameter must be plannedSessionId");
        assertTrue(!jsSource.contains("&teacherId="), "Endpoint request must not contain teacherId parameter");
        assertTrue(!jsSource.contains("&courseId="), "Endpoint request must not require courseId parameter");
    }

    private static void testPositiveIntegerValidationBeforeFetch() {
        String jsSource = readSource("src/main/webapp/js/session-roster.js");
        assertTrue(jsSource.contains("!Number.isInteger(idNum) || idNum <= 0"),
                "plannedSessionId must be validated as a positive integer before dispatching request");
    }

    // ────────────────────────────────────────────────────────────────────────
    // 3. DOM Safety & Privacy
    // ────────────────────────────────────────────────────────────────────────

    private static void testUntrustedFieldsUseTextContent() {
        String jsSource = readSource("src/main/webapp/js/session-roster.js");
        assertTrue(!jsSource.contains(".innerHTML = student.studentName"),
                "Must not use innerHTML for studentName");
        assertTrue(!jsSource.contains(".innerHTML = student.rollNumber"),
                "Must not use innerHTML for rollNumber");
        assertTrue(!jsSource.contains(".innerHTML = ps.courseName"),
                "Must not use innerHTML for courseName");
        assertTrue(jsSource.contains("tdName.textContent = student.studentName"),
                "Must use textContent for studentName");
        assertTrue(jsSource.contains("tdRoll.textContent = student.rollNumber"),
                "Must use textContent for rollNumber");
    }

    private static void testStudentEmailNotExposedInTable() {
        String jspSource = readSource("src/main/webapp/WEB-INF/views/teacher-dashboard.jsp");
        String jsSource = readSource("src/main/webapp/js/session-roster.js");

        int tableStart = jspSource.indexOf("<table class=\"data-table\"");
        int tableEnd = jspSource.indexOf("</table>", tableStart);
        String tableHeader = jspSource.substring(tableStart, tableEnd);

        assertTrue(!tableHeader.toLowerCase().contains("email"),
                "Roster table header must not include Email column");
        assertTrue(!jsSource.contains("student.email"),
                "Roster JS must not access or display student.email");
    }

    // ────────────────────────────────────────────────────────────────────────
    // 4. Presentation State Badges & Distinction
    // ────────────────────────────────────────────────────────────────────────

    private static void testAllSevenPresentationStatusesSupported() {
        String jsSource = readSource("src/main/webapp/js/session-roster.js");
        assertTrue(jsSource.contains("case 'PRESENT':"), "Must support PRESENT");
        assertTrue(jsSource.contains("case 'LATE':"), "Must support LATE");
        assertTrue(jsSource.contains("case 'ABSENT':"), "Must support ABSENT");
        assertTrue(jsSource.contains("case 'NOT ACTIVATED':"), "Must support NOT ACTIVATED");
        assertTrue(jsSource.contains("case 'NOT YET RECORDED':"), "Must support NOT YET RECORDED");
        assertTrue(jsSource.contains("case 'NOT RECORDED':"), "Must support NOT RECORDED");
        assertTrue(jsSource.contains("case 'DATA INCONSISTENCY':"), "Must support DATA INCONSISTENCY");
    }

    private static void testDataInconsistencyVisuallyDistinguished() {
        String jsSource = readSource("src/main/webapp/js/session-roster.js");
        String cssSource = readSource("src/main/webapp/css/style.css");

        assertTrue(jsSource.contains("badge-status-inconsistency"),
                "JS must assign badge-status-inconsistency for DATA INCONSISTENCY");
        assertTrue(cssSource.contains(".badge-status-inconsistency"),
                "CSS must define .badge-status-inconsistency class");
    }

    // ────────────────────────────────────────────────────────────────────────
    // 5. Metrics & State Decoupling
    // ────────────────────────────────────────────────────────────────────────

    private static void testPercentageDisplaysNaWhenInapplicable() {
        String jsSource = readSource("src/main/webapp/js/session-roster.js");
        assertTrue(jsSource.contains("metrics.percentageApplicable === true && metrics.attendancePercentage !== null"),
                "Percentage must only be displayed when percentageApplicable is true and attendancePercentage is non-null");
        assertTrue(jsSource.contains("pctEl.textContent = 'N/A'"),
                "Percentage must display 'N/A' when inapplicable or null");
        assertTrue(!jsSource.contains("metrics.presentCount / metrics.totalEnrolled"),
                "JS must never calculate a replacement percentage");
    }

    private static void testInProgressDisplayedIndependently() {
        String jsSource = readSource("src/main/webapp/js/session-roster.js");
        assertTrue(jsSource.contains("if (metrics.inProgress)"),
                "In-progress badge must be evaluated from metrics.inProgress directly");
        assertTrue(jsSource.contains("LIVE / IN PROGRESS"),
                "Must display LIVE / IN PROGRESS badge when inProgress is true");
    }

    private static void testInconsistencyAlertCommunicatesReason() {
        String jsSource = readSource("src/main/webapp/js/session-roster.js");
        assertTrue(jsSource.contains("inconsistencyBanner.style.display = 'flex'"),
                "Inconsistency banner must be displayed when inconsistencies exist");
        assertTrue(jsSource.contains("NOT counted as Absent"),
                "Inconsistency alert must clarify that inconsistent records are not counted as Absent");
    }

    // ────────────────────────────────────────────────────────────────────────
    // 6. UI States & Robustness
    // ────────────────────────────────────────────────────────────────────────

    private static void testModalContainsLoadingEmptyAndErrorStates() {
        String jspSource = readSource("src/main/webapp/WEB-INF/views/teacher-dashboard.jsp");
        assertTrue(jspSource.contains("id=\"rosterLoadingIndicator\""),
                "JSP must contain rosterLoadingIndicator element");
        assertTrue(jspSource.contains("id=\"rosterErrorContainer\""),
                "JSP must contain rosterErrorContainer element");
        assertTrue(jspSource.contains("id=\"rosterEmptyState\""),
                "JSP must contain rosterEmptyState element");
        assertTrue(jspSource.contains("id=\"rosterTableContainer\""),
                "JSP must contain rosterTableContainer element");
    }

    private static void testHttpErrorHandlingWithoutDbLeak() {
        String jsSource = readSource("src/main/webapp/js/session-roster.js");
        assertTrue(jsSource.contains("response.status === 400"), "Must handle HTTP 400");
        assertTrue(jsSource.contains("response.status === 403"), "Must handle HTTP 403");
        assertTrue(jsSource.contains("response.status === 404"), "Must handle HTTP 404");
        assertTrue(jsSource.contains("response.status === 500"), "Must handle HTTP 500");
        assertTrue(!jsSource.contains("SQLException"), "Error messages must not expose SQLException");
        assertTrue(!jsSource.contains("stackTrace"), "Error messages must not expose stack traces");
    }

    private static void testRefreshActionRefetchesCurrentSession() {
        String jsSource = readSource("src/main/webapp/js/session-roster.js");
        assertTrue(jsSource.contains("function refreshCurrentSessionRoster()"),
                "Must define refreshCurrentSessionRoster()");
        assertTrue(jsSource.contains("fetchSessionRoster(currentSessionId)"),
                "Refresh must re-fetch currentSessionId");
    }

    private static void testInFlightRequestDeduplication() {
        String jsSource = readSource("src/main/webapp/js/session-roster.js");
        assertTrue(jsSource.contains("if (isFetching && currentSessionId === sessionId)"),
                "fetchSessionRoster must prevent concurrent duplicate fetches with isFetching guard");
    }

    private static void testKeyboardEscapeModalDismissal() {
        String jsSource = readSource("src/main/webapp/js/session-roster.js");
        assertTrue(jsSource.contains("e.key === 'Escape'"),
                "JS must listen for Escape key to close modal");
    }

    private static void testSessionSwitchingSupersedesPendingRequest() {
        String jsSource = readSource("src/main/webapp/js/session-roster.js");
        assertTrue(jsSource.contains("currentAbortController.abort()"),
                "fetchSessionRoster must abort in-flight fetch when a different session is requested");
        assertTrue(jsSource.contains("const requestId = ++activeRequestId;"),
                "fetchSessionRoster must increment request sequence id on each new fetch");
    }

    private static void testStaleResponseDiscardedBeforeDomRendering() {
        String jsSource = readSource("src/main/webapp/js/session-roster.js");
        assertTrue(jsSource.contains("if (requestId !== activeRequestId)"),
                "Response handler must check requestId against activeRequestId before processing");
        assertTrue(jsSource.contains("if (requestId === activeRequestId) {"),
                "Data rendering must strictly check requestId === activeRequestId before updating DOM");
    }

    private static void testModalClosureAbortsAndInvalidates() {
        String jsSource = readSource("src/main/webapp/js/session-roster.js");
        int closeIdx = jsSource.indexOf("function closeSessionRoster()");
        assertTrue(closeIdx > 0, "closeSessionRoster must exist");
        int endCloseIdx = jsSource.indexOf("function refreshCurrentSessionRoster()", closeIdx);
        String closeBlock = jsSource.substring(closeIdx, endCloseIdx);

        assertTrue(closeBlock.contains("currentAbortController.abort()"),
                "closeSessionRoster must abort in-flight controller");
        assertTrue(closeBlock.contains("activeRequestId++"),
                "closeSessionRoster must increment activeRequestId to invalidate in-flight response");
        assertTrue(closeBlock.contains("isFetching = false"),
                "closeSessionRoster must release isFetching flag");
    }

    private static void testModalReopeningIncrementsGeneration() {
        String jsSource = readSource("src/main/webapp/js/session-roster.js");
        assertTrue(jsSource.contains("++activeRequestId"),
                "Each fetch must increment activeRequestId generation counter");
        assertTrue(jsSource.contains("new AbortController()"),
                "fetchSessionRoster must initialize a fresh AbortController for new request generation");
    }

    private static void testOlderRequestFailureIgnored() {
        String jsSource = readSource("src/main/webapp/js/session-roster.js");
        assertTrue(jsSource.contains("if (requestId !== activeRequestId || (err && err.name === 'AbortError'))"),
                "Catch block must discard errors from superseded or aborted requests without altering UI error state");
    }

    private static void testFocusRestorationOnClose() {
        String jsSource = readSource("src/main/webapp/js/session-roster.js");
        assertTrue(jsSource.contains("lastTriggerButton = triggerElement;"),
                "openSessionRoster must capture the invoking trigger button or activeElement");
        assertTrue(jsSource.contains("lastTriggerButton.focus()"),
                "closeSessionRoster must restore keyboard focus to the triggering element");
    }

    private static void testModalFocusTrapping() {
        String jsSource = readSource("src/main/webapp/js/session-roster.js");
        assertTrue(jsSource.contains("if (e.key === 'Tab')"),
                "keydown listener must intercept Tab key within open modal");
        assertTrue(jsSource.contains("e.shiftKey"),
                "Focus trap must support Shift+Tab for reverse wrapping");
        assertTrue(jsSource.contains("lastEl.focus()"),
                "Shift+Tab on first element must wrap focus to last element");
        assertTrue(jsSource.contains("firstEl.focus()"),
                "Tab on last element must wrap focus to first element");
    }

    private static void testVisibleFocusIndicatorsDefined() {
        String cssSource = readSource("src/main/webapp/css/style.css");
        assertTrue(cssSource.contains(".modal-container button:focus-visible"),
                "CSS must define visible focus indicator for modal buttons");
        assertTrue(cssSource.contains("outline: 2px solid #582c83;"),
                "Focus outline must use high-contrast primary purple styling");
    }

    private static void testDelayedFocusModalVisibilityGuarded() {
        String jsSource = readSource("src/main/webapp/js/session-roster.js");

        int showModalIdx = jsSource.indexOf("function showModal()");
        assertTrue(showModalIdx >= 0, "showModal function must be defined");

        int setTimeoutIdx = jsSource.indexOf("setTimeout(function ()", showModalIdx);
        assertTrue(setTimeoutIdx >= 0, "setTimeout callback must be defined inside showModal");

        int delayIdx = jsSource.indexOf("}, 30);", setTimeoutIdx);
        assertTrue(delayIdx >= 0, "setTimeout with 30ms delay must be present in showModal");

        String callbackBody = jsSource.substring(setTimeoutIdx, delayIdx);

        assertTrue(callbackBody.contains("!modal || modal.style.display !== 'flex'"),
                "Delayed focus callback must guard against missing or non-flex modal");
        assertTrue(callbackBody.contains("return;"),
                "Delayed focus callback must return early if modal is not active/flex");

        int guardIdx = callbackBody.indexOf("modal.style.display !== 'flex'");
        int firstFocusIdx = callbackBody.indexOf(".focus()");
        assertTrue(firstFocusIdx >= 0, "Callback must contain focus placement");
        assertTrue(guardIdx < firstFocusIdx,
                "Modal visibility check must execute before any .focus() call inside delayed callback");
    }

    private static String readSource(String relativePath) {
        try {
            return Files.readString(Paths.get(relativePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read source file: " + relativePath, e);
        }
    }
}
