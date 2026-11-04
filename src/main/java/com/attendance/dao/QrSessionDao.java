package com.attendance.dao;

import com.attendance.model.QrSession;
import com.attendance.util.DBConnection;
import com.attendance.util.QrCodeUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for QrSession (Dynamic QR Code Sessions).
 * Demonstrates:
 * - Implements Dao<QrSession>
 * - Time-based session expiration and token validation
 */
public class QrSessionDao implements Dao<QrSession> {

    @Override
    public Optional<QrSession> findById(int id) {
        String sql = "SELECT qs.*, c.course_code, c.course_name, u.name AS teacher_name " +
                     "FROM qr_sessions qs " +
                     "JOIN courses c ON qs.course_id = c.id " +
                     "JOIN teachers t ON qs.teacher_id = t.teacher_id " +
                     "JOIN users u ON t.user_id = u.id " +
                     "WHERE qs.id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToQrSession(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<QrSession> findByToken(String token) {
        String sql = "SELECT qs.*, c.course_code, c.course_name, u.name AS teacher_name " +
                     "FROM qr_sessions qs " +
                     "JOIN courses c ON qs.course_id = c.id " +
                     "JOIN teachers t ON qs.teacher_id = t.teacher_id " +
                     "JOIN users u ON t.user_id = u.id " +
                     "WHERE qs.session_token = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToQrSession(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<QrSession> findActiveByCourseId(int courseId) {
        String sql = "SELECT qs.*, c.course_code, c.course_name, u.name AS teacher_name " +
                     "FROM qr_sessions qs " +
                     "LEFT JOIN courses c ON qs.course_id = c.id " +
                     "LEFT JOIN teachers t ON qs.teacher_id = t.teacher_id " +
                     "LEFT JOIN users u ON t.user_id = u.id " +
                     "WHERE qs.course_id = ? AND qs.is_active = TRUE AND qs.closed_at IS NULL " +
                     "ORDER BY qs.id DESC LIMIT 1";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToQrSession(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    /**
     * Resolves an active QR session for an enrolled student by matching the 6-digit numeric fallback code.
     */
    public Optional<QrSession> findByNumericFallbackCode(int studentId, String numericCode) {
        String sql = "SELECT qs.*, c.course_code, c.course_name, u.name AS teacher_name " +
                     "FROM qr_sessions qs " +
                     "JOIN courses c ON qs.course_id = c.id " +
                     "JOIN teachers t ON qs.teacher_id = t.teacher_id " +
                     "JOIN users u ON t.user_id = u.id " +
                     "JOIN enrollments e ON qs.course_id = e.course_id " +
                     "WHERE e.student_id = ? AND qs.is_active = TRUE AND qs.closed_at IS NULL " +
                     "ORDER BY qs.id DESC";
        long now = System.currentTimeMillis();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    QrSession s = mapResultSetToQrSession(rs);
                    // Check current, previous, and adjacent rotation windows for network transmission tolerance
                    long stepMs = QrCodeUtil.ROTATION_INTERVAL_SECONDS * 1000L;
                    for (long offset : new long[]{0L, -stepMs, stepMs, -2 * stepMs}) {
                        String code = QrCodeUtil.computeNumericCode(s.getId(), s.getCourseId(), now + offset);
                        if (numericCode.equals(code)) {
                            return Optional.of(s);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public boolean updateActiveToken(int sessionId, String newToken) {
        String sql = "UPDATE qr_sessions SET session_token = ? WHERE id = ? AND is_active = TRUE AND closed_at IS NULL";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newToken);
            ps.setInt(2, sessionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Closes an active QR session and automatically marks all enrolled students
     * who have not marked attendance as ABSENT.
     */
    public boolean closeSession(int sessionId) {
        String selectSql = "SELECT course_id FROM qr_sessions WHERE id = ?";
        String updateSql = "UPDATE qr_sessions SET is_active = FALSE, closed_at = CURRENT_TIMESTAMP WHERE id = ?";

        int courseId = 0;
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            try (PreparedStatement psSelect = conn.prepareStatement(selectSql)) {
                psSelect.setInt(1, sessionId);
                try (ResultSet rs = psSelect.executeQuery()) {
                    if (rs.next()) {
                        courseId = rs.getInt("course_id");
                    } else {
                        return false;
                    }
                }
            }

            try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                psUpdate.setInt(1, sessionId);
                int affected = psUpdate.executeUpdate();
                if (affected > 0) {
                    // Mark unmarked enrolled students as ABSENT
                    if (courseId > 0) {
                        new AttendanceDao().markUnmarkedStudentsAbsent(sessionId, courseId);
                    }
                    return true;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Reopens a closed session, resetting closed_at to NULL, marking is_active = TRUE,
     * and extending expiration by 60 minutes.
     */
    public boolean reopenSession(int sessionId) {
        String sql = "UPDATE qr_sessions SET is_active = TRUE, closed_at = NULL, expires_at = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 60 MINUTE) WHERE id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Auto-closes expired sessions when a lecture meeting exceeds its scheduled end,
     * and reconciles absentee records.
     */
    public int autoCloseExpiredSessions() {
        String selectExpiredSql = "SELECT id FROM qr_sessions WHERE is_active = TRUE AND closed_at IS NULL AND expires_at <= CURRENT_TIMESTAMP";
        int closedCount = 0;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(selectExpiredSql);
             ResultSet rs = ps.executeQuery()) {
            List<Integer> expiredIds = new ArrayList<>();
            while (rs.next()) {
                expiredIds.add(rs.getInt("id"));
            }
            for (int id : expiredIds) {
                if (closeSession(id)) {
                    closedCount++;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return closedCount;
    }

    public void deactivateOlderSessions(int courseId) {
        String sql = "UPDATE qr_sessions SET is_active = FALSE, closed_at = CURRENT_TIMESTAMP WHERE course_id = ? AND is_active = TRUE";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<QrSession> findAll() {
        List<QrSession> sessions = new ArrayList<>();
        String sql = "SELECT qs.*, c.course_code, c.course_name, u.name AS teacher_name " +
                     "FROM qr_sessions qs " +
                     "JOIN courses c ON qs.course_id = c.id " +
                     "JOIN teachers t ON qs.teacher_id = t.teacher_id " +
                     "JOIN users u ON t.user_id = u.id " +
                     "WHERE qs.session_token NOT LIKE 'MERGED_INTO_%' " +
                     "ORDER BY qs.id DESC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                sessions.add(mapResultSetToQrSession(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sessions;
    }

    @Override
    public boolean save(QrSession session) {
        if (session.getSessionToken() == null || session.getSessionToken().trim().isEmpty() || "pending_init".equals(session.getSessionToken())) {
            session.setSessionToken("init_" + System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().replace("-", ""));
        }
        String sql = "INSERT INTO qr_sessions (session_token, course_id, teacher_id, expires_at, is_active) VALUES (?, ?, ?, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 60 MINUTE), ?)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, session.getSessionToken());
            ps.setInt(2, session.getCourseId());
            ps.setInt(3, session.getTeacherId());
            ps.setBoolean(4, session.isActive());
            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        session.setId(keys.getInt(1));
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
    public boolean update(QrSession session) {
        String sql = "UPDATE qr_sessions SET is_active = ? WHERE id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, session.isActive());
            ps.setInt(2, session.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean delete(int id) {
        String sql = "DELETE FROM qr_sessions WHERE id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private QrSession mapResultSetToQrSession(ResultSet rs) throws SQLException {
        QrSession s = new QrSession();
        s.setId(rs.getInt("id"));
        s.setSessionToken(rs.getString("session_token"));
        s.setCourseId(rs.getInt("course_id"));
        s.setCourseCode(rs.getString("course_code"));
        s.setCourseName(rs.getString("course_name"));
        s.setTeacherId(rs.getInt("teacher_id"));
        s.setTeacherName(rs.getString("teacher_name"));
        Timestamp exp = rs.getTimestamp("expires_at");
        if (exp != null) {
            s.setExpiresAt(exp.toLocalDateTime());
        }
        Timestamp crt = rs.getTimestamp("created_at");
        if (crt != null) {
            s.setCreatedAt(crt.toLocalDateTime());
        }
        try {
            Timestamp cls = rs.getTimestamp("closed_at");
            if (cls != null) {
                s.setClosedAt(cls.toLocalDateTime());
            }
        } catch (SQLException ignored) {}
        s.setActive(rs.getBoolean("is_active"));
        return s;
    }
}
