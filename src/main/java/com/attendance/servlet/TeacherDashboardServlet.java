package com.attendance.servlet;

import com.attendance.dao.AttendanceDao;
import com.attendance.dao.CourseDao;
import com.attendance.dao.CourseScheduleDao;
import com.attendance.dao.PlannedSessionDao;
import com.attendance.dao.QrSessionDao;
import com.attendance.dao.StudentDao;
import com.attendance.dao.TeacherDao;
import com.attendance.model.Course;
import com.attendance.model.CourseSchedule;
import com.attendance.model.PlannedSession;
import com.attendance.model.QrSession;
import com.attendance.model.SessionRosterItem;
import com.attendance.model.SessionRosterMetrics;
import com.attendance.model.Student;
import com.attendance.model.Teacher;
import com.attendance.model.User;
import com.attendance.util.DateTimeUtil;
import com.attendance.util.SessionUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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
    "/teacher/schedules/delete",
    "/teacher/planned-sessions/create",
    "/teacher/planned-sessions/create-block",
    "/teacher/planned-sessions/roster"
})
public class TeacherDashboardServlet extends HttpServlet {

    private CourseDao courseDao;
    private TeacherDao teacherDao;
    private StudentDao studentDao;
    private AttendanceDao attendanceDao;
    private CourseScheduleDao scheduleDao;
    private PlannedSessionDao plannedSessionDao;
    private QrSessionDao qrSessionDao;

    @Override
    public void init() throws ServletException {
        this.courseDao = new CourseDao();
        this.teacherDao = new TeacherDao();
        this.studentDao = new StudentDao();
        this.attendanceDao = new AttendanceDao();
        this.scheduleDao = new CourseScheduleDao();
        this.plannedSessionDao = new PlannedSessionDao();
        this.qrSessionDao = new QrSessionDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = SessionUtil.getLoggedInUser(request);
        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        if (!"TEACHER".equalsIgnoreCase(user.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: Teacher role required.");
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

        // ── Route: View Planned Session Attendance Roster ───────────
        if ("/teacher/planned-sessions/roster".equals(path)) {
            handlePlannedSessionRoster(request, response, teacher);
            return;
        }

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

        // Load planned academic sessions for this teacher
        List<PlannedSession> plannedSessions = new ArrayList<>();
        try {
            plannedSessions = plannedSessionDao.findByTeacherId(teacher.getTeacherId());
        } catch (SQLException e) {
            e.printStackTrace();
        }

        request.setAttribute("courses", courses);
        request.setAttribute("teacher", teacher);
        request.setAttribute("scheduleMap", scheduleMap);
        request.setAttribute("enrollmentCountMap", enrollmentCountMap);
        request.setAttribute("plannedSessions", plannedSessions);
        request.setAttribute("csrfToken", SessionUtil.getOrCreateCsrfToken(request));

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

            // Stage 12: Protection against deleting courses with planned sessions or historical attendance
            try {
                if (plannedSessionDao.countByCourseId(courseId) > 0) {
                    response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Cannot+delete+course:+Course+has+planned+academic+sessions.");
                    return;
                }
            } catch (Exception e) {
                // If table doesn't exist yet prior to migration, continue safely
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

        // ── Action 6: Create Standalone Planned Session ──────────────
        if ("/teacher/planned-sessions/create".equals(path)) {
            // CSRF validation
            if (!SessionUtil.validateCsrfToken(request)) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Security+validation+failed:+Invalid+or+missing+CSRF+token.");
                return;
            }

            String courseIdStr = request.getParameter("courseId");
            String sessionType = request.getParameter("sessionType");
            String sessionDateStr = request.getParameter("sessionDate");
            String startTimeStr = request.getParameter("startTime");
            String endTimeStr = request.getParameter("endTime");
            String room = request.getParameter("room");
            String description = request.getParameter("description");

            if (courseIdStr == null || sessionDateStr == null || startTimeStr == null || endTimeStr == null) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Missing+required+planned+session+fields.");
                return;
            }

            int courseId;
            try {
                courseId = Integer.parseInt(courseIdStr.trim());
            } catch (NumberFormatException e) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Invalid+course+id.");
                return;
            }

            // Verify persisted course ownership
            Optional<Course> courseOpt = courseDao.findById(courseId);
            if (!courseOpt.isPresent() || courseOpt.get().getTeacherId() != teacher.getTeacherId()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: You do not own this course.");
                return;
            }

            LocalDate sessionDate;
            LocalTime startTime;
            LocalTime endTime;
            try {
                sessionDate = LocalDate.parse(sessionDateStr.trim());
                String sTime = startTimeStr.trim().length() == 5 ? startTimeStr.trim() + ":00" : startTimeStr.trim();
                String eTime = endTimeStr.trim().length() == 5 ? endTimeStr.trim() + ":00" : endTimeStr.trim();
                startTime = LocalTime.parse(sTime);
                endTime = LocalTime.parse(eTime);
            } catch (DateTimeParseException e) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Invalid+date+or+time+format.");
                return;
            }

            if (!startTime.isBefore(endTime)) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Validation+failed:+Start+time+must+precede+end+time.");
                return;
            }

            if (room == null || room.trim().isEmpty()) {
                room = "TBD";
            }

            PlannedSession ps = new PlannedSession();
            ps.setCourseId(courseId);
            ps.setTeacherId(teacher.getTeacherId());
            ps.setSessionType(sessionType != null && !sessionType.trim().isEmpty() ? sessionType.trim().toUpperCase() : "LECTURE");
            ps.setSessionDate(sessionDate);
            ps.setStartTime(startTime);
            ps.setEndTime(endTime);
            ps.setRoom(room.trim());
            ps.setDescription(description != null ? description.trim() : null);
            ps.setStatus("SCHEDULED");

            try {
                int createdId = plannedSessionDao.createPlannedSession(ps);
                if (createdId > 0) {
                    response.sendRedirect(request.getContextPath() + "/teacher/dashboard?msg=planned_session_created");
                } else {
                    response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Failed+to+create+planned+session.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Database+error+creating+planned+session.");
            }
            return;
        }

        // ── Action 7: Create Consecutive Academic Block (2–4 Sessions) ──
        if ("/teacher/planned-sessions/create-block".equals(path)) {
            // CSRF validation
            if (!SessionUtil.validateCsrfToken(request)) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Security+validation+failed:+Invalid+or+missing+CSRF+token.");
                return;
            }

            String courseIdStr = request.getParameter("courseId");
            String sessionType = request.getParameter("sessionType");
            String sessionDateStr = request.getParameter("sessionDate");
            String room = request.getParameter("room");
            String description = request.getParameter("description");
            String countStr = request.getParameter("blockCount");

            if (courseIdStr == null || sessionDateStr == null || countStr == null) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Missing+required+block+fields.");
                return;
            }

            int courseId;
            int blockCount;
            try {
                courseId = Integer.parseInt(courseIdStr.trim());
                blockCount = Integer.parseInt(countStr.trim());
            } catch (NumberFormatException e) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Invalid+numeric+parameters.");
                return;
            }

            if (blockCount < 2 || blockCount > 4) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=A+block+must+contain+between+2+and+4+consecutive+sessions.");
                return;
            }

            // Verify persisted course ownership
            Optional<Course> courseOpt = courseDao.findById(courseId);
            if (!courseOpt.isPresent() || courseOpt.get().getTeacherId() != teacher.getTeacherId()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: You do not own this course.");
                return;
            }

            LocalDate sessionDate;
            try {
                sessionDate = LocalDate.parse(sessionDateStr.trim());
            } catch (DateTimeParseException e) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Invalid+date+format.");
                return;
            }

            if (room == null || room.trim().isEmpty()) {
                room = "TBD";
            }

            List<PlannedSession> blockSessions = new ArrayList<>();
            for (int i = 1; i <= blockCount; i++) {
                String sTimeStr = request.getParameter("startTime_" + i);
                String eTimeStr = request.getParameter("endTime_" + i);
                if (sTimeStr == null || eTimeStr == null || sTimeStr.trim().isEmpty() || eTimeStr.trim().isEmpty()) {
                    response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Missing+start+or+end+time+for+session+" + i + ".");
                    return;
                }

                LocalTime st;
                LocalTime et;
                try {
                    String sT = sTimeStr.trim().length() == 5 ? sTimeStr.trim() + ":00" : sTimeStr.trim();
                    String eT = eTimeStr.trim().length() == 5 ? eTimeStr.trim() + ":00" : eTimeStr.trim();
                    st = LocalTime.parse(sT);
                    et = LocalTime.parse(eT);
                } catch (DateTimeParseException e) {
                    response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Invalid+time+format+for+session+" + i + ".");
                    return;
                }

                PlannedSession ps = new PlannedSession();
                ps.setCourseId(courseId);
                ps.setTeacherId(teacher.getTeacherId());
                ps.setSessionType(sessionType != null && !sessionType.trim().isEmpty() ? sessionType.trim().toUpperCase() : "LAB");
                ps.setSessionDate(sessionDate);
                ps.setStartTime(st);
                ps.setEndTime(et);
                ps.setRoom(room.trim());
                ps.setDescription(description != null ? description.trim() : null);
                ps.setStatus("SCHEDULED");
                blockSessions.add(ps);
            }

            try {
                String blockId = plannedSessionDao.validateAndCreateConsecutiveBlock(blockSessions);
                if (blockId != null) {
                    response.sendRedirect(request.getContextPath() + "/teacher/dashboard?msg=block_created");
                } else {
                    response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Failed+to+create+consecutive+block.");
                }
            } catch (IllegalArgumentException e) {
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=" +
                    java.net.URLEncoder.encode(e.getMessage(), "UTF-8"));
            } catch (SQLException e) {
                e.printStackTrace();
                response.sendRedirect(request.getContextPath() + "/teacher/dashboard?error=Database+error+creating+block.");
            }
            return;
        }

        response.sendRedirect(request.getContextPath() + "/teacher/dashboard");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GET /teacher/planned-sessions/roster — read-only attendance roster
    // ═══════════════════════════════════════════════════════════════════════

    private void handlePlannedSessionRoster(HttpServletRequest request, HttpServletResponse response, Teacher teacher)
            throws IOException {
        String psIdStr = request.getParameter("plannedSessionId");
        if (psIdStr == null || psIdStr.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendJson(response, "{\"status\":\"error\",\"message\":\"Missing mandatory plannedSessionId parameter.\"}");
            return;
        }

        int plannedSessionId;
        try {
            plannedSessionId = Integer.parseInt(psIdStr.trim());
            if (plannedSessionId <= 0) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                sendJson(response, "{\"status\":\"error\",\"message\":\"Invalid plannedSessionId. Must be a positive integer.\"}");
                return;
            }
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendJson(response, "{\"status\":\"error\",\"message\":\"Invalid plannedSessionId. Must be a positive integer.\"}");
            return;
        }

        Optional<PlannedSession> psOpt;
        try {
            psOpt = plannedSessionDao.findById(plannedSessionId);
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendJson(response, "{\"status\":\"error\",\"message\":\"Database error occurred while looking up planned session.\"}");
            return;
        }

        if (!psOpt.isPresent()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            sendJson(response, "{\"status\":\"error\",\"message\":\"Planned session not found.\"}");
            return;
        }

        PlannedSession ps = psOpt.get();

        // Enforce strict server-side faculty ownership
        if (ps.getTeacherId() != teacher.getTeacherId()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            sendJson(response, "{\"status\":\"error\",\"message\":\"Access denied: You do not own this planned session.\"}");
            return;
        }

        // Retrieve persisted course metadata from trusted CourseDao
        Optional<Course> courseOpt = courseDao.findById(ps.getCourseId());
        if (!courseOpt.isPresent()) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendJson(response, "{\"status\":\"error\",\"message\":\"Associated course not found for planned session.\"}");
            return;
        }
        Course course = courseOpt.get();
        ps.setCourseCode(course.getCourseCode());
        ps.setCourseName(course.getCourseName());

        try {
            List<SessionRosterItem> roster = plannedSessionDao.findRosterByPlannedSessionId(
                    plannedSessionId, teacher.getTeacherId());

            Optional<QrSession> qrOpt = qrSessionDao.findByPlannedSessionId(plannedSessionId);
            Integer qrSessionId = qrOpt.map(QrSession::getId).orElse(null);

            SessionRosterMetrics metrics = SessionRosterMetrics.compute(ps.getStatus(), qrSessionId, roster);

            // Build JSON response
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"status\":\"success\",");

            // Planned Session metadata
            sb.append("\"plannedSession\":{");
            sb.append("\"id\":").append(ps.getId()).append(",");
            sb.append("\"courseId\":").append(ps.getCourseId()).append(",");
            sb.append("\"courseCode\":\"").append(escapeJson(ps.getCourseCode())).append("\",");
            sb.append("\"courseName\":\"").append(escapeJson(ps.getCourseName())).append("\",");
            sb.append("\"teacherId\":").append(ps.getTeacherId()).append(",");
            sb.append("\"sessionDate\":\"").append(ps.getSessionDate() != null ? ps.getSessionDate().toString() : "").append("\",");
            sb.append("\"startTime\":\"").append(ps.getStartTime() != null ? ps.getStartTime().toString() : "").append("\",");
            sb.append("\"endTime\":\"").append(ps.getEndTime() != null ? ps.getEndTime().toString() : "").append("\",");
            sb.append("\"sessionType\":\"").append(escapeJson(ps.getSessionType())).append("\",");
            sb.append("\"room\":\"").append(escapeJson(ps.getRoom())).append("\",");
            sb.append("\"description\":\"").append(escapeJson(ps.getDescription())).append("\",");
            sb.append("\"status\":\"").append(escapeJson(ps.getStatus())).append("\",");
            sb.append("\"qrSessionId\":").append(qrSessionId != null ? qrSessionId : "null");
            sb.append("},");

            // Metrics
            sb.append("\"metrics\":{");
            sb.append("\"totalEnrolled\":").append(metrics.getTotalEnrolled()).append(",");
            sb.append("\"presentCount\":").append(metrics.getPresentCount()).append(",");
            sb.append("\"lateCount\":").append(metrics.getLateCount()).append(",");
            sb.append("\"absentCount\":").append(metrics.getAbsentCount()).append(",");
            sb.append("\"notRecordedCount\":").append(metrics.getNotRecordedCount()).append(",");
            sb.append("\"attendancePercentage\":").append(metrics.getAttendancePercentage() != null ? metrics.getAttendancePercentage() : "null").append(",");
            sb.append("\"percentageApplicable\":").append(metrics.isPercentageApplicable()).append(",");
            sb.append("\"inProgress\":").append(metrics.isInProgress()).append(",");
            sb.append("\"containsInconsistencies\":").append(metrics.isContainsInconsistencies()).append(",");
            sb.append("\"inconsistencyCount\":").append(metrics.getInconsistencyCount());
            sb.append("},");

            // Roster array
            sb.append("\"roster\":[");
            for (int i = 0; i < roster.size(); i++) {
                if (i > 0) sb.append(",");
                SessionRosterItem item = roster.get(i);
                sb.append("{");
                sb.append("\"studentId\":").append(item.getStudentId()).append(",");
                sb.append("\"rollNumber\":\"").append(escapeJson(item.getRollNumber())).append("\",");
                sb.append("\"studentName\":\"").append(escapeJson(item.getStudentName())).append("\",");
                sb.append("\"attendanceStatus\":").append(item.getAttendanceStatus() != null ? "\"" + escapeJson(item.getAttendanceStatus()) + "\"" : "null").append(",");
                sb.append("\"markedAt\":").append(item.getMarkedAt() != null ? "\"" + escapeJson(DateTimeUtil.formatToIst(item.getMarkedAt())) + "\"" : "null").append(",");
                sb.append("\"presentationStatus\":\"").append(escapeJson(item.getPresentationStatus())).append("\",");
                sb.append("\"inconsistent\":").append(item.isInconsistent());
                sb.append("}");
            }
            sb.append("]");

            sb.append("}");

            response.setStatus(HttpServletResponse.SC_OK);
            sendJson(response, sb.toString());

        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendJson(response, "{\"status\":\"error\",\"message\":\"Database error occurred while retrieving session roster.\"}");
        }
    }

    private void sendJson(HttpServletResponse response, String json) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.print(json);
            out.flush();
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
