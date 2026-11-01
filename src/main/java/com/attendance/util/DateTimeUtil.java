package com.attendance.util;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Centralized Timezone and Date-Time Utility for ScanIn.
 *
 * Architectural Strategy:
 * - Canonical Storage & Internal Processing: UTC (Coordinated Universal Time)
 * - Faculty & User Presentation Timezone: Indian Standard Time (IST - Asia/Kolkata, UTC+05:30)
 * - Standard Display Format: "dd-MM-yyyy hh:mm:ss a IST" (e.g., "01-11-2026 10:45:32 AM IST")
 *
 * Guarantees:
 * - Converts from canonical UTC to Asia/Kolkata using java.time.ZonedDateTime
 * - Handles UTC midnight date-crossings correctly without manual hour additions
 * - Thread-safe, centralized formatters preventing duplicate or missing conversions
 */
public final class DateTimeUtil {

    public static final ZoneId UTC_ZONE = ZoneOffset.UTC;
    public static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    public static final String IST_DISPLAY_PATTERN = "dd-MM-yyyy hh:mm:ss a 'IST'";
    public static final String IST_STANDARD_PATTERN = "dd-MM-yyyy hh:mm:ss a";
    public static final String ISO_IST_PATTERN = "yyyy-MM-dd hh:mm:ss a";

    public static final DateTimeFormatter IST_DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern(IST_DISPLAY_PATTERN, Locale.ENGLISH);

    public static final DateTimeFormatter IST_STANDARD_FORMATTER =
            DateTimeFormatter.ofPattern(IST_STANDARD_PATTERN, Locale.ENGLISH);

    public static final DateTimeFormatter ISO_IST_FORMATTER =
            DateTimeFormatter.ofPattern(ISO_IST_PATTERN, Locale.ENGLISH);

    private DateTimeUtil() {}

    /**
     * Converts a UTC LocalDateTime to an Asia/Kolkata ZonedDateTime.
     */
    public static ZonedDateTime toIst(LocalDateTime utcDateTime) {
        if (utcDateTime == null) return null;
        return utcDateTime.atZone(UTC_ZONE).withZoneSameInstant(IST_ZONE);
    }

    /**
     * Converts an absolute Instant to an Asia/Kolkata ZonedDateTime.
     */
    public static ZonedDateTime toIst(Instant instant) {
        if (instant == null) return null;
        return instant.atZone(IST_ZONE);
    }

    /**
     * Converts a java.sql.Timestamp (epoch instant) to an Asia/Kolkata ZonedDateTime.
     */
    public static ZonedDateTime toIst(Timestamp timestamp) {
        if (timestamp == null) return null;
        return timestamp.toInstant().atZone(IST_ZONE);
    }

    /**
     * Formats a UTC LocalDateTime into IST presentation string: "dd-MM-yyyy hh:mm:ss a IST".
     */
    public static String formatToIst(LocalDateTime utcDateTime) {
        if (utcDateTime == null) return "N/A";
        return toIst(utcDateTime).format(IST_DISPLAY_FORMATTER);
    }

    /**
     * Formats an Instant into IST presentation string: "dd-MM-yyyy hh:mm:ss a IST".
     */
    public static String formatToIst(Instant instant) {
        if (instant == null) return "N/A";
        return toIst(instant).format(IST_DISPLAY_FORMATTER);
    }

    /**
     * Formats a Timestamp into IST presentation string: "dd-MM-yyyy hh:mm:ss a IST".
     */
    public static String formatToIst(Timestamp timestamp) {
        if (timestamp == null) return "N/A";
        return toIst(timestamp).format(IST_DISPLAY_FORMATTER);
    }

    /**
     * Formats without the trailing 'IST' literal: "dd-MM-yyyy hh:mm:ss a".
     */
    public static String formatToIstStandard(LocalDateTime utcDateTime) {
        if (utcDateTime == null) return "N/A";
        return toIst(utcDateTime).format(IST_STANDARD_FORMATTER);
    }

    /**
     * Formats without the trailing 'IST' literal: "dd-MM-yyyy hh:mm:ss a".
     */
    public static String formatToIstStandard(Instant instant) {
        if (instant == null) return "N/A";
        return toIst(instant).format(IST_STANDARD_FORMATTER);
    }

    /**
     * Formats into ISO date order in IST: "yyyy-MM-dd hh:mm:ss a".
     */
    public static String formatToIstIso(LocalDateTime utcDateTime) {
        if (utcDateTime == null) return "N/A";
        return toIst(utcDateTime).format(ISO_IST_FORMATTER);
    }

    /**
     * Formats into ISO date order in IST: "yyyy-MM-dd hh:mm:ss a".
     */
    public static String formatToIstIso(Instant instant) {
        if (instant == null) return "N/A";
        return toIst(instant).format(ISO_IST_FORMATTER);
    }

    /**
     * Formats a UTC LocalDateTime using a custom pattern in Asia/Kolkata timezone.
     */
    public static String formatToIst(LocalDateTime utcDateTime, String pattern) {
        if (utcDateTime == null) return "N/A";
        return toIst(utcDateTime).format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
    }

    /**
     * Formats an Instant using a custom pattern in Asia/Kolkata timezone.
     */
    public static String formatToIst(Instant instant, String pattern) {
        if (instant == null) return "N/A";
        return toIst(instant).format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
    }
}
