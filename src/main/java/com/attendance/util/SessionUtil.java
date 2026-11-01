package com.attendance.util;

import com.attendance.model.Student;
import com.attendance.model.Teacher;
import com.attendance.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Utility for HTTP Session operations, authentication verification,
 * and session-bound CSRF token lifecycle management.
 *
 * CSRF Strategy:
 * - One UUID token per authenticated HttpSession.
 * - Initialized eagerly at login and lazily on first scanner GET.
 * - Never rotated by polling, page refresh, or unrelated requests.
 * - Validated via constant-time comparison before any attendance mutation.
 * - Accepts token from X-CSRF-TOKEN header (AJAX) OR csrfToken body param (form).
 */
public class SessionUtil {

    public static final String SESSION_USER        = "currentUser";
    public static final String SESSION_ROLE        = "userRole";
    public static final String SESSION_TEACHER     = "teacherProfile";
    public static final String SESSION_STUDENT     = "studentProfile";
    public static final String SESSION_CSRF_TOKEN  = "csrfToken";

    public static boolean isLoggedIn(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute(SESSION_USER) != null;
    }

    public static User getLoggedInUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            return (User) session.getAttribute(SESSION_USER);
        }
        return null;
    }

    public static Teacher getTeacherProfile(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            return (Teacher) session.getAttribute(SESSION_TEACHER);
        }
        return null;
    }

    public static Student getStudentProfile(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            return (Student) session.getAttribute(SESSION_STUDENT);
        }
        return null;
    }

    /**
     * Initializes the user session on successful login.
     * A cryptographically random CSRF token is created eagerly and stored in
     * the session so it is available immediately on any subsequent request.
     */
    public static void setLoggedInUser(HttpServletRequest request, User user, Teacher teacher, Student student) {
        // Defend against Session Fixation: rotate session ID upon privilege elevation
        HttpSession existingSession = request.getSession(false);
        if (existingSession != null) {
            try {
                request.changeSessionId();
            } catch (Exception ignored) {
                // In mock test environments or containers where changeSessionId is unsupported
            }
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_USER, user);
        session.setAttribute(SESSION_ROLE, user.getRole());
        if (teacher != null) {
            session.setAttribute(SESSION_TEACHER, teacher);
        }
        if (student != null) {
            session.setAttribute(SESSION_STUDENT, student);
        }
        // Eager CSRF token generation at login — never rotate an already-present token.
        if (session.getAttribute(SESSION_CSRF_TOKEN) == null) {
            session.setAttribute(SESSION_CSRF_TOKEN, UUID.randomUUID().toString());
        }
    }

    /**
     * Returns the session CSRF token, creating it lazily if absent.
     * Synchronized on the session object to prevent double-initialization
     * under concurrent requests within the same session.
     * Returns {@code null} when no valid session exists (unauthenticated).
     */
    public static String getOrCreateCsrfToken(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        synchronized (session) {
            String token = (String) session.getAttribute(SESSION_CSRF_TOKEN);
            if (token == null || token.trim().isEmpty()) {
                token = UUID.randomUUID().toString();
                session.setAttribute(SESSION_CSRF_TOKEN, token);
            }
            return token;
        }
    }

    /**
     * Validates the CSRF token supplied with an incoming request against the
     * session-bound token.
     *
     * Validation order:
     * 1. X-CSRF-TOKEN HTTP header (preferred for AJAX fetch submissions).
     * 2. csrfToken request parameter (form-encoded fallback).
     *
     * Uses constant-time byte comparison to prevent timing-based guessing.
     *
     * @return {@code true} if and only if a valid, matching, non-empty token
     *         was supplied; {@code false} for missing, blank, or mismatched tokens.
     */
    public static boolean validateCsrfToken(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        String sessionToken = (String) session.getAttribute(SESSION_CSRF_TOKEN);
        if (sessionToken == null || sessionToken.trim().isEmpty()) {
            return false;
        }

        // Prefer the X-CSRF-TOKEN header (set by JavaScript fetch); fall back to body param.
        String reqToken = request.getHeader("X-CSRF-TOKEN");
        if (reqToken == null || reqToken.trim().isEmpty()) {
            reqToken = request.getParameter("csrfToken");
        }
        if (reqToken == null || reqToken.trim().isEmpty()) {
            return false;
        }

        return MessageDigest.isEqual(
            sessionToken.trim().getBytes(StandardCharsets.UTF_8),
            reqToken.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Encodes untrusted strings for safe rendering inside HTML attributes (e.g. value="...").
     * Escapes &, ", ', <, and >.
     *
     * @param value untrusted user/URL input string
     * @return HTML-attribute-encoded string
     */
    public static String escapeHtmlAttribute(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&':  sb.append("&amp;"); break;
                case '"':  sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                case '<':  sb.append("&lt;"); break;
                case '>':  sb.append("&gt;"); break;
                default:   sb.append(c); break;
            }
        }
        return sb.toString();
    }

    public static void invalidateSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
