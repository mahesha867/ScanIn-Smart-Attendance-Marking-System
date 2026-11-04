package com.attendance.servlet;

import com.attendance.dao.AttendanceDao;
import com.attendance.dao.CourseDao;
import com.attendance.dao.CourseScheduleDao;
import com.attendance.dao.StudentDao;
import com.attendance.dao.TeacherDao;
import com.attendance.model.Course;
import com.attendance.model.CourseSchedule;
import com.attendance.model.Student;
import com.attendance.model.Teacher;
import com.attendance.model.User;
import com.attendance.util.SessionUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Time;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Servlet handling Faculty Course & Semester Management:
 * - Course list & details
 * - Course CRUD with strict server-side faculty ownership enforcement
 * - Course roster view with student attendance percentages
 * - Semester timetable / planned sessions CRUD with start/end time validation
 */
@WebServlet(name = "TeacherDashboardServlet", urlPatterns = {
    "/teacher/dashboard",
    "/teacher/courses/create",
    "/teacher/courses/edit",
    "/teacher/courses/delete",
    "/teacher/courses/roster",
    "/teacher/schedules/create",
    "/teacher/schedules/delete"
})
public class TeacherDashboardServlet extends HttpServlet {

    private CourseDao courseDao;
    private TeacherDao teacherDao;
    private StudentDao studentDao;
    private AttendanceDao attendanceDao;
    private CourseScheduleDao scheduleDao;

    @Override
    public void init() throws ServletException {
        this.courseDao = new CourseDao();
        this.teacherDao = new TeacherDao();
        this.studentDao = new StudentDao();
        this.attendanceDao = new AttendanceDao();
        this.scheduleDao = new CourseScheduleDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = SessionUtil.getLoggedInUser(request);
        if (user == null || !"TEACHER".equalsIgnoreCase(user.getRole())) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        Teacher teacher = SessionUtil.getTeacherProfile(request);
        if (teacher == null) {
            teacher = teacherDao.findByUserId(user.getId()).orElse(null);
        }

        if (teacher == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Faculty profile not found.");
            return;
        }

        String path = request.getServletPath();

        // ── Route: View Course Roster ──────────────────────────────
        if ("/teacher/courses/roster".equals(path)) {
            String courseIdStr = request.getParameter("courseId");
            if (courseIdStr == null || courseIdStr.trim().isEmpty()) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Missing+course+id");
                return;
            }

            int courseId;
            try {
                courseId = Integer.parseInt(courseIdStr.trim());
            } catch (NumberFormatException e) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Invalid+course+id");
                return;
            }

            Optional<Course> courseOpt = courseDao.findById(courseId);
            if (!courseOpt.isPresent()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Course not found.");
                return;
            }

            Course course = courseOpt.get();

            // Strict Server-Side Ownership Check: Faculty can only view roster for their own courses
            if (course.getTeacherId() != teacher.getTeacherId()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: You do not have permission to view the roster for this course.");
                return;
            }

            List<Student> enrolledStudents = studentDao.findStudentsByCourse(courseId);
            Map<Integer, Double> studentAttendanceMap = new HashMap<>();
            for (Student s : enrolledStudents) {
                double pct = attendanceDao.getAttendancePercentage(s.getStudentId(), courseId);
                studentAttendanceMap.put(s.getStudentId(), Math.round(pct * 10.0) / 10.0);
            }

            request.setAttribute("course", course);
            request.setAttribute("teacher", teacher);
            request.setAttribute("enrolledStudents", enrolledStudents);
            request.setAttribute("attendanceMap", studentAttendanceMap);
            request.getRequestDispatcher("/WEB-INF/views/course-roster.jsp").forward(request, response);
            return;
        }

        // ── Default Route: Teacher Dashboard ─────────────────────────
        List<Course> courses = courseDao.findByTeacherId(teacher.getTeacherId());
        Map<Integer, List<CourseSchedule>> scheduleMap = new HashMap<>();
        Map<Integer, Integer> enrollmentCountMap = new HashMap<>();

        for (Course c : courses) {
            scheduleMap.put(c.getId(), scheduleDao.findByCourseId(c.getId()));
            enrollmentCountMap.put(c.getId(), studentDao.findStudentsByCourse(c.getId()).size());
        }

        request.setAttribute("courses", courses);
        request.setAttribute("teacher", teacher);
        request.setAttribute("scheduleMap", scheduleMap);
        request.setAttribute("enrollmentCountMap", enrollmentCountMap);

        request.getRequestDispatcher("/WEB-INF/views/teacher-dashboard.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = SessionUtil.getLoggedInUser(request);
        if (user == null || !"TEACHER".equalsIgnoreCase(user.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied");
            return;
        }

        Teacher teacher = SessionUtil.getTeacherProfile(request);
        if (teacher == null) {
            teacher = teacherDao.findByUserId(user.getId()).orElse(null);
        }

        if (teacher == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Faculty profile not found.");
            return;
        }

        String path = request.getServletPath();

        // ── Action 1: Create Course ─────────────────────────────────
        if ("/teacher/courses/create".equals(path)) {
            String courseCode = request.getParameter("courseCode");
            String courseName = request.getParameter("courseName");
            String department = request.getParameter("department");
            String semesterStr = request.getParameter("semester");

            // Server-side validation
            if (courseCode == null || courseCode.trim().length() < 3 || courseCode.trim().length() > 20) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Course+code+must+be+between+3+and+20+characters.");
                return;
            }
            if (courseName == null || courseName.trim().length() < 3 || courseName.trim().length() > 150) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Course+name+must+be+between+3+and+150+characters.");
                return;
            }
            if (department == null || department.trim().isEmpty()) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Department+is+required.");
                return;
            }

            int semester;
            try {
                semester = Integer.parseInt(semesterStr.trim());
                if (semester < 1 || semester > 8) {
                    response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Semester+must+be+between+1+and+8.");
                    return;
                }
            } catch (Exception e) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Invalid+semester.");
                return;
            }

            Course newCourse = new Course();
            newCourse.setCourseCode(courseCode.trim().toUpperCase());
            newCourse.setCourseName(courseName.trim());
            newCourse.setDepartment(department.trim());
            newCourse.setSemester(semester);
            newCourse.setTeacherId(teacher.getTeacherId());

            boolean saved = courseDao.save(newCourse);
            if (saved) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?msg=course_created");
            } else {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Course+code+already+exists+or+database+error.");
            }
            return;
        }

        // ── Action 2: Edit Course (with Ownership Enforcement) ──────
        if ("/teacher/courses/edit".equals(path)) {
            String courseIdStr = request.getParameter("courseId");
            String courseName = request.getParameter("courseName");
            String department = request.getParameter("department");
            String semesterStr = request.getParameter("semester");

            if (courseIdStr == null || courseIdStr.trim().isEmpty()) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Missing+course+id");
                return;
            }

            int courseId;
            try {
                courseId = Integer.parseInt(courseIdStr.trim());
            } catch (NumberFormatException e) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Invalid+course+id");
                return;
            }

            Optional<Course> courseOpt = courseDao.findById(courseId);
            if (!courseOpt.isPresent()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Course not found.");
                return;
            }

            Course course = courseOpt.get();

            // Strict Ownership Verification
            if (course.getTeacherId() != teacher.getTeacherId()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: You cannot modify courses owned by other faculty members.");
                return;
            }

            // Input Validation
            if (courseName != null && courseName.trim().length() >= 3) {
                course.setCourseName(courseName.trim());
            }
            if (department != null && !department.trim().isEmpty()) {
                course.setDepartment(department.trim());
            }
            if (semesterStr != null && !semesterStr.trim().isEmpty()) {
                try {
                    int sem = Integer.parseInt(semesterStr.trim());
                    if (sem >= 1 && sem <= 8) course.setSemester(sem);
                } catch (NumberFormatException ignored) {}
            }

            boolean updated = courseDao.update(course);
            if (updated) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?msg=course_updated");
            } else {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Failed+to+update+course.");
            }
            return;
        }

        // ── Action 3: Delete Course (with Ownership Enforcement) ────
        if ("/teacher/courses/delete".equals(path)) {
            String courseIdStr = request.getParameter("courseId");
            if (courseIdStr == null || courseIdStr.trim().isEmpty()) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Missing+course+id");
                return;
            }

            int courseId;
            try {
                courseId = Integer.parseInt(courseIdStr.trim());
            } catch (NumberFormatException e) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Invalid+course+id");
                return;
            }

            Optional<Course> courseOpt = courseDao.findById(courseId);
            if (!courseOpt.isPresent()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Course not found.");
                return;
            }

            // Strict Ownership Verification
            if (courseOpt.get().getTeacherId() != teacher.getTeacherId()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: You cannot delete courses owned by other faculty members.");
                return;
            }

            boolean deleted = courseDao.delete(courseId);
            if (deleted) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?msg=course_deleted");
            } else {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Failed+to+delete+course.");
            }
            return;
        }

        // ── Action 4: Create Planned Schedule / Timeslot ────────────
        if ("/teacher/schedules/create".equals(path)) {
            String courseIdStr = request.getParameter("courseId");
            String dayOfWeek = request.getParameter("dayOfWeek");
            String startTimeStr = request.getParameter("startTime");
            String endTimeStr = request.getParameter("endTime");
            String room = request.getParameter("room");

            if (courseIdStr == null || startTimeStr == null || endTimeStr == null || dayOfWeek == null) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Missing+schedule+fields.");
                return;
            }

            int courseId;
            try {
                courseId = Integer.parseInt(courseIdStr.trim());
            } catch (NumberFormatException e) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Invalid+course+id.");
                return;
            }

            Optional<Course> courseOpt = courseDao.findById(courseId);
            if (!courseOpt.isPresent()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Course not found.");
                return;
            }

            // Strict Ownership Verification
            if (courseOpt.get().getTeacherId() != teacher.getTeacherId()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: You cannot schedule classes for courses owned by other faculty members.");
                return;
            }

            // Schedule Edge Case Validation: Parse Times & Enforce startTime < endTime
            Time startTime;
            Time endTime;
            try {
                // Support HH:mm or HH:mm:ss
                String sTime = startTimeStr.trim().length() == 5 ? startTimeStr.trim() + ":00" : startTimeStr.trim();
                String eTime = endTimeStr.trim().length() == 5 ? endTimeStr.trim() + ":00" : endTimeStr.trim();
                startTime = Time.valueOf(sTime);
                endTime = Time.valueOf(eTime);
            } catch (IllegalArgumentException e) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Invalid+time+format.+Use+HH:mm.");
                return;
            }

            // CRITICAL EDGE CASE: start time must be strictly earlier than end time
            if (!startTime.before(endTime)) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Schedule+validation+failed:+Start+time+must+be+earlier+than+end+time.");
                return;
            }

            if (room == null || room.trim().isEmpty()) {
                room = "TBD";
            }

            CourseSchedule schedule = new CourseSchedule();
            schedule.setCourseId(courseId);
            schedule.setDayOfWeek(dayOfWeek.trim());
            schedule.setStartTime(startTime);
            schedule.setEndTime(endTime);
            schedule.setRoom(room.trim());

            boolean saved = scheduleDao.save(schedule);
            if (saved) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?msg=schedule_added");
            } else {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Failed+to+save+schedule.");
            }
            return;
        }

        // ── Action 5: Delete Schedule ────────────────────────────────
        if ("/teacher/schedules/delete".equals(path)) {
            String scheduleIdStr = request.getParameter("scheduleId");
            if (scheduleIdStr == null || scheduleIdStr.trim().isEmpty()) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Missing+schedule+id");
                return;
            }

            int scheduleId;
            try {
                scheduleId = Integer.parseInt(scheduleIdStr.trim());
            } catch (NumberFormatException e) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Invalid+schedule+id");
                return;
            }

            CourseSchedule schedule = scheduleDao.findById(scheduleId);
            if (schedule == null) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Schedule+not+found");
                return;
            }

            // Verify course ownership
            Optional<Course> courseOpt = courseDao.findById(schedule.getCourseId());
            if (!courseOpt.isPresent() || courseOpt.get().getTeacherId() != teacher.getTeacherId()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: You cannot delete schedules for courses owned by other faculty members.");
                return;
            }

            boolean deleted = scheduleDao.delete(scheduleId);
            if (deleted) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?msg=schedule_deleted");
            } else {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Failed+to+delete+schedule.");
            }
            return;
        }

        response.sendRedirect(request.getContextPath() + "/teacher/dashboard");
    }
}
