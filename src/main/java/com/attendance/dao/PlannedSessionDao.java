package com.attendance.dao;

import com.attendance.model.PlannedSession;
import com.attendance.model.QrSession;
import com.attendance.model.SessionRosterItem;
import com.attendance.util.DBConnection;
import com.attendance.util.DateTimeUtil;

import java.sql.*;
import java.time.*;
import java.util.*;

/**
 * Data Access Object for planned academic sessions.
 * Implements strict Indian Standard Time (Asia/Kolkata) schedule interpretation,
 * canonical UTC timestamp persistence, deterministic Clock injection,
 * and fail-safe transactional boundary management.
 */
public class PlannedSessionDao {

    private final Clock clock;
    private final ZoneId scheduleZone;

    public PlannedSessionDao() {
        this(Clock.system(DateTimeUtil.IST_ZONE), DateTimeUtil.IST_ZONE);
    }

    public PlannedSessionDao(Clock clock) {
        this(clock, DateTimeUtil.IST_ZONE);
    }

    public PlannedSessionDao(Clock clock, ZoneId scheduleZone) {
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
        this.scheduleZone = Objects.requireNonNull(scheduleZone, "ZoneId must not be null");
    }

    public Clock getClock() {
        return clock;
    }

    public ZoneId getScheduleZone() {
        return scheduleZone;
    }

    // ────────────────────────────────────────────────────────────────────────
    // 1. Expiry Calculation Logic (Piecewise & Boundary-Exact)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Calculates meeting expiry instant based on the piecewise block rules:
     * - Standalone (block_id == null):
     *     now < sessionEnd  -> sessionEnd
     *     now >= sessionEnd -> REJECT (IllegalStateException)
     * - Consecutive Block (block_id != null):
     *     now < sessionEnd              -> sessionEnd
     *     sessionEnd <= now < blockEnd  -> blockEnd (late activation within block)
     *     now >= blockEnd               -> REJECT (IllegalStateException)
     */
    public Instant calculateMeetingExpiry(PlannedSession session, LocalTime blockEndTime, Instant nowInstant) {
        ZonedDateTime nowZdt = nowInstant.atZone(scheduleZone);
        LocalDate sessionDate = session.getSessionDate();
        ZonedDateTime sessionEndZdt = ZonedDateTime.of(sessionDate, session.getEndTime(), scheduleZone);

        boolean isBlock = session.getBlockId() != null && blockEndTime != null;

        if (!isBlock) {
            // Standalone session
            if (!nowZdt.isBefore(sessionEndZdt)) {
                throw new IllegalStateException("Cannot activate standalone session at or after its scheduled end time (" +
                        session.getEndTime() + "). Current time: " + nowZdt.toLocalTime());
            }
            return sessionEndZdt.toInstant();
        } else {
            // Consecutive block session
            ZonedDateTime blockEndZdt = ZonedDateTime.of(sessionDate, blockEndTime, scheduleZone);
            if (!nowZdt.isBefore(blockEndZdt)) {
                throw new IllegalStateException("Cannot activate session at or after the block end time (" +
                        blockEndTime + "). Current time: " + nowZdt.toLocalTime());
            }
            if (nowZdt.isBefore(sessionEndZdt)) {
                // Activated strictly before session scheduled end
                return sessionEndZdt.toInstant();
            } else {
                // Activated at or after session scheduled end, but strictly before block end
                return blockEndZdt.toInstant();
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 2. Creation & Validation (Single Session & Consecutive Block)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Creates a standalone planned session.
     */
    public int createPlannedSession(PlannedSession session) throws SQLException {
        Objects.requireNonNull(session, "Session must not be null");
        if (!session.getStartTime().isBefore(session.getEndTime())) {
            throw new IllegalArgumentException("Session start time (" + session.getStartTime() +
                    ") must precede end time (" + session.getEndTime() + ")");
        }

        String sql = "INSERT INTO planned_sessions (block_id, course_id, teacher_id, session_type, " +
                     "session_date, start_time, end_time, room, description, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, session.getBlockId());
            ps.setInt(2, session.getCourseId());
            ps.setInt(3, session.getTeacherId());
            ps.setString(4, session.getSessionType());
            ps.setDate(5, java.sql.Date.valueOf(session.getSessionDate()));
            ps.setTime(6, Time.valueOf(session.getStartTime()));
            ps.setTime(7, Time.valueOf(session.getEndTime()));
            ps.setString(8, session.getRoom());
            ps.setString(9, session.getDescription());
            ps.setString(10, session.getStatus() != null ? session.getStatus() : "SCHEDULED");

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    session.setId(rs.getInt(1));
                    return session.getId();
                }
            }
        }
        return -1;
    }

    /**
     * Validates and atomically creates a consecutive block of sessions.
     * Guarantees rollback on all exceptions (checked and unchecked).
     */
    public String validateAndCreateConsecutiveBlock(List<PlannedSession> sessions) throws SQLException {
        if (sessions == null || sessions.size() < 2 || sessions.size() > 4) {
            throw new IllegalArgumentException("A consecutive block must contain between 2 and 4 sessions.");
        }

        // 1. Homogeneity check
        int courseId = sessions.get(0).getCourseId();
        int teacherId = sessions.get(0).getTeacherId();
        LocalDate date = sessions.get(0).getSessionDate();
        String sessionType = sessions.get(0).getSessionType();

        for (PlannedSession s : sessions) {
            if (s.getCourseId() != courseId || s.getTeacherId() != teacherId ||
                !s.getSessionDate().equals(date) || !s.getSessionType().equalsIgnoreCase(sessionType)) {
                throw new IllegalArgumentException("All sessions in a block must have identical course, faculty, date, and session type.");
            }
        }

        // 2. Contiguity and ordering check
        sessions.sort(Comparator.comparing(PlannedSession::getStartTime));
        for (int i = 0; i < sessions.size(); i++) {
            PlannedSession cur = sessions.get(i);
            if (!cur.getStartTime().isBefore(cur.getEndTime())) {
                throw new IllegalArgumentException("Session " + (i + 1) + " start time must precede end time.");
            }
            if (i > 0) {
                PlannedSession prev = sessions.get(i - 1);
                if (!cur.getStartTime().equals(prev.getEndTime())) {
                    throw new IllegalArgumentException("Block sessions must be strictly contiguous without gaps or overlaps.");
                }
            }
        }

        String blockId = UUID.randomUUID().toString();
        String sql = "INSERT INTO planned_sessions (block_id, course_id, teacher_id, session_type, " +
                     "session_date, start_time, end_time, room, description, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SCHEDULED')";

        Connection conn = null;
        boolean previousAutoCommit = true;
        try {
            conn = DBConnection.getInstance().getConnection();
            previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                for (PlannedSession s : sessions) {
                    s.setBlockId(blockId);
                    ps.setString(1, blockId);
                    ps.setInt(2, s.getCourseId());
                    ps.setInt(3, s.getTeacherId());
                    ps.setString(4, s.getSessionType().toUpperCase());
                    ps.setDate(5, java.sql.Date.valueOf(s.getSessionDate()));
                    ps.setTime(6, Time.valueOf(s.getStartTime()));
                    ps.setTime(7, Time.valueOf(s.getEndTime()));
                    ps.setString(8, s.getRoom());
                    ps.setString(9, s.getDescription());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            conn.commit();
            return blockId;
        } catch (Throwable t) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Throwable rbEx) {
                    // Suppress secondary rollback exception to preserve primary cause
                }
            }
            if (t instanceof SQLException) throw (SQLException) t;
            if (t instanceof RuntimeException) throw (RuntimeException) t;
            throw new RuntimeException("Unexpected error during block creation", t);
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(previousAutoCommit); } catch (Throwable ignored) {}
                try { conn.close(); } catch (Throwable ignored) {}
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 3. Activation & Closure Transaction Boundaries
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Atomically activates a planned session and creates the linked QR session.
     *
     * Transaction sequence:
     * 1. BEGIN.
     * 2. SELECT planned_sessions WHERE id=? FOR UPDATE  (row-lock against concurrent activation).
     * 3. Validate current status:
     *    - ATTENDANCE_OPEN + valid active QR → idempotent return of existing qr_session_id.
     *    - ATTENDANCE_OPEN + no active QR (stale) → rollback + IllegalStateException.
     *    - COMPLETED / CANCELLED / MISSED → rollback + IllegalStateException.
     *    - SCHEDULED → proceed.
     * 4. calculateMeetingExpiry() — rejects past-boundary activation before touching DB.
     * 5. INSERT qr_sessions with planned_session_id and explicit expires_at.
     * 6. UPDATE planned_sessions SET status='ATTENDANCE_OPEN'.
     * 7. COMMIT.
     *
     * @param plannedSessionId ID of the planned session to activate.
     * @param teacherId        ID of the requesting teacher (ownership enforced by caller).
     * @param qrSessionDao     DAO used to persist the new QR session row.
     * @return ID of the newly created (or idempotently-found) qr_sessions row.
     * @throws SQLException          on database error (transaction rolled back).
     * @throws IllegalStateException if the planned session cannot be activated.
     */
    public int activatePlannedSession(int plannedSessionId, int teacherId,
                                      com.attendance.dao.QrSessionDao qrSessionDao)
            throws SQLException {

        Connection conn = null;
        boolean previousAutoCommit = true;
        try {
            conn = DBConnection.getInstance().getConnection();
            previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // ── Step 1: Row-lock the planned session record ─────────────────
            String lockSql = "SELECT ps.id, ps.course_id, ps.teacher_id, ps.block_id, " +
                             "ps.session_date, ps.start_time, ps.end_time, ps.status " +
                             "FROM planned_sessions ps WHERE ps.id = ? FOR UPDATE";
            PlannedSession ps;
            try (PreparedStatement stmt = conn.prepareStatement(lockSql)) {
                stmt.setInt(1, plannedSessionId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        throw new IllegalStateException("Planned session not found: id=" + plannedSessionId);
                    }
                    ps = new PlannedSession();
                    ps.setId(rs.getInt("id"));
                    ps.setCourseId(rs.getInt("course_id"));
                    ps.setTeacherId(rs.getInt("teacher_id"));
                    ps.setBlockId(rs.getString("block_id"));
                    java.sql.Date d = rs.getDate("session_date");
                    if (d != null) ps.setSessionDate(d.toLocalDate());
                    java.sql.Time st = rs.getTime("start_time");
                    if (st != null) ps.setStartTime(st.toLocalTime());
                    java.sql.Time et = rs.getTime("end_time");
                    if (et != null) ps.setEndTime(et.toLocalTime());
                    ps.setStatus(rs.getString("status"));
                }
            }

            // ── Step 2: Status gate ─────────────────────────────────────────
            String status = ps.getStatus();

            if ("COMPLETED".equals(status) || "CANCELLED".equals(status) || "MISSED".equals(status)) {
                conn.rollback();
                throw new IllegalStateException(
                    "Cannot activate planned session id=" + plannedSessionId +
                    ": status is already terminal (" + status + ").");
            }

            if ("ATTENDANCE_OPEN".equals(status)) {
                // Idempotent path: check for existing valid active QR association.
                String findActiveSql = "SELECT id FROM qr_sessions " +
                                       "WHERE planned_session_id = ? AND is_active = TRUE AND closed_at IS NULL LIMIT 1";
                try (PreparedStatement findStmt = conn.prepareStatement(findActiveSql)) {
                    findStmt.setInt(1, plannedSessionId);
                    try (ResultSet rs = findStmt.executeQuery()) {
                        if (rs.next()) {
                            int existingId = rs.getInt("id");
                            conn.commit();
                            return existingId; // Idempotent success — return existing QR session id.
                        }
                    }
                }
                // ATTENDANCE_OPEN but no active QR — stale inconsistent state.
                conn.rollback();
                throw new IllegalStateException(
                    "Planned session id=" + plannedSessionId +
                    " is ATTENDANCE_OPEN but has no active QR association. " +
                    "Use the closure path to reconcile, then retry.");
            }

            // ── Step 3: SCHEDULED → compute expiry boundary ─────────────────
            LocalTime blockEndTime = null;
            if (ps.getBlockId() != null) {
                // Resolve block end inside the same transaction (no separate connection needed).
                String blockEndSql = "SELECT MAX(end_time) FROM planned_sessions WHERE block_id = ?";
                try (PreparedStatement blockStmt = conn.prepareStatement(blockEndSql)) {
                    blockStmt.setString(1, ps.getBlockId());
                    try (ResultSet rs = blockStmt.executeQuery()) {
                        if (rs.next()) {
                            java.sql.Time t = rs.getTime(1);
                            if (t != null) blockEndTime = t.toLocalTime();
                        }
                    }
                }
            }

            // calculateMeetingExpiry throws IllegalStateException for past-boundary activations.
            Instant expiryInstant = calculateMeetingExpiry(ps, blockEndTime, clock.instant());
            // Convert Instant (UTC) to java.sql.Timestamp for JDBC persistence.
            java.sql.Timestamp expiresAtUtc = java.sql.Timestamp.from(expiryInstant);

            // Also materialize as LocalDateTime (UTC) for in-memory QrSession object.
            java.time.LocalDateTime expiresAtLdt = java.time.LocalDateTime.ofInstant(expiryInstant, java.time.ZoneOffset.UTC);

            // ── Step 4: Create the QR session row inside this transaction ────
            // Initial placeholder token (overwritten by first polling rotation).
            String placeholderToken = "init_planned_" + plannedSessionId + "_" + System.currentTimeMillis()
                                      + "_" + java.util.UUID.randomUUID().toString().replace("-", "");

            String insertQrSql = "INSERT INTO qr_sessions " +
                                 "(session_token, course_id, planned_session_id, teacher_id, expires_at, is_active) " +
                                 "VALUES (?, ?, ?, ?, ?, TRUE)";
            int newQrSessionId;
            try (PreparedStatement insertStmt = conn.prepareStatement(insertQrSql, Statement.RETURN_GENERATED_KEYS)) {
                insertStmt.setString(1, placeholderToken);
                insertStmt.setInt(2, ps.getCourseId());
                insertStmt.setInt(3, plannedSessionId);
                insertStmt.setInt(4, teacherId);
                insertStmt.setTimestamp(5, expiresAtUtc);
                insertStmt.executeUpdate();
                try (ResultSet keys = insertStmt.getGeneratedKeys()) {
                    if (!keys.next()) {
                        conn.rollback();
                        throw new IllegalStateException("QR session insert did not return a generated key.");
                    }
                    newQrSessionId = keys.getInt(1);
                }
            }

            // ── Step 5: Transition planned session to ATTENDANCE_OPEN ────────
            String updateSql = "UPDATE planned_sessions SET status = 'ATTENDANCE_OPEN' " +
                               "WHERE id = ? AND status = 'SCHEDULED'";
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setInt(1, plannedSessionId);
                int updated = updateStmt.executeUpdate();
                if (updated == 0) {
                    // Another thread raced us — roll back and surface the conflict.
                    conn.rollback();
                    throw new IllegalStateException(
                        "Concurrent activation conflict: planned session id=" + plannedSessionId +
                        " status changed between lock acquisition and update.");
                }
            }

            conn.commit();
            return newQrSessionId;

        } catch (Throwable t) {
            if (conn != null) {
                try { conn.rollback(); } catch (Throwable ignored) {}
            }
            if (t instanceof SQLException) throw (SQLException) t;
            if (t instanceof IllegalStateException) throw (IllegalStateException) t;
            throw new RuntimeException("Unexpected error during planned session activation", t);
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(previousAutoCommit); } catch (Throwable ignored) {}
                try { conn.close(); } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * Closes an active QR meeting and completes the associated planned session atomically.
     * Enforces exact association between planned_session, qr_session, and course inside the transaction.
     */
    public boolean closeMeetingAndCompleteSession(int plannedSessionId, int qrSessionId, int courseId) throws SQLException {
        Connection conn = null;
        boolean previousAutoCommit = true;
        try {
            conn = DBConnection.getInstance().getConnection();
            previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // 1. Validate association inside transaction with row lock
            String verifySql = "SELECT qs.id, qs.course_id, qs.planned_session_id, qs.is_active, ps.status " +
                               "FROM qr_sessions qs " +
                               "JOIN planned_sessions ps ON qs.planned_session_id = ps.id " +
                               "WHERE qs.id = ? AND qs.planned_session_id = ? AND qs.course_id = ? " +
                               "FOR UPDATE";

            try (PreparedStatement psVerify = conn.prepareStatement(verifySql)) {
                psVerify.setInt(1, qrSessionId);
                psVerify.setInt(2, plannedSessionId);
                psVerify.setInt(3, courseId);
                try (ResultSet rs = psVerify.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        throw new IllegalArgumentException("Association mismatch: qr_session " + qrSessionId +
                                " does not link to planned_session " + plannedSessionId + " and course " + courseId);
                    }
                    boolean isActive = rs.getBoolean("is_active");
                    String plannedStatus = rs.getString("status");
                    if (!isActive && "COMPLETED".equals(plannedStatus)) {
                        // Already closed idempotently
                        conn.commit();
                        return true;
                    }

                    // Stage 13-C4.2.2: QR active-state consistency matrix.
                    // Normal attendance closure strictly requires BOTH an active QR session AND ATTENDANCE_OPEN planned status.
                    // An inactive QR session with ATTENDANCE_OPEN, an active QR session with COMPLETED,
                    // an unactivated SCHEDULED session, or any null/unexpected state must reject and roll back.
                    if (!isActive || !"ATTENDANCE_OPEN".equals(plannedStatus)) {
                        conn.rollback();
                        throw new IllegalStateException("Cannot close planned session #" + plannedSessionId +
                                " with QR active=" + isActive + " and status=" + plannedStatus +
                                " (must be active and ATTENDANCE_OPEN)");
                    }
                }
            }

            // 2. Deactivate QR session
            String closeQrSql = "UPDATE qr_sessions SET is_active = FALSE, closed_at = UTC_TIMESTAMP() " +
                                "WHERE id = ? AND is_active = TRUE";
            try (PreparedStatement psQr = conn.prepareStatement(closeQrSql)) {
                psQr.setInt(1, qrSessionId);
                psQr.executeUpdate();
            }

            // 3. Reconcile absentees (only for actively enrolled students who did not record attendance)
            String absentSql = "INSERT INTO attendance (student_id, course_id, qr_session_id, status) " +
                               "SELECT e.student_id, ?, ?, 'ABSENT' " +
                               "FROM enrollments e " +
                               "WHERE e.course_id = ? " +
                               "  AND (e.status = 'ACTIVE' OR e.status IS NULL) " +
                               "  AND NOT EXISTS ( " +
                               "      SELECT 1 FROM attendance a " +
                               "      WHERE a.qr_session_id = ? AND a.student_id = e.student_id" +
                               "  )";
            try (PreparedStatement psAbsent = conn.prepareStatement(absentSql)) {
                psAbsent.setInt(1, courseId);
                psAbsent.setInt(2, qrSessionId);
                psAbsent.setInt(3, courseId);
                psAbsent.setInt(4, qrSessionId);
                psAbsent.executeUpdate();
            }

            // 4. Complete planned session (strictly requires ATTENDANCE_OPEN status)
            String completePlannedSql = "UPDATE planned_sessions SET status = 'COMPLETED' " +
                                        "WHERE id = ? AND status = 'ATTENDANCE_OPEN'";
            try (PreparedStatement psPlanned = conn.prepareStatement(completePlannedSql)) {
                psPlanned.setInt(1, plannedSessionId);
                int updated = psPlanned.executeUpdate();
                if (updated == 0) {
                    conn.rollback();
                    return false;
                }
            }

            conn.commit();
            return true;
        } catch (Throwable t) {
            if (conn != null) {
                try { conn.rollback(); } catch (Throwable ignored) {}
            }
            if (t instanceof SQLException) throw (SQLException) t;
            if (t instanceof RuntimeException) throw (RuntimeException) t;
            throw new RuntimeException("Error closing meeting and completing session", t);
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(previousAutoCommit); } catch (Throwable ignored) {}
                try { conn.close(); } catch (Throwable ignored) {}
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 4. Queries & Lookups
    // ────────────────────────────────────────────────────────────────────────

    public Optional<PlannedSession> findById(int id) throws SQLException {
        String sql = "SELECT * FROM planned_sessions WHERE id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        }
        return Optional.empty();
    }

    public int countByCourseId(int courseId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM planned_sessions WHERE course_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    public List<PlannedSession> findByBlockId(String blockId) throws SQLException {
        List<PlannedSession> list = new ArrayList<>();
        if (blockId == null) return list;
        String sql = "SELECT * FROM planned_sessions WHERE block_id = ? ORDER BY start_time ASC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, blockId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    public LocalTime getBlockEndTime(String blockId) throws SQLException {
        if (blockId == null) return null;
        String sql = "SELECT MAX(end_time) FROM planned_sessions WHERE block_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, blockId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Time t = rs.getTime(1);
                    return t != null ? t.toLocalTime() : null;
                }
            }
        }
        return null;
    }

    private PlannedSession mapResultSet(ResultSet rs) throws SQLException {
        PlannedSession s = new PlannedSession();
        s.setId(rs.getInt("id"));
        s.setBlockId(rs.getString("block_id"));
        s.setCourseId(rs.getInt("course_id"));
        s.setTeacherId(rs.getInt("teacher_id"));
        s.setSessionType(rs.getString("session_type"));
        java.sql.Date d = rs.getDate("session_date");
        if (d != null) s.setSessionDate(d.toLocalDate());
        Time st = rs.getTime("start_time");
        if (st != null) s.setStartTime(st.toLocalTime());
        Time et = rs.getTime("end_time");
        if (et != null) s.setEndTime(et.toLocalTime());
        s.setRoom(rs.getString("room"));
        s.setDescription(rs.getString("description"));
        s.setStatus(rs.getString("status"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) s.setCreatedAt(ca.toLocalDateTime());
        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) s.setUpdatedAt(ua.toLocalDateTime());
        // Enrich with course and teacher display names when present in the ResultSet.
        try { s.setCourseCode(rs.getString("course_code")); } catch (SQLException ignored) {}
        try { s.setCourseName(rs.getString("course_name")); } catch (SQLException ignored) {}
        try { s.setTeacherName(rs.getString("teacher_name")); } catch (SQLException ignored) {}
        // Enrich with student attendance record when present in the ResultSet (Phase 13-C3, C3.2)
        try { s.setAttendanceStatus(rs.getString("attendance_status")); } catch (SQLException ignored) {}
        try {
            Timestamp ma = rs.getTimestamp("attendance_marked_at");
            if (ma != null) {
                // Canonical Instant mapping: ma.toInstant().atZone(ZoneOffset.UTC)
                s.setAttendanceMarkedAtInstant(ma.toInstant());
            }
        } catch (SQLException ignored) {}
        // Enrich with QR session binding when present in the ResultSet (Phase 13-C4.6)
        try {
            int qrVal = rs.getInt("qr_session_id");
            s.setQrSessionId(rs.wasNull() ? null : qrVal);
        } catch (SQLException ignored) {}
        try {
            Object qrAct = rs.getObject("qr_is_active");
            s.setQrIsActive(qrAct != null ? rs.getBoolean("qr_is_active") : null);
        } catch (SQLException ignored) {}
        return s;
    }

    // ────────────────────────────────────────────────────────────────────────
    // 5. Teacher and Student Listing Queries
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Returns all planned sessions belonging to the courses taught by the given
     * teacher, ordered by session_date DESC, start_time ASC.
     * Includes course and teacher display names for direct UI rendering.
     */
    public List<PlannedSession> findByTeacherId(int teacherId) throws SQLException {
        List<PlannedSession> list = new ArrayList<>();
        String sql = "SELECT ps.*, c.course_code, c.course_name, u.name AS teacher_name " +
                     "FROM planned_sessions ps " +
                     "JOIN courses c ON ps.course_id = c.id " +
                     "JOIN teachers t ON ps.teacher_id = t.teacher_id " +
                     "JOIN users u ON t.user_id = u.id " +
                     "WHERE ps.teacher_id = ? " +
                     "ORDER BY ps.session_date DESC, ps.start_time ASC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teacherId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    /**
     * Returns all planned sessions for courses in which the given student is
     * enrolled, ordered by session_date DESC, start_time ASC.
     * Includes course and teacher display names for direct UI rendering.
     */
    public List<PlannedSession> findByStudentEnrollment(int studentId) throws SQLException {
        List<PlannedSession> list = new ArrayList<>();
        String sql = "SELECT ps.*, c.course_code, c.course_name, u.name AS teacher_name " +
                     "FROM planned_sessions ps " +
                     "JOIN courses c ON ps.course_id = c.id " +
                     "JOIN teachers t ON ps.teacher_id = t.teacher_id " +
                     "JOIN users u ON t.user_id = u.id " +
                     "JOIN enrollments e ON ps.course_id = e.course_id " +
                     "WHERE e.student_id = ? AND (e.status = 'ACTIVE' OR e.status IS NULL) " +
                     "ORDER BY ps.session_date DESC, ps.start_time ASC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    /**
     * Returns all planned sessions for courses in which the given student is
     * enrolled, enriched with the student's personal attendance status (PRESENT, LATE, ABSENT, or null).
     * Ordered by session_date DESC, start_time ASC.
     * Guaranteed read-only with zero attendance mutations.
     */
    public List<PlannedSession> findByStudentEnrollmentWithAttendance(int studentId) throws SQLException {
        List<PlannedSession> list = new ArrayList<>();
        String sql = "SELECT ps.*, c.course_code, c.course_name, u.name AS teacher_name, " +
                     "qs.id AS qr_session_id, qs.is_active AS qr_is_active, " +
                     "a.status AS attendance_status, a.marked_at AS attendance_marked_at " +
                     "FROM planned_sessions ps " +
                     "JOIN courses c ON ps.course_id = c.id " +
                     "JOIN teachers t ON ps.teacher_id = t.teacher_id " +
                     "JOIN users u ON t.user_id = u.id " +
                     "JOIN enrollments e ON ps.course_id = e.course_id " +
                     "LEFT JOIN qr_sessions qs ON qs.planned_session_id = ps.id " +
                     "LEFT JOIN attendance a ON a.qr_session_id = qs.id AND a.student_id = ? " +
                     "WHERE e.student_id = ? AND ((e.status = 'ACTIVE' OR e.status IS NULL) OR a.status IS NOT NULL) " +
                     "ORDER BY ps.session_date DESC, ps.start_time ASC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    /**
     * Returns all planned sessions for a specific course, ordered by
     * session_date DESC, start_time ASC.
     */
    public List<PlannedSession> findByCourseId(int courseId) throws SQLException {
        List<PlannedSession> list = new ArrayList<>();
        String sql = "SELECT ps.*, c.course_code, c.course_name, u.name AS teacher_name " +
                     "FROM planned_sessions ps " +
                     "JOIN courses c ON ps.course_id = c.id " +
                     "JOIN teachers t ON ps.teacher_id = t.teacher_id " +
                     "JOIN users u ON t.user_id = u.id " +
                     "WHERE ps.course_id = ? " +
                     "ORDER BY ps.session_date DESC, ps.start_time ASC";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    /**
     * Retrieves the attendance roster for a specific planned academic session owned by the given teacher.
     * Enforces:
     * - Only students enrolled in the planned session's course are returned.
     * - Parameterized filtering by plannedSessionId and teacherId.
     * - Safe LEFT JOINs for QR session and attendance records (unmarked students remain visible).
     * - Deterministic presentation status mapping using SessionRosterItem.determinePresentationStatus().
     * - UTC-to-IST instant timestamp conversion.
     * - Strictly read-only execution (zero database mutations).
     * - Never retrieves student email or unnecessary personal details.
     */
    public List<SessionRosterItem> findRosterByPlannedSessionId(int plannedSessionId, int teacherId) throws SQLException {
        List<SessionRosterItem> roster = new ArrayList<>();
        String sql = "SELECT " +
                     "    s.student_id, " +
                     "    s.roll_number, " +
                     "    u.name AS student_name, " +
                     "    ps.status AS planned_session_status, " +
                     "    qs.id AS qr_session_id, " +
                     "    qs.is_active AS qr_is_active, " +
                     "    a.status AS attendance_status, " +
                     "    a.marked_at AS attendance_marked_at " +
                     "FROM enrollments e " +
                     "JOIN students s ON e.student_id = s.student_id " +
                     "JOIN users u ON s.user_id = u.id " +
                     "JOIN planned_sessions ps ON ps.course_id = e.course_id " +
                     "LEFT JOIN qr_sessions qs ON qs.planned_session_id = ps.id " +
                     "LEFT JOIN attendance a ON a.qr_session_id = qs.id AND a.student_id = s.student_id " +
                     "WHERE ps.id = ? AND ps.teacher_id = ? " +
                     "AND ( " +
                     "    a.status IS NOT NULL " +
                     "    OR ( " +
                     "        (ps.status != 'COMPLETED' AND (e.status = 'ACTIVE' OR e.status IS NULL)) " +
                     "        OR (ps.status = 'COMPLETED' " +
                     "            AND (e.enrolled_at IS NULL OR qs.closed_at IS NULL OR e.enrolled_at <= qs.closed_at) " +
                     "            AND (e.status = 'ACTIVE' OR e.status IS NULL OR e.dropped_at IS NULL OR qs.closed_at IS NULL OR e.dropped_at > qs.closed_at) " +
                     "        ) " +
                     "    ) " +
                     ") " +
                     "ORDER BY s.roll_number ASC";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, plannedSessionId);
            ps.setInt(2, teacherId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SessionRosterItem item = new SessionRosterItem();
                    item.setStudentId(rs.getInt("student_id"));
                    item.setRollNumber(rs.getString("roll_number"));
                    item.setStudentName(rs.getString("student_name"));

                    String plannedStatus = rs.getString("planned_session_status");
                    int qrIdVal = rs.getInt("qr_session_id");
                    Integer qrSessionId = rs.wasNull() ? null : qrIdVal;

                    Object qrActiveObj = rs.getObject("qr_is_active");
                    Boolean qrIsActive = (qrActiveObj != null) ? rs.getBoolean("qr_is_active") : null;
                    item.setQrIsActive(qrIsActive);

                    String attendanceStatus = rs.getString("attendance_status");
                    item.setAttendanceStatus(attendanceStatus);

                    Timestamp ma = rs.getTimestamp("attendance_marked_at");
                    if (ma != null) {
                        item.setMarkedAtInstant(ma.toInstant()); // Preserves ma.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime()
                    }

                    String presentationStatus = SessionRosterItem.determinePresentationStatus(
                            plannedStatus, qrSessionId, qrIsActive, attendanceStatus);
                    item.setPresentationStatus(presentationStatus);
                    item.setInconsistent("DATA INCONSISTENCY".equals(presentationStatus));

                    roster.add(item);
                }
            }
        }
        return roster;
    }
}
