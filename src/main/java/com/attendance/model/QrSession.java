package com.attendance.model;

import java.time.LocalDateTime;

/**
 * QrSession entity representing an active/expired dynamic QR code session.
 * Demonstrates: Encapsulation
 */
public class QrSession {

    private int id;
    private String sessionToken;
    private int courseId;
    private String courseCode;
    private String courseName;
    private int teacherId;
    private String teacherName;
    private LocalDateTime expiresAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private boolean active;

    public QrSession() {}

    public QrSession(int id, String sessionToken, int courseId, int teacherId,
                     LocalDateTime expiresAt, LocalDateTime createdAt, boolean active) {
        this.id           = id;
        this.sessionToken = sessionToken;
        this.courseId     = courseId;
        this.teacherId    = teacherId;
        this.expiresAt    = expiresAt;
        this.createdAt    = createdAt;
        this.active       = active;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt) || !active || closedAt != null;
    }

    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }

    // ── Getters and Setters ───────────────────────────────────────
    public int getId()                                { return id; }
    public void setId(int id)                         { this.id = id; }

    public String getSessionToken()                   { return sessionToken; }
    public void setSessionToken(String sessionToken)  { this.sessionToken = sessionToken; }

    public int getCourseId()                          { return courseId; }
    public void setCourseId(int courseId)             { this.courseId = courseId; }

    public String getCourseCode()                     { return courseCode; }
    public void setCourseCode(String courseCode)       { this.courseCode = courseCode; }

    public String getCourseName()                     { return courseName; }
    public void setCourseName(String courseName)       { this.courseName = courseName; }

    public int getTeacherId()                         { return teacherId; }
    public void setTeacherId(int teacherId)           { this.teacherId = teacherId; }

    public String getTeacherName()                    { return teacherName; }
    public void setTeacherName(String teacherName)    { this.teacherName = teacherName; }

    public LocalDateTime getExpiresAt()               { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getCreatedAt()               { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isActive()                         { return active; }
    public void setActive(boolean active)             { this.active = active; }

    @Override
    public String toString() {
        return "QrSession{" +
                "id=" + id +
                ", sessionToken='" + sessionToken + '\'' +
                ", courseId=" + courseId +
                ", expiresAt=" + expiresAt +
                ", active=" + active +
                '}';
    }
}
