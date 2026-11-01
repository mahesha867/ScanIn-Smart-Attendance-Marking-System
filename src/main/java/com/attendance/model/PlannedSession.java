package com.attendance.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

/**
 * Entity representing a pre-planned calendar-dated academic session.
 * Supports individual sessions and consecutive multi-hour academic blocks.
 */
public class PlannedSession {

    private int id;
    private String blockId;
    private int courseId;
    private String courseCode;
    private String courseName;
    private int teacherId;
    private String teacherName;
    private String sessionType; // LECTURE or PRACTICAL
    private LocalDate sessionDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String room;
    private String description;
    private String status; // SCHEDULED, ATTENDANCE_OPEN, COMPLETED, CANCELLED, MISSED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Student timetable attendance enrichment (Phase 13-C3)
    private String attendanceStatus; // PRESENT, LATE, ABSENT, or null if unrecorded
    private LocalDateTime attendanceMarkedAt;

    // Phase 13-C4.6 QR Session binding & lifecycle consistency
    private Integer qrSessionId;
    private Boolean qrIsActive;

    public PlannedSession() {}

    public PlannedSession(int id, String blockId, int courseId, int teacherId,
                          String sessionType, LocalDate sessionDate,
                          LocalTime startTime, LocalTime endTime,
                          String room, String description, String status) {
        this.id = id;
        this.blockId = blockId;
        this.courseId = courseId;
        this.teacherId = teacherId;
        this.sessionType = sessionType;
        this.sessionDate = sessionDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.room = room;
        this.description = description;
        this.status = status;
    }

    // ── Getters and Setters ───────────────────────────────────────
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getBlockId() { return blockId; }
    public void setBlockId(String blockId) { this.blockId = blockId; }

    public int getCourseId() { return courseId; }
    public void setCourseId(int courseId) { this.courseId = courseId; }

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public int getTeacherId() { return teacherId; }
    public void setTeacherId(int teacherId) { this.teacherId = teacherId; }

    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

    public String getSessionType() { return sessionType; }
    public void setSessionType(String sessionType) { this.sessionType = sessionType; }

    public LocalDate getSessionDate() { return sessionDate; }
    public void setSessionDate(LocalDate sessionDate) { this.sessionDate = sessionDate; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getAttendanceStatus() { return attendanceStatus; }
    public void setAttendanceStatus(String attendanceStatus) { this.attendanceStatus = attendanceStatus; }

    public LocalDateTime getAttendanceMarkedAt() { return attendanceMarkedAt; }
    public void setAttendanceMarkedAt(LocalDateTime attendanceMarkedAt) { this.attendanceMarkedAt = attendanceMarkedAt; }

    public String getAttendanceMarkedAtIst() {
        return com.attendance.util.DateTimeUtil.formatToIst(this.attendanceMarkedAt);
    }

    public Integer getQrSessionId() {
        return qrSessionId;
    }

    public void setQrSessionId(Integer qrSessionId) {
        this.qrSessionId = qrSessionId;
    }

    public Boolean getQrIsActive() {
        return qrIsActive;
    }

    public void setQrIsActive(Boolean qrIsActive) {
        this.qrIsActive = qrIsActive;
    }

    public String getPresentationStatus() {
        return SessionRosterItem.determinePresentationStatus(this.status, this.qrSessionId, this.qrIsActive, this.attendanceStatus);
    }

    public boolean isInconsistent() {
        return "DATA INCONSISTENCY".equals(getPresentationStatus());
    }

    @Override
    public String toString() {
        return "PlannedSession{" +
                "id=" + id +
                ", blockId='" + blockId + '\'' +
                ", courseId=" + courseId +
                ", sessionType='" + sessionType + '\'' +
                ", sessionDate=" + sessionDate +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", status='" + status + '\'' +
                '}';
    }
}

