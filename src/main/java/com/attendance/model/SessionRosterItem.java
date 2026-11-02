package com.attendance.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Model representing a single student entry on a teacher-authorized planned session roster.
 *
 * Encapsulates the minimum necessary information:
 * - Student ID, Roll Number, Student Name
 * - Persisted Attendance Status (PRESENT, LATE, ABSENT, or null)
 * - Persisted Attendance Timestamp (UTC Instant / LocalDateTime, or null)
 * - Presentation Status derived from the authoritative lifecycle state machine
 *
 * Security Note:
 * Excludes student email and sensitive personal details.
 */
public class SessionRosterItem {

    private int studentId;
    private String rollNumber;
    private String studentName;
    private String attendanceStatus;
    private Instant markedAt;
    private String presentationStatus;
    private boolean inconsistent;
    private Boolean qrIsActive;

    public SessionRosterItem() {}

    public SessionRosterItem(int studentId, String rollNumber, String studentName,
                             String attendanceStatus, LocalDateTime markedAt,
                             String presentationStatus, boolean inconsistent) {
        this.studentId = studentId;
        this.rollNumber = rollNumber;
        this.studentName = studentName;
        this.attendanceStatus = attendanceStatus;
        this.markedAt = (markedAt != null ? markedAt.toInstant(ZoneOffset.UTC) : null);
        this.presentationStatus = presentationStatus;
        this.inconsistent = inconsistent;
    }

    public SessionRosterItem(int studentId, String rollNumber, String studentName,
                             String attendanceStatus, LocalDateTime markedAt,
                             String presentationStatus, boolean inconsistent,
                             Boolean qrIsActive) {
        this(studentId, rollNumber, studentName, attendanceStatus, markedAt, presentationStatus, inconsistent);
        this.qrIsActive = qrIsActive;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public String getRollNumber() {
        return rollNumber;
    }

    public void setRollNumber(String rollNumber) {
        this.rollNumber = rollNumber;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getAttendanceStatus() {
        return attendanceStatus;
    }

    public void setAttendanceStatus(String attendanceStatus) {
        this.attendanceStatus = attendanceStatus;
    }

    public Instant getMarkedAtInstant() {
        return markedAt;
    }

    public void setMarkedAtInstant(Instant markedAt) {
        this.markedAt = markedAt;
    }

    public LocalDateTime getMarkedAt() {
        return markedAt != null ? markedAt.atZone(ZoneOffset.UTC).toLocalDateTime() : null;
    }

    public void setMarkedAt(LocalDateTime markedAt) {
        this.markedAt = (markedAt != null ? markedAt.toInstant(ZoneOffset.UTC) : null);
    }

    /**
     * Formats marked-at timestamp into Indian Standard Time (IST - Asia/Kolkata).
     */
    public String getMarkedAtIst() {
        return com.attendance.util.DateTimeUtil.formatToIst(this.markedAt);
    }

    public String getPresentationStatus() {
        return presentationStatus;
    }

    public void setPresentationStatus(String presentationStatus) {
        this.presentationStatus = presentationStatus;
    }

    public boolean isInconsistent() {
        return inconsistent;
    }

    public void setInconsistent(boolean inconsistent) {
        this.inconsistent = inconsistent;
    }

    public Boolean getQrIsActive() {
        return qrIsActive;
    }

    public void setQrIsActive(Boolean qrIsActive) {
        this.qrIsActive = qrIsActive;
    }

    /**
     * Authoritative State Machine classifying the presentation status of a roster item.
     *
     * Valid Combinations:
     * - SCHEDULED + No QR + No Attendance                       -> NOT ACTIVATED
     * - ATTENDANCE_OPEN + QR + Active (true) + No Attendance   -> NOT YET RECORDED
     * - ATTENDANCE_OPEN + QR + Active (true) + PRESENT         -> PRESENT
     * - ATTENDANCE_OPEN + QR + Active (true) + LATE            -> LATE
     * - COMPLETED + QR + Inactive (false) + PRESENT            -> PRESENT
     * - COMPLETED + QR + Inactive (false) + LATE               -> LATE
     * - COMPLETED + QR + Inactive (false) + ABSENT             -> ABSENT
     * - COMPLETED + QR + Inactive (false) + No Attendance      -> NOT RECORDED
     *
     * Any other state combination (including SCHEDULED with QR, ATTENDANCE_OPEN with inactive QR,
     * COMPLETED with active QR, missing QR for OPEN/COMPLETED, or unknown QR active status)
     * is flagged as DATA INCONSISTENCY without falsely inferring ABSENT.
     */
    public static String determinePresentationStatus(String plannedStatus, Integer qrSessionId, Boolean qrIsActive, String attendanceStatus) {
        if ("SCHEDULED".equals(plannedStatus)) {
            if (qrSessionId == null && attendanceStatus == null) {
                return "NOT ACTIVATED";
            }
            return "DATA INCONSISTENCY";
        }

        if ("ATTENDANCE_OPEN".equals(plannedStatus)) {
            if (qrSessionId != null && Boolean.TRUE.equals(qrIsActive)) {
                if (attendanceStatus == null) {
                    return "NOT YET RECORDED";
                }
                if ("PRESENT".equals(attendanceStatus)) {
                    return "PRESENT";
                }
                if ("LATE".equals(attendanceStatus)) {
                    return "LATE";
                }
                return "DATA INCONSISTENCY";
            }
            return "DATA INCONSISTENCY";
        }

        if ("COMPLETED".equals(plannedStatus)) {
            if (qrSessionId != null && Boolean.FALSE.equals(qrIsActive)) {
                if ("PRESENT".equals(attendanceStatus)) {
                    return "PRESENT";
                }
                if ("LATE".equals(attendanceStatus)) {
                    return "LATE";
                }
                if ("ABSENT".equals(attendanceStatus)) {
                    return "ABSENT";
                }
                if (attendanceStatus == null) {
                    return "NOT RECORDED";
                }
                return "DATA INCONSISTENCY";
            }
            return "DATA INCONSISTENCY";
        }

        return "DATA INCONSISTENCY";
    }

    /**
     * Backward-compatible overload inferring standard QR active states:
     * - ATTENDANCE_OPEN implies qrIsActive = true
     * - COMPLETED implies qrIsActive = false
     */
    public static String determinePresentationStatus(String plannedStatus, Integer qrSessionId, String attendanceStatus) {
        Boolean inferredActive = null;
        if (qrSessionId != null) {
            if ("ATTENDANCE_OPEN".equals(plannedStatus)) {
                inferredActive = Boolean.TRUE;
            } else if ("COMPLETED".equals(plannedStatus)) {
                inferredActive = Boolean.FALSE;
            }
        }
        return determinePresentationStatus(plannedStatus, qrSessionId, inferredActive, attendanceStatus);
    }
}
