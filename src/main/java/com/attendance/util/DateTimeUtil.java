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
 * - Standard Display Format: "dd MMM yyyy, hh:mm a" (e.g., "09 Nov 2026, 03:11 PM")
 * - Standard Log/CSV Format: "yyyy-MM-dd HH:mm:ss" (e.g., "2026-11-09 15:11:00")
 *
 * Guarantees:
 * - Converts from canonical UTC to Asia/Kolkata using java.time.ZonedDateTime
 * - Handles UTC date-crossings cleanly without manual arithmetic
 * - Thread-safe, centralized formatters preventing display discrepancies
 */
public final class DateTimeUtil {

    public static final ZoneId UTC_ZONE = ZoneOffset.UTC;
    public static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    public static final String IST_DISPLAY_PATTERN = "dd MMM yyyy, hh:mm a";
    public static final String IST_STANDARD_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String ISO_IST_PATTERN = "yyyy-MM-dd'T'HH:mm:ssXXX";

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
     * Formats a UTC LocalDateTime into IST presentation string: "dd MMM yyyy, hh:mm a".
     */
    public static String formatToIst(LocalDateTime utcDateTime) {
        if (utcDateTime == null) return "N/A";
        return toIst(utcDateTime).format(IST_DISPLAY_FORMATTER);
    }

    /**
     * Formats an Instant into IST presentation string: "dd MMM yyyy, hh:mm a".
     */
    public static String formatToIst(Instant instant) {
        if (instant == null) return "N/A";
        return toIst(instant).format(IST_DISPLAY_FORMATTER);
    }

    /**
     * Formats a Timestamp into IST presentation string: "dd MMM yyyy, hh:mm a".
     */
    public static String formatToIst(Timestamp timestamp) {
        if (timestamp == null) return "N/A";
        return toIst(timestamp).format(IST_DISPLAY_FORMATTER);
    }

    /**
     * Formats into standard timestamp: "yyyy-MM-dd HH:mm:ss".
     */
    public static String formatToIstStandard(LocalDateTime utcDateTime) {
        if (utcDateTime == null) return "N/A";
        return toIst(utcDateTime).format(IST_STANDARD_FORMATTER);
    }

    public static String formatToIstStandard(Instant instant) {
        if (instant == null) return "N/A";
        return toIst(instant).format(IST_STANDARD_FORMATTER);
    }

    public static String formatToIstStandard(Timestamp timestamp) {
        if (timestamp == null) return "N/A";
        return toIst(timestamp).format(IST_STANDARD_FORMATTER);
    }

    /**
     * Formats into ISO-8601 offset representation in Asia/Kolkata timezone:
     * "yyyy-MM-dd'T'HH:mm:ss+05:30".
     */
    public static String formatToIstIso(LocalDateTime utcDateTime) {
        if (utcDateTime == null) return "N/A";
        return toIst(utcDateTime).format(ISO_IST_FORMATTER);
    }

    public static String formatToIstIso(Instant instant) {
        if (instant == null) return "N/A";
        return toIst(instant).format(ISO_IST_FORMATTER);
    }

    public static String formatToIstIso(Timestamp timestamp) {
        if (timestamp == null) return "N/A";
        return toIst(timestamp).format(ISO_IST_FORMATTER);
    }

    /**
     * Formats using a custom pattern in Asia/Kolkata timezone.
     */
    public static String formatToIst(LocalDateTime utcDateTime, String pattern) {
        if (utcDateTime == null) return "N/A";
        return toIst(utcDateTime).format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
    }

    public static String formatToIst(Instant instant, String pattern) {
        if (instant == null) return "N/A";
        return toIst(instant).format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
    }

    public static String formatToIst(Timestamp timestamp, String pattern) {
        if (timestamp == null) return "N/A";
        return toIst(timestamp).format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
    }
}

