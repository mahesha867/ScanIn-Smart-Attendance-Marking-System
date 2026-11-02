package com.attendance;

import com.attendance.model.Attendance;
import com.attendance.model.AttendanceAuditLog;
import com.attendance.model.PlannedSession;
import com.attendance.model.SessionRosterItem;
import com.attendance.util.DateTimeUtil;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

/**
 * ScanIn Stage 25B - Offline Automated Verification Suite for UTC-to-IST Timestamp Remediation.
 *
 * Verifies:
 * - TZ-01: Standard daytime conversion (2026-11-02T05:17:08Z -> 02-11-2026 10:47:08 AM IST)
 * - TZ-02: Afternoon conversion (2026-11-02T10:30:00Z -> 02-11-2026 04:00:00 PM IST)
 * - TZ-03: UTC midnight date crossing (2026-10-31T20:00:00Z -> 01-11-2026 01:30:00 AM IST)
 * - TZ-04: IST exact midnight boundary (2026-11-01T18:30:00Z -> 02-11-2026 12:00:00 AM IST)
 * - TZ-05: Null and missing timestamp handling (returns "N/A", no NPE)
 * - TZ-06: Student attendance model (Attendance.java) canonical Instant and getMarkedAtIst()
 * - TZ-07: Faculty live roster model (SessionRosterItem.java) canonical Instant and IST presentation
 * - TZ-08: Audit log model (AttendanceAuditLog.java) canonical Instant and IST formatting
 * - TZ-09: Complete JVM default timezone independence (tested under NY, Tokyo, London, IST)
 * - TZ-10: Repeated formatting idempotency (no double-shift)
 * - TZ-11: Planned session wall-clock scheduling invariance (sessionDate, startTime, endTime preserved)
 * - TZ-12: Full contract compatibility between Instant and LocalDateTime adapters
 */
public class Stage25BTimezoneRemediationTest {

    private static int totalPassed = 0;
    private static int totalFailed = 0;

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   ScanIn Stage 25B: Automated UTC-to-IST Timestamp Remediation Suite");
        System.out.println("================================================================================");

        runTest("TZ-01: Standard daytime UTC to IST conversion (05:17:08Z -> 10:47:08 AM IST)",
                Stage25BTimezoneRemediationTest::testTz01StandardDaytimeConversion);

        runTest("TZ-02: Afternoon UTC to IST conversion (10:30:00Z -> 04:00:00 PM IST)",
                Stage25BTimezoneRemediationTest::testTz02AfternoonConversion);

        runTest("TZ-03: Midnight UTC date crossing into next IST day (Oct 31 20:00Z -> Nov 1 01:30 AM IST)",
                Stage25BTimezoneRemediationTest::testTz03MidnightDateCrossing);

        runTest("TZ-04: Exact IST midnight boundary conversion (18:30:00Z -> 12:00:00 AM IST)",
                Stage25BTimezoneRemediationTest::testTz04ExactMidnightBoundary);

        runTest("TZ-05: Null and missing timestamp safety (returns 'N/A' without NPE)",
                Stage25BTimezoneRemediationTest::testTz05NullTimestampSafety);

        runTest("TZ-06: Student Attendance entity canonical Instant mapping and getMarkedAtIst()",
                Stage25BTimezoneRemediationTest::testTz06StudentAttendanceEntity);

        runTest("TZ-07: Faculty SessionRosterItem canonical Instant mapping and IST presentation",
                Stage25BTimezoneRemediationTest::testTz07FacultyRosterItem);

        runTest("TZ-08: AttendanceAuditLog entity canonical Instant mapping and getCreatedAtIst()",
                Stage25BTimezoneRemediationTest::testTz08AttendanceAuditLogEntity);

        runTest("TZ-09: Robust independence from JVM default timezone (tested under NY, Tokyo, London, IST)",
                Stage25BTimezoneRemediationTest::testTz09JvmTimezoneIndependence);

        runTest("TZ-10: Repeated formatting idempotency (no double-shift)",
                Stage25BTimezoneRemediationTest::testTz10NoDoubleShift);

        runTest("TZ-11: Planned session wall-clock scheduling invariance (date and time inputs preserved)",
                Stage25BTimezoneRemediationTest::testTz11PlannedSessionSchedulingInvariance);

        runTest("TZ-12: Full contract compatibility between Instant and LocalDateTime adapters",
                Stage25BTimezoneRemediationTest::testTz12ContractCompatibility);

        // ── Stage 25D ISO Formatter Verification Tests ─────────────────────
        runTest("ISO-01: Correct ISO offset output (05:17:08Z -> 2026-11-02T10:47:08+05:30)",
                Stage25BTimezoneRemediationTest::testIso01CorrectOffsetOutput);

        runTest("ISO-02: Midnight rollover ISO output (Nov 01 20:00:00Z -> 2026-11-02T01:30:00+05:30)",
                Stage25BTimezoneRemediationTest::testIso02MidnightRollover);

        runTest("ISO-03: Offset presence (+05:30) in ISO output",
                Stage25BTimezoneRemediationTest::testIso03OffsetPresence);

        runTest("ISO-04: JVM timezone independence for ISO formatter (NY, Tokyo, London, Kolkata)",
                Stage25BTimezoneRemediationTest::testIso04JvmTimezoneIndependence);

        runTest("ISO-05: Null handling for ISO formatter (returns N/A)",
                Stage25BTimezoneRemediationTest::testIso05NullHandling);

        runTest("ISO-06: Legacy overload consistency for ISO formatter (LocalDateTime as UTC)",
                Stage25BTimezoneRemediationTest::testIso06LegacyOverloadConsistency);

        System.out.println("================================================================================");
        System.out.printf("   Stage 25B Timezone Remediation Suite: %d Passed | %d Failed%n", totalPassed, totalFailed);
        System.out.println("================================================================================");

        if (totalFailed > 0) {
            System.exit(1);
        }
    }

    private static void runTest(String testName, Runnable testMethod) {
        try {
            testMethod.run();
            System.out.printf("[PASS] %s%n", testName);
            totalPassed++;
        } catch (Throwable t) {
            System.err.printf("[FAIL] %s - %s%n", testName, t.getMessage());
            t.printStackTrace(System.err);
            totalFailed++;
        }
    }

    // TZ-01: Standard daytime conversion
    private static void testTz01StandardDaytimeConversion() {
        Instant instant = Instant.parse("2026-11-02T05:17:08Z");
        String formatted = DateTimeUtil.formatToIst(instant);
        assertEquals("02-11-2026 10:47:08 AM IST", formatted, "TZ-01 formatted string mismatch");

        Timestamp ts = Timestamp.from(instant);
        assertEquals("02-11-2026 10:47:08 AM IST", DateTimeUtil.formatToIst(ts), "TZ-01 Timestamp overload mismatch");

        LocalDateTime utc = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        assertEquals("02-11-2026 10:47:08 AM IST", DateTimeUtil.formatToIst(utc), "TZ-01 LocalDateTime adapter mismatch");
    }

    // TZ-02: Afternoon conversion
    private static void testTz02AfternoonConversion() {
        Instant instant = Instant.parse("2026-11-02T10:30:00Z");
        String formatted = DateTimeUtil.formatToIst(instant);
        assertEquals("02-11-2026 04:00:00 PM IST", formatted, "TZ-02 afternoon formatting mismatch");
    }

    // TZ-03: Midnight date crossing
    private static void testTz03MidnightDateCrossing() {
        // Oct 31 20:00 UTC + 05:30 -> Nov 01 01:30 AM IST
        Instant instant = Instant.parse("2026-10-31T20:00:00Z");
        String formatted = DateTimeUtil.formatToIst(instant);
        assertEquals("01-11-2026 01:30:00 AM IST", formatted, "TZ-03 date crossing mismatch");
    }

    // TZ-04: Exact midnight boundary
    private static void testTz04ExactMidnightBoundary() {
        // Nov 01 18:30 UTC + 05:30 -> Nov 02 00:00:00 (12:00:00 AM IST)
        Instant instant = Instant.parse("2026-11-01T18:30:00Z");
        String formatted = DateTimeUtil.formatToIst(instant);
        assertEquals("02-11-2026 12:00:00 AM IST", formatted, "TZ-04 midnight boundary mismatch");
    }

    // TZ-05: Null safety
    private static void testTz05NullTimestampSafety() {
        assertEquals("N/A", DateTimeUtil.formatToIst((Instant) null), "Null Instant must format as N/A");
        assertEquals("N/A", DateTimeUtil.formatToIst((Timestamp) null), "Null Timestamp must format as N/A");
        assertEquals("N/A", DateTimeUtil.formatToIst((LocalDateTime) null), "Null LocalDateTime must format as N/A");
        assertEquals("N/A", DateTimeUtil.formatToIstStandard((Instant) null), "Null Instant standard format must be N/A");
        assertEquals("N/A", DateTimeUtil.formatToIstIso((Instant) null), "Null Instant ISO format must be N/A");
    }

    // TZ-06: Student Attendance entity
    private static void testTz06StudentAttendanceEntity() {
        Instant instant = Instant.parse("2026-11-02T05:17:08Z");
        Attendance att = new Attendance();
        att.setId(407);
        att.setStudentId(64);
        att.setCourseId(16);
        att.setStatus("PRESENT");
        att.setMarkedAtInstant(instant);

        assertEquals(instant, att.getMarkedAtInstant(), "Attendance markedAtInstant mismatch");
        assertEquals("02-11-2026 10:47:08 AM IST", att.getMarkedAtIst(), "Attendance getMarkedAtIst mismatch");

        // LocalDateTime getter adapter
        LocalDateTime expectedLdt = LocalDateTime.of(2026, 11, 2, 5, 17, 8);
        assertEquals(expectedLdt, att.getMarkedAt(), "Attendance getMarkedAt() UTC adapter mismatch");

        // Null attendance handling
        att.setMarkedAtInstant(null);
        assertEquals("N/A", att.getMarkedAtIst(), "Null markedAt must format as N/A");
    }

    // TZ-07: Faculty SessionRosterItem
    private static void testTz07FacultyRosterItem() {
        Instant instant = Instant.parse("2026-11-02T05:17:08Z");
        SessionRosterItem item = new SessionRosterItem();
        item.setStudentId(64);
        item.setRollNumber("CS2021001");
        item.setStudentName("Aarav Singh");
        item.setAttendanceStatus("PRESENT");
        item.setMarkedAtInstant(instant);

        assertEquals(instant, item.getMarkedAtInstant(), "RosterItem markedAtInstant mismatch");
        assertEquals("02-11-2026 10:47:08 AM IST", item.getMarkedAtIst(), "RosterItem getMarkedAtIst mismatch");
        assertEquals("02-11-2026 10:47:08 AM IST", DateTimeUtil.formatToIst(item.getMarkedAt()),
                "DateTimeUtil.formatToIst(item.getMarkedAt()) contract mismatch");
    }

    // TZ-08: AttendanceAuditLog entity
    private static void testTz08AttendanceAuditLogEntity() {
        Instant instant = Instant.parse("2026-11-02T05:17:08Z");
        AttendanceAuditLog log = new AttendanceAuditLog();
        log.setAttendanceId(407);
        log.setStudentId(64);
        log.setCreatedAtInstant(instant);

        assertEquals(instant, log.getCreatedAtInstant(), "AuditLog createdAtInstant mismatch");
        assertEquals("02-11-2026 10:47:08 AM IST", log.getCreatedAtIst(), "AuditLog getCreatedAtIst mismatch");
    }

    // TZ-09: Complete JVM timezone independence
    private static void testTz09JvmTimezoneIndependence() {
        TimeZone original = TimeZone.getDefault();
        Instant testInstant = Instant.parse("2026-11-02T05:17:08Z");
        String expected = "02-11-2026 10:47:08 AM IST";

        String[] testZones = new String[]{
                "UTC", "America/New_York", "Asia/Tokyo", "Europe/London", "Asia/Kolkata", "Australia/Sydney"
        };

        try {
            for (String zoneId : testZones) {
                TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
                String result = DateTimeUtil.formatToIst(testInstant);
                if (!expected.equals(result)) {
                    throw new AssertionError(String.format("Timezone independence failed under JVM TZ '%s': expected '%s', got '%s'",
                            zoneId, expected, result));
                }
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    // TZ-10: Repeated formatting idempotency
    private static void testTz10NoDoubleShift() {
        Instant instant = Instant.parse("2026-11-02T05:17:08Z");
        String first = DateTimeUtil.formatToIst(instant);
        String second = DateTimeUtil.formatToIst(instant);
        String third = DateTimeUtil.formatToIst(instant);

        assertEquals("02-11-2026 10:47:08 AM IST", first, "First pass format mismatch");
        assertEquals(first, second, "Repeated format must be idempotent");
        assertEquals(second, third, "Third pass format must remain invariant");
    }

    // TZ-11: Planned session wall-clock scheduling invariance
    private static void testTz11PlannedSessionSchedulingInvariance() {
        PlannedSession ps = new PlannedSession();
        LocalDate date = LocalDate.of(2026, 11, 2);
        LocalTime start = LocalTime.of(10, 0);
        LocalTime end = LocalTime.of(11, 0);

        ps.setSessionDate(date);
        ps.setStartTime(start);
        ps.setEndTime(end);

        assertEquals(date, ps.getSessionDate(), "sessionDate must remain unmodified LocalDate");
        assertEquals(start, ps.getStartTime(), "startTime must remain unmodified LocalTime");
        assertEquals(end, ps.getEndTime(), "endTime must remain unmodified LocalTime");

        // Attendance marked timestamp on planned session
        Instant marked = Instant.parse("2026-11-02T05:17:08Z");
        ps.setAttendanceMarkedAtInstant(marked);
        assertEquals(marked, ps.getAttendanceMarkedAtInstant(), "attendanceMarkedAtInstant mismatch");
        assertEquals("02-11-2026 10:47:08 AM IST", ps.getAttendanceMarkedAtIst(), "getAttendanceMarkedAtIst mismatch");
    }

    // TZ-12: Full contract compatibility between Instant and LocalDateTime adapters
    private static void testTz12ContractCompatibility() {
        LocalDateTime utc = LocalDateTime.of(2026, 11, 2, 5, 17, 8);
        Instant instant = utc.toInstant(ZoneOffset.UTC);

        String fromUtc = DateTimeUtil.formatToIst(utc);
        String fromInstant = DateTimeUtil.formatToIst(instant);

        assertEquals("02-11-2026 10:47:08 AM IST", fromUtc, "LocalDateTime adapter format mismatch");
        assertEquals("02-11-2026 10:47:08 AM IST", fromInstant, "Instant format mismatch");
        assertEquals(fromUtc, fromInstant, "Instant and LocalDateTime adapter outputs must be identical");
    }

    // ── Stage 25D ISO Formatter Verification Implementations ─────────

    // ISO-01: Correct ISO offset output
    private static void testIso01CorrectOffsetOutput() {
        Instant instant = Instant.parse("2026-11-02T05:17:08Z");
        String formatted = DateTimeUtil.formatToIstIso(instant);
        assertEquals("2026-11-02T10:47:08+05:30", formatted, "ISO-01 offset output mismatch");
    }

    // ISO-02: Midnight rollover
    private static void testIso02MidnightRollover() {
        Instant instant = Instant.parse("2026-11-01T20:00:00Z");
        String formatted = DateTimeUtil.formatToIstIso(instant);
        assertEquals("2026-11-02T01:30:00+05:30", formatted, "ISO-02 midnight rollover mismatch");
    }

    // ISO-03: Offset presence
    private static void testIso03OffsetPresence() {
        Instant instant = Instant.parse("2026-11-02T05:17:08Z");
        String formatted = DateTimeUtil.formatToIstIso(instant);
        if (!formatted.endsWith("+05:30")) {
            throw new AssertionError("ISO-03 expected output to end with '+05:30', got: " + formatted);
        }
    }

    // ISO-04: JVM timezone independence
    private static void testIso04JvmTimezoneIndependence() {
        TimeZone original = TimeZone.getDefault();
        try {
            Instant instant = Instant.parse("2026-11-02T05:17:08Z");
            String[] testZones = {"America/New_York", "Asia/Tokyo", "Europe/London", "Asia/Kolkata"};
            for (String zone : testZones) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                String formatted = DateTimeUtil.formatToIstIso(instant);
                assertEquals("2026-11-02T10:47:08+05:30", formatted, "ISO-04 mismatch under JVM zone: " + zone);
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    // ISO-05: Null handling
    private static void testIso05NullHandling() {
        assertEquals("N/A", DateTimeUtil.formatToIstIso((Instant) null), "ISO-05 null Instant mismatch");
        assertEquals("N/A", DateTimeUtil.formatToIstIso((Timestamp) null), "ISO-05 null Timestamp mismatch");
        assertEquals("N/A", DateTimeUtil.formatToIstIso((LocalDateTime) null), "ISO-05 null LocalDateTime mismatch");
    }

    // ISO-06: Legacy overload consistency
    private static void testIso06LegacyOverloadConsistency() {
        Instant instant = Instant.parse("2026-11-02T05:17:08Z");
        LocalDateTime utc = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        Timestamp ts = Timestamp.from(instant);

        String fromInstant = DateTimeUtil.formatToIstIso(instant);
        String fromUtc = DateTimeUtil.formatToIstIso(utc);
        String fromTs = DateTimeUtil.formatToIstIso(ts);

        assertEquals("2026-11-02T10:47:08+05:30", fromInstant, "Instant ISO output mismatch");
        assertEquals(fromInstant, fromUtc, "LocalDateTime ISO overload mismatch");
        assertEquals(fromInstant, fromTs, "Timestamp ISO overload mismatch");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError(String.format("%s [Expected: <%s>, Actual: <%s>]", message, expected, actual));
    }
}
