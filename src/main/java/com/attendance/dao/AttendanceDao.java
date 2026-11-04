package com.attendance.dao;

import com.attendance.model.Attendance;
import com.attendance.model.AttendanceAuditLog;
import com.attendance.model.ScanAttempt;
import com.attendance.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Attendance Records.
 * Demonstrates:
 * - Implements Dao<Attendance>
 * - Multi-table SQL joins, aggregation, analytics (% attendance calculation)
 * - ACID Database Transactions with explicit commit() and rollback()
 * - Concurrency protection against duplicate scans
 */
public class AttendanceDao implements Dao<Attendance> {

    @Override
    public Optional<Attendance> findById(int id) {
        String sql = "SELECT a.*, u.name AS student_name, s.roll_number, c.course_code, c.course_name " +
                     "FROM attendance a " +
                     "JOIN students s ON a.student_id = s.student_id " +
                     "JOIN users u ON s.user_id = u.id " +
                     "JOIN courses c ON a.course_id = c.id " +
                     "WHERE a.id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToAttendance(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<Attendance> findBySessionAndStudent(int qrSessionId, int studentId) {
        String sql = "SELECT a.*, u.name AS student_name, s.roll_number, c.course_code, c.course_name " +
                     "FROM attendance a " +
                     "JOIN students s ON a.student_id = s.student_id " +
                     "JOIN users u ON s.user_id = u.id " +
                     "JOIN courses c ON a.course_id = c.id " +
                     "WHERE a.qr_session_id = ? AND a.student_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, qrSessionId);
            ps.setInt(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToAttendance(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public List<Attendance> findByCourse(int courseId) {
        List<Attendance> list = new ArrayList<>();
        String sql = "SELECT a.*, u.name AS student_name, s.roll_number, c.course_code, c.course_name " +
                     "FROM attendance a " +
                     "JOIN students s ON a.student_id = s.student_id " +
                     "JOIN users u ON s.user_id = u.id " +
                     "JOIN courses c ON a.course_id = c.id " +
                     "WHERE a.course_id = ? " +
                     "ORDER BY a.marked_at DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToAttendance(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<Attendance> findByStudent(int studentId) {
        List<Attendance> list = new ArrayList<>();
        String sql = "SELECT a.*, u.name AS student_name, s.roll_number, c.course_code, c.course_name " +
                     "FROM attendance a " +
                     "JOIN students s ON a.student_id = s.student_id " +
                     "JOIN users u ON s.user_id = u.id " +
                     "JOIN courses c ON a.course_id = c.id " +
                     "WHERE a.student_id = ? " +
                     "ORDER BY a.marked_at DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToAttendance(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<Attendance> findBySession(int qrSessionId) {
        List<Attendance> list = new ArrayList<>();
        String sql = "SELECT a.*, u.name AS student_name, s.roll_number, c.course_code, c.course_name " +
                     "FROM attendance a " +
                     "JOIN students s ON a.student_id = s.student_id " +
                     "JOIN users u ON s.user_id = u.id " +
                     "JOIN courses c ON a.course_id = c.id " +
                     "WHERE a.qr_session_id = ? " +
                     "ORDER BY a.marked_at DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, qrSessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToAttendance(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Calculates consistent attendance percentage.
     * College policy: Attended sessions = (PRESENT + LATE + OD).
     * Total sessions = all sessions initiated for the course.
     */
    public double getAttendancePercentage(int studentId, int courseId) {
        String totalSessionsSql = "SELECT COUNT(DISTINCT id) FROM qr_sessions WHERE course_id = ? AND session_token NOT LIKE 'MERGED_INTO_%'";
        String attendedSql = "SELECT COUNT(*) FROM attendance WHERE student_id = ? AND course_id = ? AND status IN ('PRESENT','LATE','OD')";

        int totalSessions = 0;
        int attendedSessions = 0;

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(totalSessionsSql)) {
                ps.setInt(1, courseId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        totalSessions = rs.getInt(1);
                    }
                }
            }
            if (totalSessions == 0) return 100.0; // No sessions conducted yet

            String excusedSql = "SELECT COUNT(*) FROM attendance WHERE student_id = ? AND course_id = ? AND status = 'EXCUSED'";
            int excusedSessions = 0;
            try (PreparedStatement ps = conn.prepareStatement(excusedSql)) {
                ps.setInt(1, studentId);
                ps.setInt(2, courseId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        excusedSessions = rs.getInt(1);
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(attendedSql)) {
                ps.setInt(1, studentId);
                ps.setInt(2, courseId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        attendedSessions = rs.getInt(1);
                    }
                }
            }

            int effectiveTotal = totalSessions - excusedSessions;
            if (effectiveTotal <= 0) return 100.0;
            return ((double) attendedSessions / effectiveTotal) * 100.0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    @Override
    public List<Attendance> findAll() {
        List<Attendance> list = new ArrayList<>();
        String sql = "SELECT a.*, u.name AS student_name, s.roll_number, c.course_code, c.course_name " +
                     "FROM attendance a " +
                     "JOIN students s ON a.student_id = s.student_id " +
                     "JOIN users u ON s.user_id = u.id " +
                     "JOIN courses c ON a.course_id = c.id " +
                     "ORDER BY a.marked_at DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToAttendance(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public boolean save(Attendance attendance) {
        String sql = "INSERT INTO attendance (student_id, course_id, qr_session_id, status) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, attendance.getStudentId());
            ps.setInt(2, attendance.getCourseId());
            ps.setInt(3, attendance.getQrSessionId());
            ps.setString(4, attendance.getStatus() == null ? "PRESENT" : attendance.getStatus());
            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        attendance.setId(keys.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * ACID Transactional Attendance Recording with Scan Attempt Logging.
     * Enforces atomicity: Attendance row and ScanAttempt row are committed together.
     * If a duplicate scan or constraint failure occurs, the transaction rolls back cleanly.
     *
     * @return true if attendance was successfully marked and committed; false if rolled back.
     */
    public boolean recordAttendanceTransaction(int studentId, int courseId, int qrSessionId,
                                              String status, String tokenHash) throws SQLException {
        String insertAttendanceSql = "INSERT INTO attendance (student_id, course_id, qr_session_id, status) VALUES (?, ?, ?, ?)";
        String insertAttemptSql = "INSERT INTO scan_attempts (session_id, student_id, token_hash, outcome) VALUES (?, ?, ?, ?)";

        Connection conn = null;
        try {
            conn = DBConnection.getInstance().getConnection();
            conn.setAutoCommit(false); // Begin ACID Transaction

            // 1. Insert attendance
            try (PreparedStatement psAtt = conn.prepareStatement(insertAttendanceSql, Statement.RETURN_GENERATED_KEYS)) {
                psAtt.setInt(1, studentId);
                psAtt.setInt(2, courseId);
                psAtt.setInt(3, qrSessionId);
                psAtt.setString(4, status != null ? status : "PRESENT");
                psAtt.executeUpdate();
            }

            // 2. Insert scan attempt audit record
            try (PreparedStatement psAtm = conn.prepareStatement(insertAttemptSql)) {
                psAtm.setInt(1, qrSessionId);
                psAtm.setInt(2, studentId);
                psAtm.setString(3, tokenHash);
                psAtm.setString(4, "SUCCESS");
                psAtm.executeUpdate();
            }

            conn.commit(); // Commit Transaction
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback(); // Rollback on any failure (duplicate constraint, etc.)
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            // Check if this was a duplicate scan constraint violation
            if (e.getErrorCode() == 1062 || (e.getMessage() != null && e.getMessage().contains("Duplicate entry"))) {
                // Log duplicate scan attempt in separate transaction
                new ScanAttemptDao().recordAttempt(qrSessionId, studentId, tokenHash, "DUPLICATE");
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Updates attendance status directly (e.g. transitioning from ABSENT to PRESENT when student scans in a reopened session).
     */
    public boolean updateStatus(int attendanceId, String status) {
        String sql = "UPDATE attendance SET status = ?, marked_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, attendanceId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * ACID Transactional Manual Attendance Status Update with Immutable Audit Logging.
     */
    public boolean updateWithAuditTransaction(int attendanceId, String newStatus, String reason, int changedByUserId) throws SQLException {
        String selectSql = "SELECT student_id, qr_session_id, status FROM attendance WHERE id = ?";
        String updateSql = "UPDATE attendance SET status = ? WHERE id = ?";
        String auditSql = "INSERT INTO attendance_audit_log (attendance_id, student_id, session_id, changed_by_user_id, old_status, new_status, reason) " +
                          "VALUES (?, ?, ?, ?, ?, ?, ?)";

        Connection conn = null;
        try {
            conn = DBConnection.getInstance().getConnection();
            conn.setAutoCommit(false); // Begin Transaction

            int studentId = 0;
            int sessionId = 0;
            String oldStatus = null;

            try (PreparedStatement psSelect = conn.prepareStatement(selectSql)) {
                psSelect.setInt(1, attendanceId);
                try (ResultSet rs = psSelect.executeQuery()) {
                    if (rs.next()) {
                        studentId = rs.getInt("student_id");
                        sessionId = rs.getInt("qr_session_id");
                        oldStatus = rs.getString("status");
                    } else {
                        conn.rollback();
                        return false;
                    }
                }
            }

            // Update attendance record
            try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                psUpdate.setString(1, newStatus);
                psUpdate.setInt(2, attendanceId);
                psUpdate.executeUpdate();
            }

            // Insert audit record
            try (PreparedStatement psAudit = conn.prepareStatement(auditSql)) {
                psAudit.setInt(1, attendanceId);
                psAudit.setInt(2, studentId);
                psAudit.setInt(3, sessionId);
                psAudit.setInt(4, changedByUserId);
                psAudit.setString(5, oldStatus);
                psAudit.setString(6, newStatus);
                psAudit.setString(7, reason);
                psAudit.executeUpdate();
            }

            conn.commit(); // Commit Transaction
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * ACID Transactional Manual Attendance Creation with Audit Logging.
     */
    public boolean createWithAuditTransaction(int studentId, int courseId, int sessionId, String status, String reason, int changedByUserId) throws SQLException {
        String insertSql = "INSERT INTO attendance (student_id, course_id, qr_session_id, status) VALUES (?, ?, ?, ?)";
        String auditSql = "INSERT INTO attendance_audit_log (attendance_id, student_id, session_id, changed_by_user_id, old_status, new_status, reason) " +
                          "VALUES (?, ?, ?, ?, ?, ?, ?)";

        Connection conn = null;
        try {
            conn = DBConnection.getInstance().getConnection();
            conn.setAutoCommit(false);

            int attendanceId = 0;
            try (PreparedStatement psInsert = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                psInsert.setInt(1, studentId);
                psInsert.setInt(2, courseId);
                psInsert.setInt(3, sessionId);
                psInsert.setString(4, status);
                psInsert.executeUpdate();
                try (ResultSet rs = psInsert.getGeneratedKeys()) {
                    if (rs.next()) {
                        attendanceId = rs.getInt(1);
                    }
                }
            }

            try (PreparedStatement psAudit = conn.prepareStatement(auditSql)) {
                psAudit.setInt(1, attendanceId);
                psAudit.setInt(2, studentId);
                psAudit.setInt(3, sessionId);
                psAudit.setInt(4, changedByUserId);
                psAudit.setNull(5, Types.VARCHAR);
                psAudit.setString(6, status);
                psAudit.setString(7, reason);
                psAudit.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public boolean update(Attendance attendance) {
        String sql = "UPDATE attendance SET status = ? WHERE id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attendance.getStatus());
            ps.setInt(2, attendance.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean delete(int id) {
        String sql = "DELETE FROM attendance WHERE id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Marks all enrolled students who did not scan/attend the session as ABSENT.
     * Idempotent: uses NOT EXISTS to prevent duplicates.
     */
    public int markUnmarkedStudentsAbsent(int sessionId, int courseId) {
        String sql = "INSERT INTO attendance (student_id, course_id, qr_session_id, status) " +
                     "SELECT e.student_id, ?, ?, 'ABSENT' " +
                     "FROM enrollments e " +
                     "WHERE e.course_id = ? " +
                     "AND NOT EXISTS (" +
                     "    SELECT 1 FROM attendance a " +
                     "    WHERE a.qr_session_id = ? AND a.student_id = e.student_id" +
                     ")";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            ps.setInt(2, sessionId);
            ps.setInt(3, courseId);
            ps.setInt(4, sessionId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private Attendance mapResultSetToAttendance(ResultSet rs) throws SQLException {
        Attendance a = new Attendance();
        a.setId(rs.getInt("id"));
        a.setStudentId(rs.getInt("student_id"));
        a.setStudentName(rs.getString("student_name"));
        a.setRollNumber(rs.getString("roll_number"));
        a.setCourseId(rs.getInt("course_id"));
        a.setCourseCode(rs.getString("course_code"));
        a.setCourseName(rs.getString("course_name"));
        a.setQrSessionId(rs.getInt("qr_session_id"));
        Timestamp ts = rs.getTimestamp("marked_at");
        if (ts != null) {
            a.setMarkedAt(ts.toLocalDateTime());
        }
        a.setStatus(rs.getString("status"));
        return a;
    }
}
