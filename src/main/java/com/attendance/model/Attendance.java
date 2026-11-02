package com.attendance.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Attendance entity representing a single student attendance record.
 * Demonstrates: Encapsulation
 */
public class Attendance {

    private int id;
    private int studentId;
    private String studentName;
    private String rollNumber;
    private int courseId;
    private String courseCode;
    private String courseName;
    private int qrSessionId;
    private Instant markedAt;
    private String status; // PRESENT, ABSENT, LATE

    public Attendance() {}

    public Attendance(int id, int studentId, int courseId, int qrSessionId,
                      Instant markedAt, String status) {
        this.id          = id;
        this.studentId   = studentId;
        this.courseId    = courseId;
        this.qrSessionId = qrSessionId;
        this.markedAt    = markedAt;
        this.status      = status;
    }

    public Attendance(int id, int studentId, int courseId, int qrSessionId,
                      LocalDateTime markedAt, String status) {
        this(id, studentId, courseId, qrSessionId,
             markedAt != null ? markedAt.toInstant(ZoneOffset.UTC) : null, status);
    }

    // ── Getters and Setters ───────────────────────────────────────
    public int getId()                                { return id; }
    public void setId(int id)                         { this.id = id; }

    public int getStudentId()                         { return studentId; }
    public void setStudentId(int studentId)           { this.studentId = studentId; }

    public String getStudentName()                    { return studentName; }
    public void setStudentName(String studentName)    { this.studentName = studentName; }

    public String getRollNumber()                     { return rollNumber; }
    public void setRollNumber(String rollNumber)      { this.rollNumber = rollNumber; }

    public int getCourseId()                          { return courseId; }
    public void setCourseId(int courseId)             { this.courseId = courseId; }

    public String getCourseCode()                     { return courseCode; }
    public void setCourseCode(String courseCode)       { this.courseCode = courseCode; }

    public String getCourseName()                     { return courseName; }
    public void setCourseName(String courseName)       { this.courseName = courseName; }

    public int getQrSessionId()                       { return qrSessionId; }
    public void setQrSessionId(int qrSessionId)       { this.qrSessionId = qrSessionId; }

    public Instant getMarkedAtInstant()               { return markedAt; }
    public void setMarkedAtInstant(Instant markedAt)  { this.markedAt = markedAt; }

    public LocalDateTime getMarkedAt() {
        return markedAt != null ? markedAt.atZone(ZoneOffset.UTC).toLocalDateTime() : null;
    }

    public void setMarkedAt(LocalDateTime markedAt) {
        this.markedAt = markedAt != null ? markedAt.toInstant(ZoneOffset.UTC) : null;
    }

    /**
     * Returns the formatted marked-at timestamp in Indian Standard Time (IST - Asia/Kolkata).
     * Example: "01-11-2026 10:45:32 AM IST"
     */
    public String getMarkedAtIst() {
        return com.attendance.util.DateTimeUtil.formatToIst(this.markedAt);
    }

    public String getStatus()                         { return status; }
    public void setStatus(String status)              { this.status = status; }

    @Override
    public String toString() {
        return "Attendance{" +
                "id=" + id +
                ", studentId=" + studentId +
                ", courseId=" + courseId +
                ", qrSessionId=" + qrSessionId +
                ", markedAt=" + markedAt +
                ", status='" + status + '\'' +
                '}';
    }
}
