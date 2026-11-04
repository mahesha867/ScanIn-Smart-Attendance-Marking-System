package com.attendance.dao;

import com.attendance.model.Course;
import com.attendance.model.Student;
import com.attendance.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Students.
 * Demonstrates:
 * - Implements Dao<Student>
 * - Multi-table SQL joins (students JOIN users)
 */
public class StudentDao implements Dao<Student> {

    @Override
    public Optional<Student> findById(int studentId) {
        String sql = "SELECT s.*, u.name, u.email, u.password, u.phone, u.created_at " +
                     "FROM students s " +
                     "JOIN users u ON s.user_id = u.id " +
                     "WHERE s.student_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToStudent(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<Student> findByUserId(int userId) {
        String sql = "SELECT s.*, u.name, u.email, u.password, u.phone, u.created_at " +
                     "FROM students s " +
                     "JOIN users u ON s.user_id = u.id " +
                     "WHERE s.user_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToStudent(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<Student> findByRollNumber(String rollNumber) {
        String sql = "SELECT s.*, u.name, u.email, u.password, u.phone, u.created_at " +
                     "FROM students s " +
                     "JOIN users u ON s.user_id = u.id " +
                     "WHERE s.roll_number = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rollNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToStudent(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    @Override
    public List<Student> findAll() {
        List<Student> students = new ArrayList<>();
        String sql = "SELECT s.*, u.name, u.email, u.password, u.phone, u.created_at " +
                     "FROM students s " +
                     "JOIN users u ON s.user_id = u.id " +
                     "ORDER BY s.student_id ASC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                students.add(mapResultSetToStudent(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return students;
    }

    @Override
    public boolean save(Student student) {
        String sql = "INSERT INTO students (user_id, roll_number, department, semester) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, student.getId()); // user_id
            ps.setString(2, student.getRollNumber());
            ps.setString(3, student.getDepartment());
            ps.setInt(4, student.getSemester());
            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        student.setStudentId(keys.getInt(1));
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
    public boolean update(Student student) {
        String sql = "UPDATE students SET roll_number = ?, department = ?, semester = ? WHERE student_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, student.getRollNumber());
            ps.setString(2, student.getDepartment());
            ps.setInt(3, student.getSemester());
            ps.setInt(4, student.getStudentId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean delete(int studentId) {
        String sql = "DELETE FROM students WHERE student_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<Course> getEnrolledCourses(int studentId) {
        List<Course> courses = new ArrayList<>();
        String sql = "SELECT c.*, u.name AS teacher_name " +
                     "FROM enrollments e " +
                     "JOIN courses c ON e.course_id = c.id " +
                     "JOIN teachers t ON c.teacher_id = t.teacher_id " +
                     "JOIN users u ON t.user_id = u.id " +
                     "WHERE e.student_id = ? " +
                     "ORDER BY c.course_code ASC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
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
                    courses.add(c);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return courses;
    }

    public List<Student> findStudentsByCourse(int courseId) {
        List<Student> students = new ArrayList<>();
        String sql = "SELECT s.*, u.name, u.email, u.password, u.phone, u.created_at " +
                     "FROM enrollments e " +
                     "JOIN students s ON e.student_id = s.student_id " +
                     "JOIN users u ON s.user_id = u.id " +
                     "WHERE e.course_id = ? " +
                     "ORDER BY s.roll_number ASC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    students.add(mapResultSetToStudent(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return students;
    }

    private Student mapResultSetToStudent(ResultSet rs) throws SQLException {
        Student s = new Student();
        s.setStudentId(rs.getInt("student_id"));
        s.setId(rs.getInt("user_id"));
        s.setRollNumber(rs.getString("roll_number"));
        s.setDepartment(rs.getString("department"));
        s.setSemester(rs.getInt("semester"));
        s.setName(rs.getString("name"));
        s.setEmail(rs.getString("email"));
        s.setPassword(rs.getString("password"));
        s.setPhone(rs.getString("phone"));
        s.setRole("STUDENT");
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            s.setCreatedAt(ts.toLocalDateTime());
        }
        return s;
    }
}
