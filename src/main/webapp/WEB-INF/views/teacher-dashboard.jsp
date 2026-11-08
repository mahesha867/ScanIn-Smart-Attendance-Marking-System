<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.attendance.model.Course" %>
<%@ page import="com.attendance.model.CourseSchedule" %>
<%@ page import="com.attendance.model.Teacher" %>
<%@ page import="com.attendance.model.User" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%
    Teacher teacher = (Teacher) request.getAttribute("teacher");
    User user = (User) session.getAttribute("currentUser");
    List<Course> courses = (List<Course>) request.getAttribute("courses");
    Map<Integer, List<CourseSchedule>> scheduleMap = (Map<Integer, List<CourseSchedule>>) request.getAttribute("scheduleMap");
    Map<Integer, Integer> enrollmentCountMap = (Map<Integer, Integer>) request.getAttribute("enrollmentCountMap");

    String msg = request.getParameter("msg");
    String error = request.getParameter("error");
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Faculty Dashboard - Course & Semester Management</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet">
</head>
<body>
    <%@ include file="common-icons.jspf" %>

    <!-- Header Navigation -->
    <nav class="navbar">
        <a href="${pageContext.request.contextPath}/teacher/dashboard" class="nav-brand">
            <div class="brand-icon">
                <svg class="ui-icon"><use href="#icon-teacher"></use></svg>
            </div>
            <span>ScanIn Faculty Portal</span>
        </a>
        <div class="nav-actions">
            <span class="user-badge">
                <span class="role-pill teacher">FACULTY</span>
                <span><%= user != null ? user.getName() : "Faculty" %></span>
            </span>
            <a href="${pageContext.request.contextPath}/logout" class="btn btn-secondary btn-sm">
                <svg class="ui-icon ui-icon-xs"><use href="#icon-logout"></use></svg>
                <span>Sign Out</span>
            </a>
        </div>
    </nav>

    <main class="container">
        <!-- Message Banners -->
        <% if ("course_created".equals(msg)) { %>
            <div class="alert alert-success">
                <svg class="ui-icon ui-icon-sm"><use href="#icon-check"></use></svg>
                <span>Course successfully created and added to your assigned courses!</span>
            </div>
        <% } else if ("course_updated".equals(msg)) { %>
            <div class="alert alert-success">
                <svg class="ui-icon ui-icon-sm"><use href="#icon-check"></use></svg>
                <span>Course details updated successfully.</span>
            </div>
        <% } else if ("course_deleted".equals(msg)) { %>
            <div class="alert alert-info">
                <svg class="ui-icon ui-icon-sm"><use href="#icon-info"></use></svg>
                <span>Course successfully deleted from system.</span>
            </div>
        <% } else if ("schedule_added".equals(msg)) { %>
            <div class="alert alert-success">
                <svg class="ui-icon ui-icon-sm"><use href="#icon-check"></use></svg>
                <span>Lecture timeslot successfully scheduled.</span>
            </div>
        <% } else if ("schedule_deleted".equals(msg)) { %>
            <div class="alert alert-info">
                <svg class="ui-icon ui-icon-sm"><use href="#icon-info"></use></svg>
                <span>Lecture timeslot removed from timetable.</span>
            </div>
        <% } %>

        <% if (error != null) { %>
            <div class="alert alert-danger">
                <svg class="ui-icon ui-icon-sm"><use href="#icon-alert"></use></svg>
                <span><%= error.replace("+", " ") %></span>
            </div>
        <% } %>

        <header class="page-header">
            <div>
                <h1 class="page-title">Course & Semester Management</h1>
                <p class="page-subtitle">
                    <%= teacher != null ? teacher.getName() + " &bull; " + teacher.getDepartment() + " &bull; Emp ID: " + teacher.getEmployeeId() : "Faculty Portal" %>
                </p>
            </div>
            <div style="display: flex; gap: 0.75rem;">
                <a href="${pageContext.request.contextPath}/reports" class="btn btn-secondary">
                    <svg class="ui-icon ui-icon-sm"><use href="#icon-chart"></use></svg>
                    <span>Attendance Reports</span>
                </a>
                <button type="button" class="btn btn-primary" onclick="document.getElementById('newCourseCard').scrollIntoView({behavior: 'smooth'});">
                    <svg class="ui-icon ui-icon-sm"><use href="#icon-plus"></use></svg>
                    <span>Add New Course</span>
                </button>
            </div>
        </header>

        <!-- Stats Overview -->
        <div class="grid-cols-3" style="margin-bottom: 2.2rem;">
            <div class="stat-card">
                <div class="stat-icon">
                    <svg class="ui-icon"><use href="#icon-book"></use></svg>
                </div>
                <div>
                    <div class="stat-val"><%= courses != null ? courses.size() : 0 %></div>
                    <div class="stat-label">Faculty-Owned Courses</div>
                </div>
            </div>
            <div class="stat-card">
                <div class="stat-icon emerald">
                    <svg class="ui-icon"><use href="#icon-users"></use></svg>
                </div>
                <div>
                    <%
                        int totalStudentsEnrolled = 0;
                        if (enrollmentCountMap != null) {
                            for (int count : enrollmentCountMap.values()) totalStudentsEnrolled += count;
                        }
                    %>
                    <div class="stat-val"><%= totalStudentsEnrolled %></div>
                    <div class="stat-label">Enrolled Students Total</div>
                </div>
            </div>
            <div class="stat-card">
                <div class="stat-icon cyan">
                    <svg class="ui-icon"><use href="#icon-calendar"></use></svg>
                </div>
                <div>
                    <%
                        int totalSchedules = 0;
                        if (scheduleMap != null) {
                            for (List<CourseSchedule> list : scheduleMap.values()) totalSchedules += list.size();
                        }
                    %>
                    <div class="stat-val"><%= totalSchedules %></div>
                    <div class="stat-label">Planned Sessions Weekly</div>
                </div>
            </div>
        </div>

        <!-- ════════════════════════════════════════════════════════════ -->
        <!-- ASSIGNED FACULTY COURSES                                     -->
        <!-- ════════════════════════════════════════════════════════════ -->
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem;">
            <h2 style="font-size: 1.35rem; font-weight: 700; color: #582c83; display: flex; align-items: center; gap: 0.5rem;">
                <svg class="ui-icon ui-icon-sm"><use href="#icon-book"></use></svg>
                <span>Assigned Courses & Rosters</span>
            </h2>
            <span style="font-size: 0.85rem; color: var(--text-muted);">Owned & Managed by You</span>
        </div>

        <% if (courses == null || courses.isEmpty()) { %>
            <div class="glass-card" style="text-align: center; padding: 2.5rem 1rem; border: 1.5px dashed var(--border-subtle); margin-bottom: 2.5rem;">
                <div style="width: 42px; height: 42px; margin: 0 auto 0.65rem; border-radius: 50%; background: var(--bg-subtle); display: flex; align-items: center; justify-content: center; color: var(--text-muted); border: 1px solid var(--border-subtle);">
                    <svg class="ui-icon ui-icon-md"><use href="#icon-book"></use></svg>
                </div>
                <h3 style="font-size: 1.15rem; font-weight: 700; color: #1e293b; margin-bottom: 0.3rem;">No Courses Found</h3>
                <p style="color: var(--text-muted); font-size: 0.92rem; max-width: 480px; margin: 0 auto 1.25rem auto;">
                    You do not currently have any assigned courses. Use the form below to create your first course.
                </p>
            </div>
        <% } else { %>
            <div class="grid-cols-2" style="margin-bottom: 2.5rem;">
                <% for (Course c : courses) { 
                    int studentCount = enrollmentCountMap != null && enrollmentCountMap.containsKey(c.getId()) ? enrollmentCountMap.get(c.getId()) : 0;
                    List<CourseSchedule> schedules = scheduleMap != null ? scheduleMap.get(c.getId()) : null;
                %>
                    <div class="glass-card" style="display: flex; flex-direction: column; justify-content: space-between;">
                        <div>
                            <!-- Header & Tag -->
                            <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 0.8rem;">
                                <span class="badge" style="background: var(--purple-subtle); color: #582c83; border: 1px solid var(--purple-border); font-size: 0.85rem;">
                                    <%= c.getCourseCode() %>
                                </span>
                                <span style="font-size: 0.82rem; font-weight: 600; color: var(--text-secondary);">
                                    Semester <%= c.getSemester() %> &bull; <%= c.getDepartment() %>
                                </span>
                            </div>

                            <h3 style="font-size: 1.25rem; font-weight: 700; color: #0f172a; margin-bottom: 0.35rem;">
                                <%= c.getCourseName() %>
                            </h3>
                            <div style="font-size: 0.85rem; color: var(--text-muted); margin-bottom: 1.25rem; display: flex; align-items: center; gap: 0.4rem;">
                                <svg class="ui-icon ui-icon-xs"><use href="#icon-users"></use></svg>
                                <span><%= studentCount %> Enrolled Student<%= studentCount == 1 ? "" : "s" %></span>
                            </div>

                            <!-- Planned Lecture Timetable for this course -->
                            <div style="background: var(--bg-subtle); border-radius: var(--radius-sm); padding: 0.85rem 1rem; margin-bottom: 1.25rem; border: 1px solid var(--border-subtle);">
                                <div style="font-size: 0.78rem; font-weight: 700; text-transform: uppercase; color: #582c83; margin-bottom: 0.5rem; letter-spacing: 0.04em; display: flex; align-items: center; gap: 0.4rem;">
                                    <svg class="ui-icon ui-icon-xs"><use href="#icon-calendar"></use></svg>
                                    <span>Planned Lecture Schedule</span>
                                </div>
                                <% if (schedules == null || schedules.isEmpty()) { %>
                                    <div style="font-size: 0.82rem; color: var(--text-muted); font-style: italic;">
                                        No lecture timeslots scheduled yet.
                                    </div>
                                <% } else { %>
                                    <ul style="list-style: none; padding: 0; margin: 0; font-size: 0.84rem;">
                                        <% for (CourseSchedule cs : schedules) { %>
                                            <li style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.35rem;">
                                                <span><strong><%= cs.getDayOfWeek() %>:</strong> <%= cs.getStartTime() %> - <%= cs.getEndTime() %> (<%= cs.getRoom() %>)</span>
                                                <form method="POST" action="${pageContext.request.contextPath}/teacher/schedules/delete" style="display: inline;" onsubmit="return confirm('Remove this timeslot?');">
                                                    <input type="hidden" name="scheduleId" value="<%= cs.getId() %>">
                                                    <button type="submit" title="Remove timeslot" style="background: none; border: none; color: #94a3b8; cursor: pointer; padding: 2px;">
                                                        <svg class="ui-icon ui-icon-xs" style="color: #64748b;"><use href="#icon-close"></use></svg>
                                                    </button>
                                                </form>
                                            </li>
                                        <% } %>
                                    </ul>
                                <% } %>

                                <!-- Quick Add Schedule Form -->
                                <details style="margin-top: 0.6rem;">
                                    <summary style="font-size: 0.8rem; font-weight: 600; color: #582c83; cursor: pointer; display: inline-flex; align-items: center; gap: 0.25rem;">
                                        <svg class="ui-icon ui-icon-xs"><use href="#icon-plus"></use></svg>
                                        <span>Add Timeslot</span>
                                    </summary>
                                    <form method="POST" action="${pageContext.request.contextPath}/teacher/schedules/create" style="margin-top: 0.6rem; display: grid; grid-template-columns: 1fr 1fr; gap: 0.4rem;">
                                        <input type="hidden" name="courseId" value="<%= c.getId() %>">
                                        <select name="dayOfWeek" class="form-input" style="padding: 0.4rem; font-size: 0.8rem;" required>
                                            <option value="Monday">Monday</option>
                                            <option value="Tuesday">Tuesday</option>
                                            <option value="Wednesday">Wednesday</option>
                                            <option value="Thursday">Thursday</option>
                                            <option value="Friday">Friday</option>
                                            <option value="Saturday">Saturday</option>
                                        </select>
                                        <input type="text" name="room" placeholder="Room/Hall" class="form-input" style="padding: 0.4rem; font-size: 0.8rem;" required>
                                        <input type="time" name="startTime" class="form-input" style="padding: 0.4rem; font-size: 0.8rem;" required>
                                        <input type="time" name="endTime" class="form-input" style="padding: 0.4rem; font-size: 0.8rem;" required>
                                        <button type="submit" class="btn btn-primary btn-sm" style="grid-column: span 2; padding: 0.4rem;">Save Schedule</button>
                                    </form>
                                </details>
                            </div>
                        </div>

                        <!-- Actions Grid -->
                        <div style="display: flex; gap: 0.6rem; flex-wrap: wrap; border-top: 1px solid var(--border-subtle); padding-top: 1rem;">
                            <a href="${pageContext.request.contextPath}/qr/generate?courseId=<%= c.getId() %>" class="btn btn-primary btn-sm" style="flex: 1;">
                                <svg class="ui-icon ui-icon-xs"><use href="#icon-qr"></use></svg>
                                <span>Start Live QR</span>
                            </a>
                            <a href="${pageContext.request.contextPath}/teacher/courses/roster?courseId=<%= c.getId() %>" class="btn btn-secondary btn-sm">
                                <svg class="ui-icon ui-icon-xs"><use href="#icon-users"></use></svg>
                                <span>Roster (<%= studentCount %>)</span>
                            </a>
                            <details style="position: relative;">
                                <summary class="btn btn-secondary btn-sm" style="cursor: pointer;" title="Course Settings">
                                    <svg class="ui-icon ui-icon-xs"><use href="#icon-settings"></use></svg>
                                </summary>
                                <div style="position: absolute; right: 0; bottom: 100%; margin-bottom: 0.5rem; background: #ffffff; border: 1.5px solid var(--border-subtle); border-radius: var(--radius-sm); padding: 1.25rem; width: 290px; box-shadow: var(--shadow-lg); z-index: 50;">
                                    <h4 style="font-size: 0.92rem; font-weight: 700; color: #582c83; margin-bottom: 0.75rem; display: flex; align-items: center; gap: 0.4rem;">
                                        <svg class="ui-icon ui-icon-xs"><use href="#icon-edit"></use></svg>
                                        <span>Edit Course Details</span>
                                    </h4>
                                    <form method="POST" action="${pageContext.request.contextPath}/teacher/courses/edit">
                                        <input type="hidden" name="courseId" value="<%= c.getId() %>">
                                        <div style="margin-bottom: 0.5rem;">
                                            <label style="font-size: 0.75rem; font-weight: 600; color: #334155;">Title</label>
                                            <input type="text" name="courseName" value="<%= c.getCourseName() %>" class="form-input" style="padding: 0.4rem 0.6rem; font-size: 0.82rem;" required>
                                        </div>
                                        <div style="margin-bottom: 0.5rem;">
                                            <label style="font-size: 0.75rem; font-weight: 600; color: #334155;">Department</label>
                                            <input type="text" name="department" value="<%= c.getDepartment() %>" class="form-input" style="padding: 0.4rem 0.6rem; font-size: 0.82rem;" required>
                                        </div>
                                        <div style="margin-bottom: 0.75rem;">
                                            <label style="font-size: 0.75rem; font-weight: 600; color: #334155;">Semester</label>
                                            <input type="number" name="semester" min="1" max="8" value="<%= c.getSemester() %>" class="form-input" style="padding: 0.4rem 0.6rem; font-size: 0.82rem;" required>
                                        </div>
                                        <button type="submit" class="btn btn-primary btn-sm" style="width: 100%; margin-bottom: 0.5rem;">Update Course</button>
                                    </form>
                                    <form method="POST" action="${pageContext.request.contextPath}/teacher/courses/delete" onsubmit="return confirm('Are you sure you want to permanently delete this course? All associated sessions will be removed.');">
                                        <input type="hidden" name="courseId" value="<%= c.getId() %>">
                                        <button type="submit" class="btn btn-danger-outline btn-sm" style="width: 100%;">Delete Course</button>
                                    </form>
                                </div>
                            </details>
                        </div>
                    </div>
                <% } %>
            </div>
        <% } %>

        <!-- ════════════════════════════════════════════════════════════ -->
        <!-- CREATE NEW COURSE FORM                                       -->
        <!-- ════════════════════════════════════════════════════════════ -->
        <section id="newCourseCard" class="glass-card" style="max-width: 720px; margin: 0 auto 2.5rem auto; border-radius: 16px;">
            <div style="border-bottom: 1.5px solid var(--border-subtle); padding-bottom: 0.75rem; margin-bottom: 1.25rem;">
                <h2 style="font-size: 1.25rem; font-weight: 700; color: #582c83; display: flex; align-items: center; gap: 0.5rem;">
                    <svg class="ui-icon"><use href="#icon-plus"></use></svg>
                    <span>Create New Academic Course</span>
                </h2>
                <p style="font-size: 0.85rem; color: var(--text-muted); margin-top: 0.2rem;">
                    Course will be assigned to your faculty profile with full ownership.
                </p>
            </div>

            <form method="POST" action="${pageContext.request.contextPath}/teacher/courses/create">
                <div class="grid-cols-2" style="gap: 1rem; margin-bottom: 1rem;">
                    <div class="form-group" style="margin-bottom: 0;">
                        <label class="form-label">Course Code (e.g. CS601)</label>
                        <input type="text" name="courseCode" placeholder="CS601" class="form-input" style="text-transform: uppercase;" required>
                    </div>
                    <div class="form-group" style="margin-bottom: 0;">
                        <label class="form-label">Semester (1–8)</label>
                        <input type="number" name="semester" min="1" max="8" value="5" class="form-input" required>
                    </div>
                </div>

                <div class="form-group">
                    <label class="form-label">Course Title</label>
                    <input type="text" name="courseName" placeholder="e.g. Distributed Cloud Computing" class="form-input" required>
                </div>

                <div class="form-group">
                    <label class="form-label">Academic Department</label>
                    <input type="text" name="department" value="<%= teacher != null ? teacher.getDepartment() : "Computer Science" %>" class="form-input" required>
                </div>

                <button type="submit" class="btn btn-primary btn-lg" style="width: 100%;">
                    <span>Create Course & Assign to My Profile</span>
                </button>
            </form>
        </section>
    </main>

    <footer class="footer">
        <p>ScanIn Smart Attendance System &bull; Faculty Course Management &bull; College DBMS Project</p>
    </footer>

</body>
</html>
