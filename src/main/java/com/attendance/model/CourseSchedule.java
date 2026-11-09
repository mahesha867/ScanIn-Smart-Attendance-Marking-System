package com.attendance.model;

import java.sql.Time;
import java.time.LocalDateTime;

/**
 * Entity representing weekly scheduled lecture timeslots for courses.
 */
public class CourseSchedule {

    private int id;
    private int courseId;
    private String courseCode;
    private String courseName;
    private String teacherName;
    private String dayOfWeek;
    private Time startTime;
    private Time endTime;
    private String room;
    private boolean conducted;
    private LocalDateTime createdAt;

    public CourseSchedule() {}

    public CourseSchedule(int id, int courseId, String dayOfWeek, Time startTime, Time endTime, String room) {
        this.id = id;
        this.courseId = courseId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.room = room;
        this.createdAt = LocalDateTime.now();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getCourseId() { return courseId; }
    public void setCourseId(int courseId) { this.courseId = courseId; }

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

    public String getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public Time getStartTime() { return startTime; }
    public void setStartTime(Time startTime) { this.startTime = startTime; }

    public Time getEndTime() { return endTime; }
    public void setEndTime(Time endTime) { this.endTime = endTime; }

    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }

    public boolean isConducted() { return conducted; }
    public void setConducted(boolean conducted) { this.conducted = conducted; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
