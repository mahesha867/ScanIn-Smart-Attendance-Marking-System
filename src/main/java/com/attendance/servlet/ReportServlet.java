package com.attendance.servlet;

import com.attendance.dao.AttendanceAuditLogDao;
import com.attendance.dao.AttendanceDao;
import com.attendance.dao.CourseDao;
import com.attendance.dao.TeacherDao;
import com.attendance.model.Attendance;
import com.attendance.model.AttendanceAuditLog;
import com.attendance.model.Course;
import com.attendance.model.Teacher;
import com.attendance.model.User;
import com.attendance.util.SessionUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Servlet handling Attendance Reports, Course Analytics, Audit Trail, and CSV Export.
 */
@WebServlet(name = "ReportServlet", urlPatterns = {"/reports"})
public class ReportServlet extends HttpServlet {

    private AttendanceDao attendanceDao;
    private AttendanceAuditLogDao auditLogDao;
    private CourseDao courseDao;
    private TeacherDao teacherDao;

    @Override
    public void init() throws ServletException {
        this.attendanceDao = new AttendanceDao();
        this.auditLogDao = new AttendanceAuditLogDao();
        this.courseDao = new CourseDao();
        this.teacherDao = new TeacherDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = SessionUtil.getLoggedInUser(request);
        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        // Security Check: Restrict faculty reports strictly to TEACHER role
        if (!"TEACHER".equalsIgnoreCase(user.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: Faculty attendance reports are restricted to teachers only.");
            return;
        }

        Teacher teacher = SessionUtil.getTeacherProfile(request);
        if (teacher == null) {
            teacher = teacherDao.findByUserId(user.getId()).orElse(null);
        }

        List<Course> courses = null;
        if (teacher != null) {
            courses = courseDao.findByTeacherId(teacher.getTeacherId());
            request.setAttribute("courses", courses);
        }

        String courseIdParam = request.getParameter("courseId");
        int courseId = -1;
        if (courseIdParam != null && !courseIdParam.trim().isEmpty()) {
            try {
                courseId = Integer.parseInt(courseIdParam.trim());
            } catch (NumberFormatException ignored) {}
        } else if (courses != null && !courses.isEmpty()) {
            courseId = courses.get(0).getId();
        }

        Course selectedCourse = null;
        if (courseId > 0) {
            Optional<Course> courseOpt = courseDao.findById(courseId);
            if (!courseOpt.isPresent()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Course not found.");
                return;
            }

            selectedCourse = courseOpt.get();
            // Enforce teacher course ownership
            if (teacher != null && selectedCourse.getTeacherId() != teacher.getTeacherId()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: You do not own this course.");
                return;
            }

            request.setAttribute("selectedCourse", selectedCourse);
            request.setAttribute("selectedCourseId", courseId);

            List<Attendance> attendanceList = attendanceDao.findByCourse(courseId);
            request.setAttribute("attendanceList", attendanceList);

            List<AttendanceAuditLog> auditLogs = auditLogDao.findByCourse(courseId);
            request.setAttribute("auditLogs", auditLogs);

            // Handle CSV Export
            String format = request.getParameter("format");
            if ("csv".equalsIgnoreCase(format)) {
                exportCsv(response, selectedCourse, attendanceList);
                return;
            }
        }

        request.getRequestDispatcher("/WEB-INF/views/attendance-report.jsp").forward(request, response);
    }

    private void exportCsv(HttpServletResponse response, Course course, List<Attendance> attendanceList) throws IOException {
        String filename = "attendance_report_" + course.getCourseCode().replaceAll("[^a-zA-Z0-9_-]", "_") + ".csv";
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        PrintWriter writer = response.getWriter();
        writer.println("Attendance ID,Course Code,Roll Number,Student Name,Marked At,Status,Session ID");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if (attendanceList != null) {
            for (Attendance a : attendanceList) {
                String markedAtStr = a.getMarkedAt() != null ? a.getMarkedAt().format(dtf) : "N/A";
                writer.println(String.format("%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d",
                        a.getId(),
                        escapeCsv(a.getCourseCode() != null ? a.getCourseCode() : course.getCourseCode()),
                        escapeCsv(a.getRollNumber() != null ? a.getRollNumber() : ""),
                        escapeCsv(a.getStudentName() != null ? a.getStudentName() : ""),
                        escapeCsv(markedAtStr),
                        escapeCsv(a.getStatus() != null ? a.getStatus() : "ABSENT"),
                        a.getQrSessionId()
                ));
            }
        }
        writer.flush();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }
}
