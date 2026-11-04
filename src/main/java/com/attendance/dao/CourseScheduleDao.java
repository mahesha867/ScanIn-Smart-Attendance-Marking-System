package com.attendance.dao;

import com.attendance.model.CourseSchedule;
import com.attendance.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Course Timetable Schedules.
 */
public class CourseScheduleDao {

    public List<CourseSchedule> findByCourseId(int courseId) {
        List<CourseSchedule> list = new ArrayList<>();
        String sql = "SELECT cs.*, c.course_code, c.course_name, u.name as teacher_name " +
                     "FROM course_schedules cs " +
                     "JOIN courses c ON cs.course_id = c.id " +
                     "JOIN teachers t ON c.teacher_id = t.teacher_id " +
                     "JOIN users u ON t.user_id = u.id " +
                     "WHERE cs.course_id = ? ORDER BY cs.start_time ASC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Retrieves upcoming classes scheduled for all courses in which the student is enrolled.
     */
    public List<CourseSchedule> findUpcomingClassesForStudent(int studentId) {
        List<CourseSchedule> list = new ArrayList<>();
        String sql = "SELECT cs.*, c.course_code, c.course_name, u.name as teacher_name " +
                     "FROM course_schedules cs " +
                     "JOIN courses c ON cs.course_id = c.id " +
                     "JOIN enrollments e ON c.id = e.course_id " +
                     "JOIN teachers t ON c.teacher_id = t.teacher_id " +
                     "JOIN users u ON t.user_id = u.id " +
                     "WHERE e.student_id = ? " +
                     "ORDER BY FIELD(cs.day_of_week, 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'), cs.start_time ASC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean save(CourseSchedule schedule) {
        String sql = "INSERT INTO course_schedules (course_id, day_of_week, start_time, end_time, room) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, schedule.getCourseId());
            ps.setString(2, schedule.getDayOfWeek());
            ps.setTime(3, schedule.getStartTime());
            ps.setTime(4, schedule.getEndTime());
            ps.setString(5, schedule.getRoom());
            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        schedule.setId(keys.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public CourseSchedule findById(int id) {
        String sql = "SELECT cs.*, c.course_code, c.course_name, u.name as teacher_name " +
                     "FROM course_schedules cs " +
                     "JOIN courses c ON cs.course_id = c.id " +
                     "JOIN teachers t ON c.teacher_id = t.teacher_id " +
                     "JOIN users u ON t.user_id = u.id " +
                     "WHERE cs.id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean delete(int id) {
        String sql = "DELETE FROM course_schedules WHERE id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private CourseSchedule mapResultSet(ResultSet rs) throws SQLException {
        CourseSchedule cs = new CourseSchedule();
        cs.setId(rs.getInt("id"));
        cs.setCourseId(rs.getInt("course_id"));
        cs.setCourseCode(rs.getString("course_code"));
        cs.setCourseName(rs.getString("course_name"));
        cs.setTeacherName(rs.getString("teacher_name"));
        cs.setDayOfWeek(rs.getString("day_of_week"));
        cs.setStartTime(rs.getTime("start_time"));
        cs.setEndTime(rs.getTime("end_time"));
        cs.setRoom(rs.getString("room"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) cs.setCreatedAt(ts.toLocalDateTime());
        return cs;
    }
}
