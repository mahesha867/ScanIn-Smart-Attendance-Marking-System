package com.attendance.dao;

import com.attendance.model.AttendanceAuditLog;
import com.attendance.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Attendance Audit Logs.
 * Handles append-only logging of manual overrides and changes.
 */
public class AttendanceAuditLogDao {

    /**
     * Appends an audit entry within an existing connection/transaction.
     */
    public boolean logAudit(Connection conn, AttendanceAuditLog log) throws SQLException {
        String sql = "INSERT INTO attendance_audit_log (attendance_id, student_id, session_id, changed_by_user_id, old_status, new_status, reason) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (log.getAttendanceId() != null) ps.setInt(1, log.getAttendanceId());
            else ps.setNull(1, Types.INTEGER);

            ps.setInt(2, log.getStudentId());

            if (log.getSessionId() != null) ps.setInt(3, log.getSessionId());
            else ps.setNull(3, Types.INTEGER);

            ps.setInt(4, log.getChangedByUserId());
            ps.setString(5, log.getOldStatus());
            ps.setString(6, log.getNewStatus());
            ps.setString(7, log.getReason());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        log.setId(keys.getInt(1));
                    }
                }
                return true;
            }
            return false;
        }
    }

    /**
     * Appends an audit entry with auto-managed pooled connection.
     */
    public boolean logAudit(AttendanceAuditLog log) {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return logAudit(conn, log);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Finds audit history for a specific course.
     */
    public List<AttendanceAuditLog> findByCourse(int courseId) {
        List<AttendanceAuditLog> list = new ArrayList<>();
        String sql = "SELECT al.*, u.name as changed_by_name, su.name as student_name, s.roll_number, c.course_code " +
                     "FROM attendance_audit_log al " +
                     "JOIN users u ON al.changed_by_user_id = u.id " +
                     "JOIN students s ON al.student_id = s.student_id " +
                     "JOIN users su ON s.user_id = su.id " +
                     "LEFT JOIN qr_sessions qs ON al.session_id = qs.id " +
                     "LEFT JOIN attendance a ON al.attendance_id = a.id " +
                     "LEFT JOIN courses c ON COALESCE(qs.course_id, a.course_id) = c.id " +
                     "WHERE COALESCE(qs.course_id, a.course_id) = ? " +
                     "ORDER BY al.created_at DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AttendanceAuditLog log = mapResultSet(rs);
                    try {
                        log.setRollNumber(rs.getString("roll_number"));
                        log.setCourseCode(rs.getString("course_code"));
                    } catch (SQLException ignored) {}
                    list.add(log);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Finds audit history for a specific session.
     */
    public List<AttendanceAuditLog> findBySession(int sessionId) {
        List<AttendanceAuditLog> list = new ArrayList<>();
        String sql = "SELECT al.*, u.name as changed_by_name, su.name as student_name, s.roll_number " +
                     "FROM attendance_audit_log al " +
                     "JOIN users u ON al.changed_by_user_id = u.id " +
                     "JOIN students s ON al.student_id = s.student_id " +
                     "JOIN users su ON s.user_id = su.id " +
                     "WHERE al.session_id = ? " +
                     "ORDER BY al.created_at DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AttendanceAuditLog log = mapResultSet(rs);
                    try {
                        log.setRollNumber(rs.getString("roll_number"));
                    } catch (SQLException ignored) {}
                    list.add(log);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Finds audit history for a specific student.
     */
    public List<AttendanceAuditLog> findByStudent(int studentId) {
        List<AttendanceAuditLog> list = new ArrayList<>();
        String sql = "SELECT al.*, u.name as changed_by_name, su.name as student_name, s.roll_number " +
                     "FROM attendance_audit_log al " +
                     "JOIN users u ON al.changed_by_user_id = u.id " +
                     "JOIN students s ON al.student_id = s.student_id " +
                     "JOIN users su ON s.user_id = su.id " +
                     "WHERE al.student_id = ? " +
                     "ORDER BY al.created_at DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AttendanceAuditLog log = mapResultSet(rs);
                    try {
                        log.setRollNumber(rs.getString("roll_number"));
                    } catch (SQLException ignored) {}
                    list.add(log);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    private AttendanceAuditLog mapResultSet(ResultSet rs) throws SQLException {
        AttendanceAuditLog log = new AttendanceAuditLog();
        log.setId(rs.getInt("id"));
        int attId = rs.getInt("attendance_id");
        log.setAttendanceId(rs.wasNull() ? null : attId);
        log.setStudentId(rs.getInt("student_id"));
        log.setStudentName(rs.getString("student_name"));
        int sessId = rs.getInt("session_id");
        log.setSessionId(rs.wasNull() ? null : sessId);
        log.setChangedByUserId(rs.getInt("changed_by_user_id"));
        log.setChangedByName(rs.getString("changed_by_name"));
        log.setOldStatus(rs.getString("old_status"));
        log.setNewStatus(rs.getString("new_status"));
        log.setReason(rs.getString("reason"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) log.setCreatedAt(ts.toLocalDateTime());
        return log;
    }
}
