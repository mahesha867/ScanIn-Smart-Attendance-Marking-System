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

    <!-- Header Navigation -->
    <nav class="navbar">
        <a href="${pageContext.request.contextPath}/teacher/dashboard" class="nav-brand">
            <div class="brand-icon">👨‍🏫</div>
            <span>ScanIn Faculty Portal</span>
        </a>
        <div class="nav-actions">
            <span class="user-badge">
                <span class="role-pill teacher">FACULTY</span>
                <span><%= user != null ? user.getName() : "Faculty" %></span>
            </span>
            <a href="${pageContext.request.contextPath}/logout" class="btn btn-secondary btn-sm">Sign Out</a>
        </div>
    </nav>

    <main class="container">
        <!-- Message Banners -->
        <% if ("course_created".equals(msg)) { %>
            <div class="alert alert-success">
                <span>✅</span>
                <span>Course successfully created and added to your assigned courses!</span>
            </div>
        <% } else if ("course_updated".equals(msg)) { %>
            <div class="alert alert-success">
                <span>✅</span>
                <span>Course details updated successfully.</span>
            </div>
        <% } else if ("course_deleted".equals(msg)) { %>
            <div class="alert alert-info">
                <span>ℹ️</span>
                <span>Course successfully deleted from system.</span>
            </div>
        <% } else if ("schedule_added".equals(msg)) { %>
            <div class="alert alert-success">
                <span>✅</span>
                <span>Lecture timeslot successfully scheduled.</span>
            </div>
        <% } else if ("schedule_deleted".equals(msg)) { %>
            <div class="alert alert-info">
                <span>ℹ️</span>
                <span>Lecture timeslot removed from timetable.</span>
            </div>
        <% } %>

        <% if (error != null) { %>
            <div class="alert alert-danger">
                <span>⚠️</span>
                <span><%= error.replace("+", " ") %></span>
            </div>
        <% } %>

        <header class="page-header">
            <div>
                <h1 class="page-title">Course & Semester Management</h1>
                <p class="page-subtitle">
                    <%= teacher != null ? teacher.getName() + " • " + teacher.getDepartment() + " • Emp ID: " + teacher.getEmployeeId() : "Faculty Portal" %>
                </p>
            </div>
            <div style="display: flex; gap: 0.75rem;">
                <a href="${pageContext.request.contextPath}/reports" class="btn btn-secondary">
                    📊 Attendance Reports
                </a>
                <button type="button" class="btn btn-primary" onclick="document.getElementById('newCourseCard').scrollIntoView({behavior: 'smooth'});">
                    + Add New Course
                </button>
            </div>
        </header>

        <!-- Stats Overview -->
        <div class="grid-cols-3" style="margin-bottom: 2.2rem;">
            <div class="stat-card">
                <div class="stat-icon">📚</div>
                <div>
                    <div class="stat-val"><%= courses != null ? courses.size() : 0 %></div>
                    <div class="stat-label">Faculty-Owned Courses</div>
                </div>
            </div>
            <div class="stat-card">
                <div class="stat-icon emerald">👥</div>
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
                <div class="stat-icon cyan">🗓️</div>
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
            <h2 style="font-size: 1.35rem; font-weight: 700; color: #582c83;">
                Assigned Courses & Rosters
            </h2>
            <span style="font-size: 0.85rem; color: var(--text-muted);">Owned & Managed by You</span>
        </div>

        <% if (courses == null || courses.isEmpty()) { %>
            <div class="glass-card" style="text-align: center; padding: 3rem 1rem; border: 1.5px dashed var(--border-subtle); margin-bottom: 2.5rem;">
                <div style="font-size: 2.5rem; margin-bottom: 0.5rem;">📖</div>
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
                                    Semester <%= c.getSemester() %> • <%= c.getDepartment() %>
                                </span>
                            </div>

                            <h3 style="font-size: 1.25rem; font-weight: 700; color: #0f172a; margin-bottom: 0.3rem;">
                                <%= c.getCourseName() %>
                            </h3>
                            <div style="font-size: 0.85rem; color: var(--text-muted); margin-bottom: 1.25rem;">
                                👥 <%= studentCount %> Enrolled Student<%= studentCount == 1 ? "" : "s" %>
                            </div>

                            <!-- Planned Lecture Timetable for this course -->
                            <div style="background: var(--bg-subtle); border-radius: var(--radius-sm); padding: 0.85rem 1rem; margin-bottom: 1.25rem;">
                                <div style="font-size: 0.78rem; font-weight: 700; text-transform: uppercase; color: #582c83; margin-bottom: 0.4rem; letter-spacing: 0.04em;">
                                    🗓️ Planned Lecture Schedule
                                </div>
                                <% if (schedules == null || schedules.isEmpty()) { %>
                                    <div style="font-size: 0.82rem; color: var(--text-muted); font-style: italic;">
                                        No lecture timeslots scheduled yet.
                                    </div>
                                <% } else { %>
                                    <ul style="list-style: none; padding: 0; margin: 0; font-size: 0.84rem;">
                                        <% for (CourseSchedule cs : schedules) { %>
                                            <li style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.3rem;">
                                                <span><strong><%= cs.getDayOfWeek() %>:</strong> <%= cs.getStartTime() %> - <%= cs.getEndTime() %> (<%= cs.getRoom() %>)</span>
                                                <form method="POST" action="${pageContext.request.contextPath}/teacher/schedules/delete" style="display: inline;" onsubmit="return confirm('Remove this timeslot?');">
                                                    <input type="hidden" name="scheduleId" value="<%= cs.getId() %>">
                                                    <button type="submit" style="background: none; border: none; color: #582c83; cursor: pointer; font-size: 0.75rem;">✕</button>
                                                </form>
                                            </li>
                                        <% } %>
                                    </ul>
                                <% } %>

                                <!-- Quick Add Schedule Form -->
                                <details style="margin-top: 0.6rem;">
                                    <summary style="font-size: 0.8rem; font-weight: 600; color: #582c83; cursor: pointer;">+ Add Timeslot</summary>
                                    <form method="POST" action="${pageContext.request.contextPath}/teacher/schedules/create" style="margin-top: 0.5rem; display: grid; grid-template-columns: 1fr 1fr; gap: 0.4rem;">
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
                                        <button type="submit" class="btn btn-primary btn-sm" style="grid-column: span 2; padding: 0.35rem;">Save Schedule</button>
                                    </form>
                                </details>
                            </div>
                        </div>

                        <!-- Actions Grid -->
                        <div style="display: flex; gap: 0.6rem; flex-wrap: wrap; border-top: 1px solid var(--border-subtle); padding-top: 1rem;">
                            <a href="${pageContext.request.contextPath}/qr/generate?courseId=<%= c.getId() %>" class="btn btn-primary btn-sm" style="flex: 1;">
                                📱 Start Live QR
                            </a>
                            <a href="${pageContext.request.contextPath}/teacher/courses/roster?courseId=<%= c.getId() %>" class="btn btn-secondary btn-sm">
                                👥 Roster (<%= studentCount %>)
                            </a>
                            <details style="position: relative;">
                                <summary class="btn btn-secondary btn-sm" style="cursor: pointer;">⚙️</summary>
                                <div style="position: absolute; right: 0; bottom: 100%; margin-bottom: 0.5rem; background: #ffffff; border: 1.5px solid var(--border-subtle); border-radius: var(--radius-sm); padding: 1rem; width: 280px; box-shadow: var(--shadow-lg); z-index: 50;">
                                    <h4 style="font-size: 0.9rem; font-weight: 700; color: #582c83; margin-bottom: 0.6rem;">Edit Course Details</h4>
                                    <form method="POST" action="${pageContext.request.contextPath}/teacher/courses/edit">
                                        <input type="hidden" name="courseId" value="<%= c.getId() %>">
                                        <div style="margin-bottom: 0.4rem;">
                                            <label style="font-size: 0.75rem; font-weight: 600;">Title</label>
                                            <input type="text" name="courseName" value="<%= c.getCourseName() %>" class="form-input" style="padding: 0.35rem 0.6rem; font-size: 0.82rem;" required>
                                        </div>
                                        <div style="margin-bottom: 0.4rem;">
                                            <label style="font-size: 0.75rem; font-weight: 600;">Department</label>
                                            <input type="text" name="department" value="<%= c.getDepartment() %>" class="form-input" style="padding: 0.35rem 0.6rem; font-size: 0.82rem;" required>
                                        </div>
                                        <div style="margin-bottom: 0.6rem;">
                                            <label style="font-size: 0.75rem; font-weight: 600;">Semester</label>
                                            <input type="number" name="semester" min="1" max="8" value="<%= c.getSemester() %>" class="form-input" style="padding: 0.35rem 0.6rem; font-size: 0.82rem;" required>
                                        </div>
                                        <button type="submit" class="btn btn-primary btn-sm" style="width: 100%; margin-bottom: 0.5rem;">Update Course</button>
                                    </form>
                                    <form method="POST" action="${pageContext.request.contextPath}/teacher/courses/delete" onsubmit="return confirm('Are you sure you want to permanently delete this course? All associated sessions will be removed.');">
                                        <input type="hidden" name="courseId" value="<%= c.getId() %>">
                                        <button type="submit" class="btn btn-secondary btn-sm" style="width: 100%; color: #dc2626; border-color: #fca5a5;">Delete Course</button>
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
        <section id="newCourseCard" class="glass-card" style="max-width: 720px; margin: 0 auto 2.5rem auto;">
            <div style="border-bottom: 1.5px solid var(--border-subtle); padding-bottom: 0.75rem; margin-bottom: 1.25rem;">
                <h2 style="font-size: 1.25rem; font-weight: 700; color: #582c83;">
                    ➕ Create New Academic Course
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
                    Create Course & Assign to My Profile
                </button>
            </form>
        </section>
    </main>

    <footer class="footer">
        <p>ScanIn Smart Attendance System • Faculty Course Management • College DBMS Project</p>
    </footer>

</body>
</html>
