package com.attendance.model;

import java.time.LocalDateTime;

/**
 * Entity representing an immutable audit log record for manual attendance modifications.
 * Demonstrates:
 * - Auditability requirement for DBMS submissions
 * - Tracking actor, timestamp, affected student, old status, new status, and justification reason
 */
public class AttendanceAuditLog {

    private int id;
    private Integer attendanceId;
    private int studentId;
    private String studentName;
    private String rollNumber;
    private Integer sessionId;
    private String courseCode;
    private int changedByUserId;
    private String changedByName;
    private String oldStatus;
    private String newStatus;
    private String reason;
    private LocalDateTime createdAt;

    public AttendanceAuditLog() {}

    public AttendanceAuditLog(Integer attendanceId, int studentId, Integer sessionId,
                              int changedByUserId, String oldStatus, String newStatus, String reason) {
        this.attendanceId = attendanceId;
        this.studentId = studentId;
        this.sessionId = sessionId;
        this.changedByUserId = changedByUserId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Integer getAttendanceId() { return attendanceId; }
    public void setAttendanceId(Integer attendanceId) { this.attendanceId = attendanceId; }

    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getRollNumber() { return rollNumber; }
    public void setRollNumber(String rollNumber) { this.rollNumber = rollNumber; }

    public Integer getSessionId() { return sessionId; }
    public void setSessionId(Integer sessionId) { this.sessionId = sessionId; }

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }

    public int getChangedByUserId() { return changedByUserId; }
    public void setChangedByUserId(int changedByUserId) { this.changedByUserId = changedByUserId; }

    public String getChangedByName() { return changedByName; }
    public void setChangedByName(String changedByName) { this.changedByName = changedByName; }

    public String getOldStatus() { return oldStatus; }
    public void setOldStatus(String oldStatus) { this.oldStatus = oldStatus; }

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
