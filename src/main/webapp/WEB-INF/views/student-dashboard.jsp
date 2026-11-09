<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.attendance.model.Attendance" %>
<%@ page import="com.attendance.model.Course" %>
<%@ page import="com.attendance.model.CourseSchedule" %>
<%@ page import="com.attendance.model.Student" %>
<%@ page import="com.attendance.model.User" %>
<%@ page import="com.attendance.util.DateTimeUtil" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%
    Student student = (Student) request.getAttribute("student");
    User user = (User) session.getAttribute("currentUser");
    List<Course> courses = (List<Course>) request.getAttribute("courses");
    Map<Integer, Double> attendanceMap = (Map<Integer, Double>) request.getAttribute("attendanceMap");
    Double overallAttendance = (Double) request.getAttribute("overallAttendance");
    Boolean hasLowAttendance = (Boolean) request.getAttribute("hasLowAttendance");
    List<CourseSchedule> upcomingClasses = (List<CourseSchedule>) request.getAttribute("upcomingClasses");
    List<Course> availableCourses = (List<Course>) request.getAttribute("availableCourses");
    List<Attendance> history = (List<Attendance>) request.getAttribute("history");
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    String msg = request.getParameter("msg");
    String error = request.getParameter("error");
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Student Portal - ScanIn Academic Attendance</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet">
</head>
<body>
    <%@ include file="common-icons.jspf" %>

    <!-- Header Navigation -->
    <nav class="navbar">
        <a href="${pageContext.request.contextPath}/student/dashboard" class="nav-brand">
            <div class="brand-icon">
                <svg class="ui-icon"><use href="#icon-student"></use></svg>
            </div>
            <span>ScanIn Student Portal</span>
        </a>
        <div class="nav-actions">
            <span class="user-badge">
                <span class="role-pill student">STUDENT</span>
                <span><%= user != null ? user.getName() : "Student" %></span>
            </span>
            <a href="${pageContext.request.contextPath}/logout" class="btn btn-secondary btn-sm">
                <svg class="ui-icon ui-icon-xs"><use href="#icon-logout"></use></svg>
                <span>Sign Out</span>
            </a>
        </div>
    </nav>

    <main class="container">
        <!-- Message Banners -->
        <% if ("registered".equals(msg)) { %>
            <div class="alert alert-success">
                <svg class="ui-icon ui-icon-sm"><use href="#icon-check"></use></svg>
                <span>Course registration successful! The course is now listed in your Academics section.</span>
            </div>
        <% } else if ("dropped".equals(msg)) { %>
            <div class="alert alert-info">
                <svg class="ui-icon ui-icon-sm"><use href="#icon-info"></use></svg>
                <span>Course successfully dropped from your semester schedule.</span>
            </div>
        <% } %>

        <% if (error != null) { %>
            <div class="alert alert-danger">
                <svg class="ui-icon ui-icon-sm"><use href="#icon-alert"></use></svg>
                <span><%= error.replace("+", " ") %></span>
            </div>
        <% } %>

        <% if (hasLowAttendance != null && hasLowAttendance) { %>
            <div class="alert alert-danger" style="border-left: 5px solid #582c83; background: #fff5f5;">
                <svg class="ui-icon ui-icon-md" style="color: #b91c1c;"><use href="#icon-alert"></use></svg>
                <div>
                    <strong>Low Attendance Warning Notice:</strong>
                    Your attendance in one or more registered courses is currently below the mandatory <strong>75%</strong> university requirement.
                    Please attend all upcoming classes to avoid examination debarment.
                </div>
            </div>
        <% } %>

        <!-- Header Actions -->
        <header class="page-header">
            <div>
                <h1 class="page-title">Academic Portal & Attendance</h1>
                <p class="page-subtitle">
                    <%= student != null ? student.getDepartment() + " &bull; Semester " + student.getSemester() + " &bull; Roll No: " + student.getRollNumber() : "Official Academic Record" %>
                </p>
            </div>
            <div>
                <a href="${pageContext.request.contextPath}/qr/scan" class="btn btn-primary btn-lg">
                    <svg class="ui-icon"><use href="#icon-camera"></use></svg>
                    <span>Launch QR Scanner</span>
                </a>
            </div>
        </header>

        <!-- ════════════════════════════════════════════════════════════ -->
        <!-- SECTION 1: OFFICIAL RECORD                                    -->
        <!-- ════════════════════════════════════════════════════════════ -->
        <section class="glass-card" style="margin-bottom: 2rem; border-radius: 16px;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem; border-bottom: 1.5px solid var(--border-subtle); padding-bottom: 0.75rem;">
                <h2 style="font-size: 1.25rem; font-weight: 700; color: #582c83; display: flex; align-items: center; gap: 0.5rem;">
                    <svg class="ui-icon ui-icon-sm"><use href="#icon-clipboard"></use></svg>
                    <span>Official Record</span>
                </h2>
                <span class="badge badge-present" style="font-size: 0.75rem;">
                    <svg class="ui-icon ui-icon-xs"><use href="#icon-check"></use></svg>
                    <span>Active &bull; Good Standing</span>
                </span>
            </div>

            <% if (student != null) { %>
                <div class="grid-cols-3" style="gap: 1.25rem;">
                    <div>
                        <div style="font-size: 0.78rem; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.04em;">Full Legal Name</div>
                        <div style="font-size: 1rem; font-weight: 600; color: #0f172a; margin-top: 0.2rem;"><%= student.getName() %></div>
                    </div>
                    <div>
                        <div style="font-size: 0.78rem; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.04em;">Roll Number</div>
                        <div style="font-size: 1rem; font-weight: 600; color: #582c83; font-family: monospace; margin-top: 0.2rem;"><%= student.getRollNumber() %></div>
                    </div>
                    <div>
                        <div style="font-size: 0.78rem; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.04em;">Department</div>
                        <div style="font-size: 1rem; font-weight: 600; color: #0f172a; margin-top: 0.2rem;"><%= student.getDepartment() %></div>
                    </div>
                    <div>
                        <div style="font-size: 0.78rem; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.04em;">Current Semester</div>
                        <div style="font-size: 1rem; font-weight: 600; color: #0f172a; margin-top: 0.2rem;">Semester <%= student.getSemester() %></div>
                    </div>
                    <div>
                        <div style="font-size: 0.78rem; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.04em;">Institutional Email</div>
                        <div style="font-size: 1rem; font-weight: 600; color: #0f172a; margin-top: 0.2rem;"><%= student.getEmail() %></div>
                    </div>
                    <div>
                        <div style="font-size: 0.78rem; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.04em;">Contact Phone</div>
                        <div style="font-size: 1rem; font-weight: 600; color: #0f172a; margin-top: 0.2rem;"><%= student.getPhone() != null ? student.getPhone() : "Not Provided" %></div>
                    </div>
                </div>
            <% } else { %>
                <p style="color: var(--text-muted);">Official student record is not available.</p>
            <% } %>
        </section>

        <!-- ════════════════════════════════════════════════════════════ -->
        <!-- SECTION 2: ACADEMICS & COURSE REGISTRATION                     -->
        <!-- ════════════════════════════════════════════════════════════ -->
        <section class="glass-card" style="margin-bottom: 2rem; border-radius: 16px;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem; border-bottom: 1.5px solid var(--border-subtle); padding-bottom: 0.75rem;">
                <h2 style="font-size: 1.25rem; font-weight: 700; color: #582c83; display: flex; align-items: center; gap: 0.5rem;">
                    <svg class="ui-icon ui-icon-sm"><use href="#icon-book"></use></svg>
                    <span>Academics & Enrolled Courses</span>
                </h2>
                <div style="display: flex; align-items: center; gap: 0.75rem;">
                    <span style="font-size: 0.88rem; color: var(--text-secondary); font-weight: 600;">Overall Attendance:</span>
                    <span class="badge <%= (overallAttendance != null && overallAttendance >= 75.0) ? "badge-present" : "badge-absent" %>" style="font-size: 0.9rem; padding: 0.35rem 0.8rem;">
                        <%= overallAttendance != null ? overallAttendance : 100.0 %>%
                    </span>
                </div>
            </div>

            <% if (courses == null || courses.isEmpty()) { %>
                <div style="text-align: center; padding: 2.25rem 1rem; background: var(--bg-subtle); border-radius: var(--radius-sm); border: 1.5px dashed var(--border-subtle);">
                    <div style="width: 42px; height: 42px; margin: 0 auto 0.65rem; border-radius: 50%; background: #ffffff; display: flex; align-items: center; justify-content: center; color: var(--text-muted); border: 1px solid var(--border-subtle);">
                        <svg class="ui-icon ui-icon-md"><use href="#icon-book"></use></svg>
                    </div>
                    <h3 style="font-size: 1.15rem; font-weight: 700; color: #1e293b; margin-bottom: 0.3rem;">No Courses Registered</h3>
                    <p style="color: var(--text-muted); font-size: 0.92rem; max-width: 480px; margin: 0 auto 1.25rem auto;">
                        You are not currently enrolled in any academic courses for this semester. Browse available courses below to register.
                    </p>
                </div>
            <% } else { %>
                <div class="table-responsive">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>Course Code</th>
                                <th>Course Title</th>
                                <th>Faculty Instructor</th>
                                <th>Attendance %</th>
                                <th>Compliance Status</th>
                                <th style="text-align: right;">Action</th>
                            </tr>
                        </thead>
                        <tbody>
                            <% for (Course c : courses) { 
                                Double pct = attendanceMap != null ? attendanceMap.get(c.getId()) : 100.0;
                                if (pct == null) pct = 100.0;
                                boolean isWarning = pct < 75.0;
                            %>
                                <tr>
                                    <td><strong><%= c.getCourseCode() %></strong></td>
                                    <td><%= c.getCourseName() %></td>
                                    <td style="color: var(--text-secondary);"><%= c.getTeacherName() != null ? c.getTeacherName() : "Faculty Staff" %></td>
                                    <td style="width: 180px;">
                                        <div style="font-weight: 700; color: <%= isWarning ? "#582c83" : "#059669" %>;">
                                            <%= pct %>%
                                        </div>
                                        <div class="progress-container">
                                            <div class="progress-bar <%= isWarning ? "warning" : "success" %>" style="width: <%= Math.min(100.0, pct) %>%;"></div>
                                        </div>
                                    </td>
                                    <td>
                                        <% if (isWarning) { %>
                                            <span class="badge badge-absent">
                                                <svg class="ui-icon ui-icon-xs"><use href="#icon-alert"></use></svg>
                                                <span>Low Attendance (&lt; 75%)</span>
                                            </span>
                                        <% } else { %>
                                            <span class="badge badge-present">
                                                <svg class="ui-icon ui-icon-xs"><use href="#icon-check"></use></svg>
                                                <span>Eligible (&ge; 75%)</span>
                                            </span>
                                        <% } %>
                                    </td>
                                    <td style="text-align: right;">
                                        <form method="POST" action="${pageContext.request.contextPath}/student/courses/drop" style="display: inline;" onsubmit="return confirm('Are you sure you want to drop <%= c.getCourseCode() %>?');">
                                            <input type="hidden" name="courseId" value="<%= c.getId() %>">
                                            <button type="submit" class="btn btn-secondary btn-sm" style="color: #582c83; border-color: var(--purple-border);">
                                                Drop
                                            </button>
                                        </form>
                                    </td>
                                </tr>
                            <% } %>
                        </tbody>
                    </table>
                </div>
            <% } %>

            <!-- Course Registration Panel -->
            <div style="margin-top: 2rem; padding-top: 1.5rem; border-top: 1.5px solid var(--border-subtle);">
                <h3 style="font-size: 1.05rem; font-weight: 700; color: #1e293b; margin-bottom: 0.8rem; display: flex; align-items: center; gap: 0.4rem;">
                    <svg class="ui-icon ui-icon-xs" style="color: #582c83;"><use href="#icon-plus"></use></svg>
                    <span>Register Available Courses</span>
                </h3>
                <% if (availableCourses == null || availableCourses.isEmpty()) { %>
                    <p style="color: var(--text-muted); font-size: 0.9rem; font-style: italic;">
                        You are enrolled in all currently offered department courses for this semester.
                    </p>
                <% } else { %>
                    <div class="table-responsive">
                        <table class="data-table">
                            <thead>
                                <tr>
                                    <th>Code</th>
                                    <th>Course Title</th>
                                    <th>Instructor</th>
                                    <th>Department</th>
                                    <th style="text-align: right;">Registration</th>
                                </tr>
                            </thead>
                            <tbody>
                                <% for (Course ac : availableCourses) { %>
                                    <tr>
                                        <td><strong><%= ac.getCourseCode() %></strong></td>
                                        <td><%= ac.getCourseName() %></td>
                                        <td><%= ac.getTeacherName() != null ? ac.getTeacherName() : "Faculty" %></td>
                                        <td><%= ac.getDepartment() %></td>
                                        <td style="text-align: right;">
                                            <form method="POST" action="${pageContext.request.contextPath}/student/courses/register" style="display: inline;">
                                                <input type="hidden" name="courseId" value="<%= ac.getId() %>">
                                                <button type="submit" class="btn btn-primary btn-sm">
                                                    <svg class="ui-icon ui-icon-xs"><use href="#icon-plus"></use></svg>
                                                    <span>Enroll Course</span>
                                                </button>
                                            </form>
                                        </td>
                                    </tr>
                                <% } %>
                            </tbody>
                        </table>
                    </div>
                <% } %>
            </div>
        </section>

        <!-- ════════════════════════════════════════════════════════════ -->
        <!-- SECTION 3: UPCOMING CLASSES TIMETABLE                          -->
        <!-- ════════════════════════════════════════════════════════════ -->
        <section class="glass-card" style="margin-bottom: 2rem; border-radius: 16px;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem; border-bottom: 1.5px solid var(--border-subtle); padding-bottom: 0.75rem;">
                <h2 style="font-size: 1.25rem; font-weight: 700; color: #582c83; display: flex; align-items: center; gap: 0.5rem;">
                    <svg class="ui-icon ui-icon-sm"><use href="#icon-calendar"></use></svg>
                    <span>Upcoming Classes & Schedule</span>
                </h2>
                <span style="font-size: 0.82rem; color: var(--text-muted);">Weekly Lecture Timetable</span>
            </div>

            <% if (upcomingClasses == null || upcomingClasses.isEmpty()) { %>
                <div style="text-align: center; padding: 2rem 1rem; background: var(--bg-subtle); border-radius: var(--radius-sm); border: 1.5px dashed var(--border-subtle);">
                    <div style="width: 42px; height: 42px; margin: 0 auto 0.5rem; border-radius: 50%; background: #ffffff; display: flex; align-items: center; justify-content: center; color: var(--text-muted); border: 1px solid var(--border-subtle);">
                        <svg class="ui-icon ui-icon-md"><use href="#icon-calendar"></use></svg>
                    </div>
                    <div style="font-weight: 600; color: #1e293b;">No Scheduled Classes Found</div>
                    <div style="font-size: 0.88rem; color: var(--text-muted); margin-top: 0.2rem;">
                        No upcoming lecture timeslots are scheduled for your enrolled courses at this time.
                    </div>
                </div>
            <% } else { %>
                <div class="table-responsive">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>Day</th>
                                <th>Time Window</th>
                                <th>Course</th>
                                <th>Instructor</th>
                                <th>Location / Room</th>
                            </tr>
                        </thead>
                        <tbody>
                            <% for (CourseSchedule cs : upcomingClasses) { %>
                                <tr>
                                    <td><span class="badge" style="background: var(--purple-subtle); color: #582c83; border: 1px solid var(--purple-border);"><%= cs.getDayOfWeek() %></span></td>
                                    <td style="font-weight: 600;"><%= cs.getStartTime() %> &ndash; <%= cs.getEndTime() %></td>
                                    <td><strong><%= cs.getCourseCode() %></strong> &mdash; <%= cs.getCourseName() %></td>
                                    <td><%= cs.getTeacherName() != null ? cs.getTeacherName() : "Faculty Staff" %></td>
                                    <td><span style="color: var(--text-secondary);"><%= cs.getRoom() %></span></td>
                                </tr>
                            <% } %>
                        </tbody>
                    </table>
                </div>
            <% } %>
        </section>

        <!-- ════════════════════════════════════════════════════════════ -->
        <!-- SECTION 4: ATTENDANCE HISTORY LOG                              -->
        <!-- ════════════════════════════════════════════════════════════ -->
        <section class="glass-card" style="border-radius: 16px;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem; border-bottom: 1.5px solid var(--border-subtle); padding-bottom: 0.75rem;">
                <h2 style="font-size: 1.25rem; font-weight: 700; color: #582c83; display: flex; align-items: center; gap: 0.5rem;">
                    <svg class="ui-icon ui-icon-sm"><use href="#icon-clock"></use></svg>
                    <span>Attendance History Log</span>
                </h2>
                <span class="badge badge-present" style="font-size: 0.75rem;">
                    Total Sessions Recorded: <%= history != null ? history.size() : 0 %>
                </span>
            </div>

            <% if (history == null || history.isEmpty()) { %>
                <div style="text-align: center; padding: 2.25rem 1rem; background: var(--bg-subtle); border-radius: var(--radius-sm); border: 1.5px dashed var(--border-subtle);">
                    <div style="width: 42px; height: 42px; margin: 0 auto 0.65rem; border-radius: 50%; background: #ffffff; display: flex; align-items: center; justify-content: center; color: var(--text-muted); border: 1px solid var(--border-subtle);">
                        <svg class="ui-icon ui-icon-md"><use href="#icon-clock"></use></svg>
                    </div>
                    <h3 style="font-size: 1.15rem; font-weight: 700; color: #1e293b; margin-bottom: 0.3rem;">No Attendance Records Yet</h3>
                    <p style="color: var(--text-muted); font-size: 0.92rem; max-width: 480px; margin: 0 auto;">
                        You have not attended any recorded class sessions yet. Launch the QR scanner during class to mark your presence.
                    </p>
                </div>
            <% } else { %>
                <div class="table-responsive">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>Timestamp</th>
                                <th>Course Code</th>
                                <th>Course Title</th>
                                <th>Status</th>
                                <th>Verification Method</th>
                            </tr>
                        </thead>
                        <tbody>
                            <% for (Attendance att : history) { %>
                                <tr>
                                    <td style="color: var(--text-secondary);">
                                        <%= att.getMarkedAt() != null ? DateTimeUtil.formatToIst(att.getMarkedAt()) : "&mdash;" %>
                                    </td>
                                    <td><strong><%= att.getCourseCode() %></strong></td>
                                    <td>
                                        <div><%= att.getCourseName() %></div>
                                        <% if (att.getScheduleFormatted() != null) { %>
                                            <div style="font-size: 0.75rem; color: #64748b; margin-top: 0.15rem; display: flex; align-items: center; gap: 0.25rem;">
                                                <svg class="ui-icon ui-icon-xs" style="color: #582c83;"><use href="#icon-calendar"></use></svg>
                                                <span>Scheduled: <%= att.getScheduleFormatted() %></span>
                                            </div>
                                        <% } %>
                                    </td>
                                    <td>
                                        <% if ("PRESENT".equalsIgnoreCase(att.getStatus())) { %>
                                            <span class="badge badge-present">
                                                <svg class="ui-icon ui-icon-xs"><use href="#icon-check"></use></svg>
                                                <span>PRESENT</span>
                                            </span>
                                        <% } else if ("OD".equalsIgnoreCase(att.getStatus())) { %>
                                            <span class="badge badge-od">
                                                <svg class="ui-icon ui-icon-xs"><use href="#icon-shield-check"></use></svg>
                                                <span>OD (On Duty)</span>
                                            </span>
                                        <% } else if ("LATE".equalsIgnoreCase(att.getStatus())) { %>
                                            <span class="badge badge-late">
                                                <svg class="ui-icon ui-icon-xs"><use href="#icon-clock"></use></svg>
                                                <span>LATE</span>
                                            </span>
                                        <% } else if ("EXCUSED".equalsIgnoreCase(att.getStatus())) { %>
                                            <span class="badge badge-excused">
                                                <svg class="ui-icon ui-icon-xs"><use href="#icon-info"></use></svg>
                                                <span>EXCUSED (Medical)</span>
                                            </span>
                                        <% } else { %>
                                            <span class="badge badge-absent">
                                                <svg class="ui-icon ui-icon-xs"><use href="#icon-close"></use></svg>
                                                <span><%= att.getStatus() %></span>
                                            </span>
                                        <% } %>
                                    </td>
                                    <td>
                                        <span style="font-size: 0.84rem; color: var(--text-muted); display: flex; align-items: center; gap: 0.35rem;">
                                            <svg class="ui-icon ui-icon-xs" style="color: #059669;"><use href="#icon-shield-check"></use></svg>
                                            <span>Dynamic QR Token Verified</span>
                                        </span>
                                    </td>
                                </tr>
                            <% } %>
                        </tbody>
                    </table>
                </div>
            <% } %>
        </section>
    </main>

    <!-- Portal Footer -->
    <footer class="footer">
        <p>ScanIn Smart Attendance System &bull; Official Student Academic Portal &bull; College DBMS Project</p>
    </footer>

</body>
</html>
