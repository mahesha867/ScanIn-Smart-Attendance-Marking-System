package com.attendance.dao;

import com.attendance.model.Course;
import com.attendance.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Courses.
 * Demonstrates:
 * - Implements Dao<Course>
 * - Course queries and student enrollment verification
 */
public class CourseDao implements Dao<Course> {

    @Override
    public Optional<Course> findById(int id) {
        String sql = "SELECT c.*, u.name AS teacher_name " +
                     "FROM courses c " +
                     "JOIN teachers t ON c.teacher_id = t.teacher_id " +
                     "JOIN users u ON t.user_id = u.id " +
                     "WHERE c.id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToCourse(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<Course> findByCourseCode(String courseCode) {
        String sql = "SELECT c.*, u.name AS teacher_name " +
                     "FROM courses c " +
                     "JOIN teachers t ON c.teacher_id = t.teacher_id " +
                     "JOIN users u ON t.user_id = u.id " +
                     "WHERE c.course_code = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, courseCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToCourse(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public List<Course> findByTeacherId(int teacherId) {
        List<Course> courses = new ArrayList<>();
        String sql = "SELECT c.*, u.name AS teacher_name " +
                     "FROM courses c " +
                     "JOIN teachers t ON c.teacher_id = t.teacher_id " +
                     "JOIN users u ON t.user_id = u.id " +
                     "WHERE c.teacher_id = ? " +
                     "ORDER BY c.course_code ASC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teacherId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    courses.add(mapResultSetToCourse(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return courses;
    }

    @Override
    public List<Course> findAll() {
        List<Course> courses = new ArrayList<>();
        String sql = "SELECT c.*, u.name AS teacher_name " +
                     "FROM courses c " +
                     "JOIN teachers t ON c.teacher_id = t.teacher_id " +
                     "JOIN users u ON t.user_id = u.id " +
                     "ORDER BY c.course_code ASC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                courses.add(mapResultSetToCourse(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return courses;
    }

    public boolean isStudentEnrolled(int studentId, int courseId) {
        String sql = "SELECT 1 FROM enrollments WHERE student_id = ? AND course_id = ? AND (status = 'ACTIVE' OR status IS NULL)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Enrolls a student in a course, enforcing uniqueness to prevent duplicate registrations.
     * Preserves historical enrollment lifecycle by reactivating previously dropped records.
     */
    public boolean enrollStudent(int studentId, int courseId) {
        String checkSql = "SELECT status FROM enrollments WHERE student_id = ? AND course_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement psCheck = conn.prepareStatement(checkSql)) {
            psCheck.setInt(1, studentId);
            psCheck.setInt(2, courseId);
            try (ResultSet rs = psCheck.executeQuery()) {
                if (rs.next()) {
                    String status = rs.getString("status");
                    if (status == null || "ACTIVE".equalsIgnoreCase(status)) {
                        return false; // Prevent duplicate active registration
                    }
                    // Reactivate dropped enrollment record while preserving original history
                    String updateSql = "UPDATE enrollments SET status = 'ACTIVE', dropped_at = NULL, enrolled_at = CURRENT_TIMESTAMP WHERE student_id = ? AND course_id = ?";
                    try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                        psUpdate.setInt(1, studentId);
                        psUpdate.setInt(2, courseId);
                        return psUpdate.executeUpdate() > 0;
                    }
                }
            }

            // Fresh enrollment
            String sql = "INSERT INTO enrollments (student_id, course_id, status, enrolled_at) VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                ps.setInt(2, courseId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Soft-drops a student's enrollment from a course to preserve historical attendance association.
     */
    public boolean dropStudent(int studentId, int courseId) {
        String sql = "UPDATE enrollments SET status = 'DROPPED', dropped_at = CURRENT_TIMESTAMP WHERE student_id = ? AND course_id = ? AND (status = 'ACTIVE' OR status IS NULL)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, courseId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Retrieves all available courses not yet enrolled by the student.
     */
    public List<Course> getAvailableCoursesForStudent(int studentId) {
        List<Course> courses = new ArrayList<>();
        String sql = "SELECT c.*, u.name AS teacher_name " +
                     "FROM courses c " +
                     "JOIN teachers t ON c.teacher_id = t.teacher_id " +
                     "JOIN users u ON t.user_id = u.id " +
                     "WHERE c.id NOT IN (SELECT course_id FROM enrollments WHERE student_id = ? AND (status = 'ACTIVE' OR status IS NULL)) " +
                     "ORDER BY c.course_code ASC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    courses.add(mapResultSetToCourse(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return courses;
    }

    @Override
    public boolean save(Course course) {
        String sql = "INSERT INTO courses (course_code, course_name, teacher_id, semester, department) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, course.getCourseCode());
            ps.setString(2, course.getCourseName());
            ps.setInt(3, course.getTeacherId());
            ps.setInt(4, course.getSemester());
            ps.setString(5, course.getDepartment());
            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        course.setId(keys.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean update(Course course) {
        String sql = "UPDATE courses SET course_name = ?, semester = ?, department = ? WHERE id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, course.getCourseName());
            ps.setInt(2, course.getSemester());
            ps.setString(3, course.getDepartment());
            ps.setInt(4, course.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean delete(int id) {
        String sql = "DELETE FROM courses WHERE id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private Course mapResultSetToCourse(ResultSet rs) throws SQLException {
        Course c = new Course();
        c.setId(rs.getInt("id"));
        c.setCourseCode(rs.getString("course_code"));
        c.setCourseName(rs.getString("course_name"));
        c.setTeacherId(rs.getInt("teacher_id"));
        c.setTeacherName(rs.getString("teacher_name"));
        c.setSemester(rs.getInt("semester"));
        c.setDepartment(rs.getString("department"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            c.setCreatedAt(ts.toLocalDateTime());
        }
        return c;
    }
}
