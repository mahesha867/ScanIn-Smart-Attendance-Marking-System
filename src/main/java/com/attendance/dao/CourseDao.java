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
        String sql = "SELECT 1 FROM enrollments WHERE student_id = ? AND course_id = ?";
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
     */
    public boolean enrollStudent(int studentId, int courseId) {
        if (isStudentEnrolled(studentId, courseId)) {
            return false; // Prevent duplicate registration
        }
        String sql = "INSERT INTO enrollments (student_id, course_id) VALUES (?, ?)";
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
     * Drops a student's enrollment from a course.
     */
    public boolean dropStudent(int studentId, int courseId) {
        String sql = "DELETE FROM enrollments WHERE student_id = ? AND course_id = ?";
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
                     "WHERE c.id NOT IN (SELECT course_id FROM enrollments WHERE student_id = ?) " +
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
