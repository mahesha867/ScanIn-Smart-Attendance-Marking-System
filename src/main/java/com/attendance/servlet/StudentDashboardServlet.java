package com.attendance.servlet;

import com.attendance.dao.AttendanceDao;
import com.attendance.dao.CourseDao;
import com.attendance.dao.CourseScheduleDao;
import com.attendance.dao.StudentDao;
import com.attendance.model.Attendance;
import com.attendance.model.Course;
import com.attendance.model.CourseSchedule;
import com.attendance.model.Student;
import com.attendance.model.User;
import com.attendance.util.SessionUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servlet handling Student Portal:
 * - Official Record profile view
 * - Academics & course enrollment summaries with low attendance warnings (< 75%)
 * - Course registration and drop actions
 * - Upcoming scheduled classes timetable
 * - Full attendance history log
 */
@WebServlet(name = "StudentDashboardServlet", urlPatterns = {
    "/student/dashboard",
    "/student/courses/register",
    "/student/courses/drop"
})
public class StudentDashboardServlet extends HttpServlet {

    private StudentDao studentDao;
    private CourseDao courseDao;
    private AttendanceDao attendanceDao;
    private CourseScheduleDao scheduleDao;

    @Override
    public void init() throws ServletException {
        this.studentDao = new StudentDao();
        this.courseDao = new CourseDao();
        this.attendanceDao = new AttendanceDao();
        this.scheduleDao = new CourseScheduleDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = SessionUtil.getLoggedInUser(request);
        if (user == null || !"STUDENT".equalsIgnoreCase(user.getRole())) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        Student student = SessionUtil.getStudentProfile(request);
        if (student == null) {
            student = studentDao.findByUserId(user.getId()).orElse(null);
        }

        if (student != null) {
            // 1. Academics: Enrolled Courses & Attendance Percentages
            List<Course> enrolledCourses = studentDao.getEnrolledCourses(student.getStudentId());
            Map<Integer, Double> attendanceMap = new HashMap<>();
            boolean hasLowAttendance = false;
            double totalPctSum = 0;

            for (Course c : enrolledCourses) {
                double pct = attendanceDao.getAttendancePercentage(student.getStudentId(), c.getId());
                double roundedPct = Math.round(pct * 10.0) / 10.0;
                attendanceMap.put(c.getId(), roundedPct);
                totalPctSum += roundedPct;
                if (roundedPct < 75.0) {
                    hasLowAttendance = true;
                }
            }

            double overallAttendance = enrolledCourses.isEmpty() ? 100.0 : Math.round((totalPctSum / enrolledCourses.size()) * 10.0) / 10.0;

            // 2. Upcoming Classes
            List<CourseSchedule> upcomingClasses = scheduleDao.findUpcomingClassesForStudent(student.getStudentId());

            // 3. Available Courses for Registration
            List<Course> availableCourses = courseDao.getAvailableCoursesForStudent(student.getStudentId());

            // 4. Attendance History Log
            List<Attendance> history = attendanceDao.findByStudent(student.getStudentId());

            request.setAttribute("student", student);
            request.setAttribute("courses", enrolledCourses);
            request.setAttribute("attendanceMap", attendanceMap);
            request.setAttribute("overallAttendance", overallAttendance);
            request.setAttribute("hasLowAttendance", hasLowAttendance);
            request.setAttribute("upcomingClasses", upcomingClasses);
            request.setAttribute("availableCourses", availableCourses);
            request.setAttribute("history", history);
        }

        request.getRequestDispatcher("/WEB-INF/views/student-dashboard.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = SessionUtil.getLoggedInUser(request);
        if (user == null || !"STUDENT".equalsIgnoreCase(user.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied");
            return;
        }

        Student student = SessionUtil.getStudentProfile(request);
        if (student == null) {
            student = studentDao.findByUserId(user.getId()).orElse(null);
        }

        if (student == null) {
            response.sendRedirect(request.getContextPath() + "/student/dashboard?error=Profile+not+found");
            return;
        }

        String path = request.getServletPath();
        String courseIdStr = request.getParameter("courseId");

        if (courseIdStr == null || courseIdStr.trim().isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/student/dashboard?error=Missing+course+selection");
            return;
        }

        try {
            int courseId = Integer.parseInt(courseIdStr.trim());

            if ("/student/courses/register".equals(path)) {
                // Course Registration
                boolean enrolled = courseDao.enrollStudent(student.getStudentId(), courseId);
                if (enrolled) {
                    response.sendRedirect(request.getContextPath() + "/student/dashboard?msg=registered");
                } else {
                    response.sendRedirect(request.getContextPath() + "/student/dashboard?error=Already+enrolled+or+invalid+course");
                }
            } else if ("/student/courses/drop".equals(path)) {
                // Course Drop
                boolean dropped = courseDao.dropStudent(student.getStudentId(), courseId);
                if (dropped) {
                    response.sendRedirect(request.getContextPath() + "/student/dashboard?msg=dropped");
                } else {
                    response.sendRedirect(request.getContextPath() + "/student/dashboard?error=Could+not+drop+course");
                }
            } else {
                response.sendRedirect(request.getContextPath() + "/student/dashboard");
            }
        } catch (NumberFormatException e) {
            response.sendRedirect(request.getContextPath() + "/student/dashboard?error=Invalid+course+ID");
        }
    }
}
