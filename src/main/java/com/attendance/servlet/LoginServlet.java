package com.attendance.servlet;

import com.attendance.dao.StudentDao;
import com.attendance.dao.TeacherDao;
import com.attendance.dao.UserDao;
import com.attendance.model.Student;
import com.attendance.model.Teacher;
import com.attendance.model.User;
import com.attendance.util.PasswordUtil;
import com.attendance.util.SessionUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Optional;

/**
 * Servlet handling user authentication and role-based polymorphic routing.
 */
@WebServlet(name = "LoginServlet", urlPatterns = {"/login", ""})
public class LoginServlet extends HttpServlet {

    private UserDao userDao;
    private TeacherDao teacherDao;
    private StudentDao studentDao;

    @Override
    public void init() throws ServletException {
        this.userDao = new UserDao();
        this.teacherDao = new TeacherDao();
        this.studentDao = new StudentDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User loggedUser = SessionUtil.getLoggedInUser(request);
        if (loggedUser != null) {
            // Polymorphism: getDashboardPath() resolves to subclass implementation
            response.sendRedirect(request.getContextPath() + loggedUser.getDashboardPath());
            return;
        }
        request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String email = request.getParameter("email");
        String password = request.getParameter("password");

        if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            request.setAttribute("errorMessage", "Email and password are required.");
            request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
            return;
        }

        Optional<User> userOpt = userDao.findByEmail(email.trim());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (PasswordUtil.verifyPassword(password.trim(), user.getPassword())) {
                // Safe migration path: Automatically upgrade legacy hash to BCrypt
                if (PasswordUtil.needsUpgrade(user.getPassword())) {
                    String newBcryptHash = PasswordUtil.hashPassword(password.trim());
                    userDao.updatePassword(user.getId(), newBcryptHash);
                    user.setPassword(newBcryptHash);
                }

                Teacher teacher = null;
                Student student = null;

                if ("TEACHER".equalsIgnoreCase(user.getRole())) {
                    teacher = teacherDao.findByUserId(user.getId()).orElse(null);
                } else if ("STUDENT".equalsIgnoreCase(user.getRole())) {
                    student = studentDao.findByUserId(user.getId()).orElse(null);
                }

                SessionUtil.setLoggedInUser(request, user, teacher, student);

                // Polymorphism: dynamic routing based on user type
                response.sendRedirect(request.getContextPath() + user.getDashboardPath());
                return;
            }
        }

        if (UserDao.lastSqlError != null) {
            request.setAttribute("errorMessage", "Database Error: " + UserDao.lastSqlError);
        } else if (!userOpt.isPresent()) {
            request.setAttribute("errorMessage", "No account found for '" + email.trim() + "'. (Ensure database/schema.sql was executed on your database).");
        } else {
            request.setAttribute("errorMessage", "Incorrect password. Please try again.");
        }
        request.setAttribute("enteredEmail", email);
        request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
    }
}
