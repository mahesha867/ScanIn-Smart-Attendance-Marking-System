package com.attendance.listener;

import com.attendance.util.DBConnection;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.sql.Connection;
import java.sql.Statement;
import java.util.TimeZone;

/**
 * Ensures a consistent UTC timezone strategy, dynamic cookie security configuration,
 * configurable session timeout, and startup schema self-healing migrations.
 */
@WebListener
public class AppContextListener implements ServletContextListener, HttpSessionListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        System.out.println("[AppContextListener] Application default timezone configured to UTC.");

        // Configure session cookie security: HttpOnly always, Secure for HTTPS staging/production
        try {
            jakarta.servlet.SessionCookieConfig cookieConfig = sce.getServletContext().getSessionCookieConfig();
            cookieConfig.setHttpOnly(true);
            String cookieSecureEnv = System.getenv("COOKIE_SECURE");
            if (cookieSecureEnv == null || cookieSecureEnv.trim().isEmpty()) {
                cookieSecureEnv = System.getenv("SESSION_COOKIE_SECURE");
            }
            if ("true".equalsIgnoreCase(cookieSecureEnv)) {
                cookieConfig.setSecure(true);
                System.out.println("[AppContextListener] Session cookie secure flag enabled (HTTPS deployment).");
            } else {
                cookieConfig.setSecure(false);
                System.out.println("[AppContextListener] Session cookie secure flag disabled (Local HTTP demonstration).");
            }
        } catch (Exception e) {
            System.err.println("[AppContextListener] SessionCookieConfig notice: " + e.getMessage());
        }

        // Execute self-healing database migrations on startup
        runStartupMigrations();
    }

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        int timeoutMinutes = 30; // Default 30 min (replaces legacy 60 min)
        String envTimeout = System.getenv("SESSION_TIMEOUT_MINUTES");
        if (envTimeout != null && !envTimeout.trim().isEmpty()) {
            try {
                int parsed = Integer.parseInt(envTimeout.trim());
                if (parsed > 0) {
                    timeoutMinutes = parsed;
                }
            } catch (NumberFormatException ignored) {}
        }
        se.getSession().setMaxInactiveInterval(timeoutMinutes * 60);
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
    }

    private void runStartupMigrations() {
        System.out.println("[AppContextListener] Running startup schema self-healing migrations...");
        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Expand session_token to VARCHAR(255) to accommodate rotating HMAC-SHA256 signatures
            try {
                stmt.executeUpdate("ALTER TABLE qr_sessions MODIFY COLUMN session_token VARCHAR(255) NOT NULL");
                System.out.println("[AppContextListener] Migrated qr_sessions.session_token to VARCHAR(255).");
            } catch (Exception e) {
                System.out.println("[AppContextListener] qr_sessions.session_token migration notice: " + e.getMessage());
            }

            // 1b. Ensure closed_at column exists in qr_sessions
            try {
                stmt.executeUpdate("ALTER TABLE qr_sessions ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP NULL DEFAULT NULL");
                System.out.println("[AppContextListener] Ensured qr_sessions.closed_at column exists.");
            } catch (Exception e1) {
                try {
                    stmt.executeUpdate("ALTER TABLE qr_sessions ADD COLUMN closed_at TIMESTAMP NULL DEFAULT NULL");
                    System.out.println("[AppContextListener] Added qr_sessions.closed_at column.");
                } catch (Exception e2) {
                    System.out.println("[AppContextListener] qr_sessions.closed_at column notice: " + e2.getMessage());
                }
            }

            // 2. Ensure scan_attempts table exists
            try {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS scan_attempts (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "session_id INT NULL, " +
                        "student_id INT NULL, " +
                        "token_hash VARCHAR(64) NOT NULL, " +
                        "outcome VARCHAR(50) NOT NULL, " +
                        "attempted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        "INDEX idx_scan_token_hash (token_hash), " +
                        "INDEX idx_scan_student_session (student_id, session_id))");
                System.out.println("[AppContextListener] Ensured scan_attempts table exists.");
            } catch (Exception e) {
                System.out.println("[AppContextListener] scan_attempts table notice: " + e.getMessage());
            }

            // 2b. Ensure attendance_audit_log table exists
            try {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS attendance_audit_log (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "attendance_id INT NULL, " +
                        "student_id INT NOT NULL, " +
                        "session_id INT NULL, " +
                        "changed_by_user_id INT NOT NULL, " +
                        "old_status VARCHAR(20), " +
                        "new_status VARCHAR(20) NOT NULL, " +
                        "reason TEXT NOT NULL, " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
                System.out.println("[AppContextListener] Ensured attendance_audit_log table exists.");
            } catch (Exception e) {
                System.out.println("[AppContextListener] attendance_audit_log table notice: " + e.getMessage());
            }

            // 2c. Ensure attendance status column supports OD and EXCUSED
            try {
                stmt.executeUpdate("ALTER TABLE attendance MODIFY COLUMN status ENUM('PRESENT','ABSENT','LATE','OD','EXCUSED') DEFAULT 'PRESENT'");
            } catch (Exception ignored) {}

            // 2d. Ensure enrollments status and dropped_at columns exist for historical enrollment retention
            try {
                stmt.executeUpdate("ALTER TABLE enrollments ADD COLUMN status ENUM('ACTIVE','DROPPED') NOT NULL DEFAULT 'ACTIVE'");
                System.out.println("[AppContextListener] Ensured enrollments.status column exists.");
            } catch (Exception ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE enrollments ADD COLUMN dropped_at TIMESTAMP NULL DEFAULT NULL");
                System.out.println("[AppContextListener] Ensured enrollments.dropped_at column exists.");
            } catch (Exception ignored) {}

            // 3. Clean up any corrupted legacy 'pending_init' sessions so they never block new sessions
            try {
                stmt.executeUpdate("UPDATE qr_sessions SET session_token = CONCAT('cleaned_', id, '_', UNIX_TIMESTAMP()), is_active = FALSE, closed_at = UTC_TIMESTAMP() WHERE session_token = 'pending_init' OR session_token LIKE 'pending_init%'");
                System.out.println("[AppContextListener] Cleaned up legacy uninitialized sessions.");
            } catch (Exception e) {
                System.out.println("[AppContextListener] Session cleanup notice: " + e.getMessage());
            }

        } catch (Exception e) {
            System.err.println("[AppContextListener] Startup migration notice: " + e.getMessage());
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Cleanup if necessary
    }
}

