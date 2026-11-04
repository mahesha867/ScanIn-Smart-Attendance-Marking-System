package com.attendance.util;

import com.attendance.model.Student;
import com.attendance.model.Teacher;
import com.attendance.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Utility for HTTP Session operations and authentication verification.
 */
public class SessionUtil {

    public static final String SESSION_USER = "currentUser";
    public static final String SESSION_ROLE = "userRole";
    public static final String SESSION_TEACHER = "teacherProfile";
    public static final String SESSION_STUDENT = "studentProfile";

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

    public static void setLoggedInUser(HttpServletRequest request, User user, Teacher teacher, Student student) {
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_USER, user);
        session.setAttribute(SESSION_ROLE, user.getRole());
        if (teacher != null) {
            session.setAttribute(SESSION_TEACHER, teacher);
        }
        if (student != null) {
            session.setAttribute(SESSION_STUDENT, student);
        }
    }

    public static void invalidateSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
