<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.attendance.model.Course" %>
<%@ page import="com.attendance.model.CourseSchedule" %>
<%@ page import="com.attendance.model.PlannedSession" %>
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
    List<PlannedSession> plannedSessions = (List<PlannedSession>) request.getAttribute("plannedSessions");
    String csrfToken = (String) request.getAttribute("csrfToken");

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
        <% } else if ("planned_session_created".equals(msg)) { %>
            <div class="alert alert-success">
                <span>✅</span>
                <span>Planned academic session successfully scheduled!</span>
            </div>
        <% } else if ("block_created".equals(msg)) { %>
            <div class="alert alert-success">
                <span>✅</span>
                <span>Consecutive academic block (multiple sessions) created successfully!</span>
            </div>
        <% } else if ("session_closed".equals(msg)) { %>
            <div class="alert alert-info">
                <span>ℹ️</span>
                <span>Attendance session ended. Absentee records reconciled successfully.</span>
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
        <!-- PLANNED ACADEMIC SESSIONS & TIMETABLE                        -->
        <!-- ════════════════════════════════════════════════════════════ -->
        <section class="glass-card" style="margin-bottom: 2.5rem; padding: 1.75rem;">
            <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1.25rem; flex-wrap: wrap; gap: 0.75rem;">
                <div>
                    <h2 style="font-size: 1.35rem; font-weight: 700; color: #582c83; margin: 0 0 0.25rem 0;">
                        🗓️ Planned Academic Sessions & Attendance
                    </h2>
                    <p style="font-size: 0.85rem; color: var(--text-muted); margin: 0;">
                        Schedule standalone lectures, multi-period consecutive blocks, and activate dynamic attendance QR sessions.
                    </p>
                </div>
                <div style="display: flex; gap: 0.6rem; flex-wrap: wrap;">
                    <button type="button" class="btn btn-primary btn-sm" onclick="toggleForm('createSessionCard')">
                        + Schedule Single Session
                    </button>
                    <button type="button" class="btn btn-secondary btn-sm" onclick="toggleForm('createBlockCard')">
                        + Schedule Consecutive Block (2–4 Periods)
                    </button>
                </div>
            </div>

            <!-- Single Standalone Planned Session Form (Collapsible) -->
            <div id="createSessionCard" style="display: none; background: var(--bg-subtle); border: 1.5px solid var(--border-subtle); border-radius: var(--radius-md); padding: 1.25rem; margin-bottom: 1.5rem;">
                <h3 style="font-size: 1.05rem; font-weight: 700; color: #582c83; margin-top: 0; margin-bottom: 0.8rem;">
                    ➕ Schedule Standalone Academic Session
                </h3>
                <form method="POST" action="${pageContext.request.contextPath}/teacher/planned-sessions/create" onsubmit="return handleFormSubmit(this);">
                    <input type="hidden" name="csrfToken" value="<%= csrfToken != null ? csrfToken : "" %>">
                    <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 0.75rem; margin-bottom: 0.75rem;">
                        <div>
                            <label class="form-label" style="font-size: 0.78rem;">Select Course *</label>
                            <select name="courseId" class="form-input" style="padding: 0.45rem;" required>
                                <option value="">-- Choose Course --</option>
                                <% if (courses != null) { for (Course c : courses) { %>
                                    <option value="<%= c.getId() %>"><%= c.getCourseCode() %> - <%= c.getCourseName() %></option>
                                <% } } %>
                            </select>
                        </div>
                        <div>
                            <label class="form-label" style="font-size: 0.78rem;">Session Type *</label>
                            <select name="sessionType" class="form-input" style="padding: 0.45rem;" required>
                                <option value="LECTURE">Lecture</option>
                                <option value="LAB">Laboratory</option>
                                <option value="TUTORIAL">Tutorial</option>
                                <option value="SEMINAR">Seminar</option>
                            </select>
                        </div>
                        <div>
                            <label class="form-label" style="font-size: 0.78rem;">Session Date *</label>
                            <input type="date" name="sessionDate" class="form-input" style="padding: 0.45rem;" required>
                        </div>
                        <div>
                            <label class="form-label" style="font-size: 0.78rem;">Room / Location</label>
                            <input type="text" name="room" placeholder="e.g. Hall 301 / Lab 2" class="form-input" style="padding: 0.45rem;">
                        </div>
                    </div>

                    <div style="display: grid; grid-template-columns: 1fr 1fr 2fr; gap: 0.75rem; margin-bottom: 1rem;">
                        <div>
                            <label class="form-label" style="font-size: 0.78rem;">Start Time (HH:mm) *</label>
                            <input type="time" name="startTime" class="form-input" style="padding: 0.45rem;" required>
                        </div>
                        <div>
                            <label class="form-label" style="font-size: 0.78rem;">End Time (HH:mm) *</label>
                            <input type="time" name="endTime" class="form-input" style="padding: 0.45rem;" required>
                        </div>
                        <div>
                            <label class="form-label" style="font-size: 0.78rem;">Topic / Description (Optional)</label>
                            <input type="text" name="description" placeholder="e.g. Chapter 4: Concurrency & Locks" class="form-input" style="padding: 0.45rem;">
                        </div>
                    </div>

                    <div style="display: flex; justify-content: flex-end; gap: 0.5rem;">
                        <button type="button" class="btn btn-secondary btn-sm" onclick="toggleForm('createSessionCard')">Cancel</button>
                        <button type="submit" class="btn btn-primary btn-sm">Save Planned Session</button>
                    </div>
                </form>
            </div>

            <!-- Consecutive Academic Block Form (Collapsible) -->
            <div id="createBlockCard" style="display: none; background: #faf5ff; border: 1.5px solid #d8b4fe; border-radius: var(--radius-md); padding: 1.25rem; margin-bottom: 1.5rem;">
                <h3 style="font-size: 1.05rem; font-weight: 700; color: #582c83; margin-top: 0; margin-bottom: 0.8rem;">
                    🔗 Schedule Consecutive Academic Block (2–4 Continuous Sessions)
                </h3>
                <form method="POST" action="${pageContext.request.contextPath}/teacher/planned-sessions/create-block" onsubmit="return handleFormSubmit(this);">
                    <input type="hidden" name="csrfToken" value="<%= csrfToken != null ? csrfToken : "" %>">
                    
                    <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 0.75rem; margin-bottom: 0.75rem;">
                        <div>
                            <label class="form-label" style="font-size: 0.78rem;">Select Course *</label>
                            <select name="courseId" class="form-input" style="padding: 0.45rem;" required>
                                <option value="">-- Choose Course --</option>
                                <% if (courses != null) { for (Course c : courses) { %>
                                    <option value="<%= c.getId() %>"><%= c.getCourseCode() %> - <%= c.getCourseName() %></option>
                                <% } } %>
                            </select>
                        </div>
                        <div>
                            <label class="form-label" style="font-size: 0.78rem;">Session Type *</label>
                            <select name="sessionType" class="form-input" style="padding: 0.45rem;" required>
                                <option value="LAB">Laboratory Block</option>
                                <option value="LECTURE">Extended Lecture Block</option>
                                <option value="WORKSHOP">Workshop / Seminar</option>
                            </select>
                        </div>
                        <div>
                            <label class="form-label" style="font-size: 0.78rem;">Session Date *</label>
                            <input type="date" name="sessionDate" class="form-input" style="padding: 0.45rem;" required>
                        </div>
                        <div>
                            <label class="form-label" style="font-size: 0.78rem;">Room / Location</label>
                            <input type="text" name="room" placeholder="e.g. Systems Lab 1" class="form-input" style="padding: 0.45rem;">
                        </div>
                    </div>

                    <div style="display: grid; grid-template-columns: 1fr 2fr; gap: 0.75rem; margin-bottom: 1rem;">
                        <div>
                            <label class="form-label" style="font-size: 0.78rem;">Consecutive Periods *</label>
                            <select id="blockCountSelect" name="blockCount" class="form-input" style="padding: 0.45rem;" onchange="updateBlockPeriodInputs(this.value)">
                                <option value="2">2 Periods (Double Block)</option>
                                <option value="3">3 Periods (Triple Block)</option>
                                <option value="4">4 Periods (Quad Block)</option>
                            </select>
                        </div>
                        <div>
                            <label class="form-label" style="font-size: 0.78rem;">Block Description</label>
                            <input type="text" name="description" placeholder="e.g. Lab Exercise 5: Transaction Isolation Levels" class="form-input" style="padding: 0.45rem;">
                        </div>
                    </div>

                    <!-- Continuous Period Inputs -->
                    <div style="background: #ffffff; border: 1px solid #e9d5ff; border-radius: var(--radius-sm); padding: 0.85rem; margin-bottom: 1rem;">
                        <div style="font-size: 0.78rem; font-weight: 700; color: #6b21a8; margin-bottom: 0.5rem; text-transform: uppercase;">
                            ⏱️ Continuous Period Times (Must be strictly contiguous without gaps)
                        </div>
                        <div id="blockPeriodsContainer" style="display: grid; gap: 0.5rem;">
                            <!-- Period 1 -->
                            <div style="display: flex; gap: 0.5rem; align-items: center;">
                                <span style="font-size: 0.82rem; font-weight: 600; min-width: 80px;">Period 1:</span>
                                <input type="time" name="startTime_1" value="09:00" class="form-input" style="padding: 0.35rem;" required>
                                <span>to</span>
                                <input type="time" name="endTime_1" value="10:00" class="form-input" style="padding: 0.35rem;" required>
                            </div>
                            <!-- Period 2 -->
                            <div style="display: flex; gap: 0.5rem; align-items: center;">
                                <span style="font-size: 0.82rem; font-weight: 600; min-width: 80px;">Period 2:</span>
                                <input type="time" name="startTime_2" value="10:00" class="form-input" style="padding: 0.35rem;" required>
                                <span>to</span>
                                <input type="time" name="endTime_2" value="11:00" class="form-input" style="padding: 0.35rem;" required>
                            </div>
                            <!-- Period 3 (hidden by default) -->
                            <div id="periodRow_3" style="display: none; gap: 0.5rem; align-items: center;">
                                <span style="font-size: 0.82rem; font-weight: 600; min-width: 80px;">Period 3:</span>
                                <input type="time" name="startTime_3" value="11:00" class="form-input" style="padding: 0.35rem;">
                                <span>to</span>
                                <input type="time" name="endTime_3" value="12:00" class="form-input" style="padding: 0.35rem;">
                            </div>
                            <!-- Period 4 (hidden by default) -->
                            <div id="periodRow_4" style="display: none; gap: 0.5rem; align-items: center;">
                                <span style="font-size: 0.82rem; font-weight: 600; min-width: 80px;">Period 4:</span>
                                <input type="time" name="startTime_4" value="12:00" class="form-input" style="padding: 0.35rem;">
                                <span>to</span>
                                <input type="time" name="endTime_4" value="13:00" class="form-input" style="padding: 0.35rem;">
                            </div>
                        </div>
                    </div>

                    <div style="display: flex; justify-content: flex-end; gap: 0.5rem;">
                        <button type="button" class="btn btn-secondary btn-sm" onclick="toggleForm('createBlockCard')">Cancel</button>
                        <button type="submit" class="btn btn-primary btn-sm">Create Consecutive Block</button>
                    </div>
                </form>
            </div>

            <!-- Planned Sessions List Table -->
            <% if (plannedSessions == null || plannedSessions.isEmpty()) { %>
                <div style="text-align: center; padding: 2rem 1rem; border: 1.5px dashed var(--border-subtle); border-radius: var(--radius-md); background: #fafafa;">
                    <div style="font-size: 2rem; margin-bottom: 0.4rem;">📅</div>
                    <h4 style="font-size: 1rem; font-weight: 700; color: #1e293b; margin: 0 0 0.2rem 0;">No Planned Sessions Scheduled</h4>
                    <p style="color: var(--text-muted); font-size: 0.85rem; max-width: 440px; margin: 0 auto 1rem auto;">
                        You have not scheduled any academic sessions yet. Use the buttons above to plan your upcoming lectures and lab sessions.
                    </p>
                </div>
            <% } else { %>
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse; font-size: 0.88rem; text-align: left;">
                        <thead>
                            <tr style="border-bottom: 2px solid var(--border-subtle); color: #475569; font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.04em;">
                                <th style="padding: 0.6rem 0.75rem;">Date & Time</th>
                                <th style="padding: 0.6rem 0.75rem;">Course</th>
                                <th style="padding: 0.6rem 0.75rem;">Type & Room</th>
                                <th style="padding: 0.6rem 0.75rem;">Block</th>
                                <th style="padding: 0.6rem 0.75rem;">Status</th>
                                <th style="padding: 0.6rem 0.75rem; text-align: right;">Action</th>
                            </tr>
                        </thead>
                        <tbody>
                            <% for (PlannedSession ps : plannedSessions) { 
                                String status = ps.getStatus() != null ? ps.getStatus() : "SCHEDULED";
                            %>
                                <tr style="border-bottom: 1px solid var(--border-subtle); transition: background 0.15s ease;">
                                    <td style="padding: 0.75rem; white-space: nowrap;">
                                        <strong><%= ps.getSessionDate() %></strong><br>
                                        <span style="font-size: 0.8rem; color: var(--text-muted);"><%= ps.getStartTime() %> – <%= ps.getEndTime() %></span>
                                    </td>
                                    <td style="padding: 0.75rem;">
                                        <span class="badge" style="background: var(--purple-subtle); color: #582c83; font-size: 0.75rem; padding: 0.2rem 0.5rem;">
                                            <%= ps.getCourseCode() != null ? ps.getCourseCode() : ("Course #" + ps.getCourseId()) %>
                                        </span>
                                        <div style="font-size: 0.82rem; font-weight: 600; color: #1e293b; margin-top: 0.2rem;">
                                            <%= ps.getCourseName() != null ? ps.getCourseName() : "" %>
                                        </div>
                                    </td>
                                    <td style="padding: 0.75rem;">
                                        <span style="font-weight: 600;"><%= ps.getSessionType() %></span><br>
                                        <span style="font-size: 0.8rem; color: var(--text-muted);"><%= ps.getRoom() != null ? ps.getRoom() : "TBD" %></span>
                                    </td>
                                    <td style="padding: 0.75rem;">
                                        <% if (ps.getBlockId() != null) { %>
                                            <span class="badge" style="background: #f3e8ff; color: #7e22ce; border: 1px solid #e9d5ff; font-size: 0.72rem; padding: 0.2rem 0.45rem;" title="Block UUID: <%= ps.getBlockId() %>">
                                                🔗 Block
                                            </span>
                                        <% } else { %>
                                            <span style="font-size: 0.78rem; color: var(--text-muted);">Standalone</span>
                                        <% } %>
                                    </td>
                                    <td style="padding: 0.75rem;">
                                        <% if ("SCHEDULED".equals(status)) { %>
                                            <span class="badge" style="background: #e0e7ff; color: #3730a3; border: 1px solid #c7d2fe; font-size: 0.75rem;">
                                                🗓️ SCHEDULED
                                            </span>
                                        <% } else if ("ATTENDANCE_OPEN".equals(status)) { %>
                                            <span class="badge" style="background: #dcfce7; color: #166534; border: 1px solid #bbf7d0; font-size: 0.75rem; display: inline-flex; align-items: center; gap: 0.3rem;">
                                                <span class="radar-dot" style="width: 6px; height: 6px;"></span> LIVE / OPEN
                                            </span>
                                        <% } else if ("COMPLETED".equals(status)) { %>
                                            <span class="badge" style="background: #f1f5f9; color: #475569; border: 1px solid #cbd5e1; font-size: 0.75rem;">
                                                ✅ COMPLETED
                                            </span>
                                        <% } else if ("CANCELLED".equals(status)) { %>
                                            <span class="badge" style="background: #fee2e2; color: #991b1b; border: 1px solid #fecaca; font-size: 0.75rem;">
                                                🚫 CANCELLED
                                            </span>
                                        <% } else if ("MISSED".equals(status)) { %>
                                            <span class="badge" style="background: #fef3c7; color: #92400e; border: 1px solid #fde68a; font-size: 0.75rem;">
                                                ⚠️ MISSED
                                            </span>
                                        <% } else { %>
                                            <span class="badge" style="font-size: 0.75rem;"><%= status %></span>
                                        <% } %>
                                    </td>
                                    <td style="padding: 0.75rem; text-align: right; white-space: nowrap;">
                                        <button type="button" 
                                                class="btn btn-secondary btn-sm btn-roster-view" 
                                                data-session-id="<%= ps.getId() %>"
                                                onclick="openSessionRoster(<%= ps.getId() %>, this)" 
                                                style="padding: 0.35rem 0.65rem; font-size: 0.8rem; margin-right: 0.35rem;" 
                                                title="View Attendance Roster">
                                            👥 Roster
                                        </button>
                                        <% if ("SCHEDULED".equals(status)) { %>
                                            <form method="POST" action="${pageContext.request.contextPath}/qr/activate" style="display:inline; margin:0;" onsubmit="return handleActivateSubmit(this);">
                                                <input type="hidden" name="plannedSessionId" value="<%= ps.getId() %>">
                                                <input type="hidden" name="csrfToken" value="<%= csrfToken != null ? csrfToken : "" %>">
                                                <input type="hidden" name="redirect" value="true">
                                                <button type="submit" class="btn btn-primary btn-sm" style="padding: 0.35rem 0.75rem; font-size: 0.8rem;">
                                                    ⚡ Start Attendance
                                                </button>
                                            </form>
                                        <% } else if ("ATTENDANCE_OPEN".equals(status)) { %>
                                            <a href="${pageContext.request.contextPath}/qr/generate?courseId=<%= ps.getCourseId() %>&plannedSessionId=<%= ps.getId() %>" class="btn btn-sm" style="background: #16a34a; color: #fff; padding: 0.35rem 0.75rem; font-size: 0.8rem;">
                                                📱 View Live QR
                                            </a>
                                        <% } else { %>
                                            <a href="${pageContext.request.contextPath}/qr/generate?courseId=<%= ps.getCourseId() %>&plannedSessionId=<%= ps.getId() %>" class="btn btn-secondary btn-sm" style="padding: 0.35rem 0.75rem; font-size: 0.8rem;">
                                                📋 Summary
                                            </a>
                                        <% } %>
                                    </td>
                                </tr>
                            <% } %>
                        </tbody>
                    </table>
                </div>
            <% } %>
        </section>

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

        <!-- ════════════════════════════════════════════════════════════ -->
        <!-- PLANNED SESSION ATTENDANCE ROSTER MODAL (Stage 13-C4.4)      -->
        <!-- ════════════════════════════════════════════════════════════ -->
        <div id="sessionRosterModal" class="modal-backdrop" style="display: none;" role="dialog" aria-modal="true" aria-labelledby="rosterCourseName">
            <div class="modal-container" tabindex="-1">
                <!-- Modal Header -->
                <div class="modal-header">
                    <div>
                        <div style="display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap; margin-bottom: 0.35rem;">
                            <span id="rosterCourseCode" class="badge" style="background: var(--purple-subtle); color: #582c83; font-size: 0.85rem; padding: 0.25rem 0.65rem; font-weight: 700;">
                                Loading...
                            </span>
                            <span id="rosterStatusBadge" class="badge" style="font-size: 0.75rem;">
                                SCHEDULED
                            </span>
                            <span id="rosterInProgressBadge" class="badge badge-status-present" style="display: none; font-size: 0.75rem;">
                                ● LIVE / IN PROGRESS
                            </span>
                        </div>
                        <h2 id="rosterCourseName" style="font-size: 1.25rem; font-weight: 700; color: #0f172a; margin: 0 0 0.25rem 0;">
                            Loading Session Roster...
                        </h2>
                        <div id="rosterSessionMeta" style="font-size: 0.82rem; color: var(--text-muted);">
                            Fetching session details...
                        </div>
                    </div>
                    <button type="button" 
                            id="rosterCloseHeaderBtn" 
                            class="btn btn-secondary btn-sm" 
                            onclick="closeSessionRoster()" 
                            style="padding: 0.4rem 0.75rem; font-size: 0.9rem; line-height: 1;" 
                            aria-label="Close modal"
                            title="Close">
                        ✕
                    </button>
                </div>

                <!-- Modal Body -->
                <div class="modal-body">
                    <!-- Loading State -->
                    <div id="rosterLoadingIndicator" style="display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 3rem 1rem;">
                        <div class="brand-icon" style="font-size: 2rem; background: transparent; width: 48px; height: 48px; margin-bottom: 1rem;">
                            ⌛
                        </div>
                        <div style="font-weight: 600; color: #582c83; font-size: 1rem;">
                            Loading Attendance Roster...
                        </div>
                        <div style="font-size: 0.82rem; color: var(--text-muted); margin-top: 0.3rem;">
                            Retrieving enrolled students and persisted session attendance.
                        </div>
                    </div>

                    <!-- Error State -->
                    <div id="rosterErrorContainer" style="display: none; padding: 2rem 1rem; text-align: center;">
                        <div style="font-size: 2.5rem; margin-bottom: 0.5rem;">⚠️</div>
                        <h3 style="font-size: 1.15rem; font-weight: 700; color: #991b1b; margin-bottom: 0.4rem;">
                            Unable to Load Roster
                        </h3>
                        <p id="rosterErrorText" style="color: var(--text-secondary); font-size: 0.9rem; max-width: 500px; margin: 0 auto 1.25rem auto;">
                            An error occurred while retrieving the session roster.
                        </p>
                        <button type="button" id="rosterRetryBtn" class="btn btn-primary btn-sm">
                            🔄 Retry Loading
                        </button>
                    </div>

                    <!-- Content Area -->
                    <div id="rosterContent" style="display: none;">
                        <!-- Inconsistency Alert Banner -->
                        <div id="rosterInconsistencyBanner" class="inconsistency-banner" style="display: none;">
                            <span style="font-size: 1.25rem;">⚠️</span>
                            <div id="rosterInconsistencyText" style="flex: 1;">
                                Attendance inconsistencies detected.
                            </div>
                        </div>

                        <!-- Metrics Grid -->
                        <div class="roster-metrics-grid">
                            <div class="roster-metric-card">
                                <div id="metricTotalEnrolled" class="roster-metric-val">0</div>
                                <div class="roster-metric-label">Enrolled</div>
                            </div>
                            <div class="roster-metric-card" style="border-top: 3px solid #16a34a;">
                                <div id="metricPresent" class="roster-metric-val" style="color: #15803d;">0</div>
                                <div class="roster-metric-label">Present</div>
                            </div>
                            <div class="roster-metric-card" style="border-top: 3px solid #ca8a04;">
                                <div id="metricLate" class="roster-metric-val" style="color: #a16207;">0</div>
                                <div class="roster-metric-label">Late</div>
                            </div>
                            <div class="roster-metric-card" style="border-top: 3px solid #dc2626;">
                                <div id="metricAbsent" class="roster-metric-val" style="color: #b91c1c;">0</div>
                                <div class="roster-metric-label">Absent</div>
                            </div>
                            <div class="roster-metric-card" style="border-top: 3px solid #94a3b8;">
                                <div id="metricNotRecorded" class="roster-metric-val" style="color: #475569;">0</div>
                                <div class="roster-metric-label">Unmarked</div>
                            </div>
                            <div class="roster-metric-card" style="border-top: 3px solid #ea580c;">
                                <div id="metricInconsistent" class="roster-metric-val" style="color: #c2410c;">0</div>
                                <div class="roster-metric-label">Inconsistent</div>
                            </div>
                            <div class="roster-metric-card" style="border-top: 3px solid #582c83; background: #faf5ff;">
                                <div id="metricPercentage" class="roster-metric-val" style="color: #582c83;">N/A</div>
                                <div class="roster-metric-label">Attendance %</div>
                            </div>
                        </div>

                        <!-- Empty Roster State -->
                        <div id="rosterEmptyState" style="display: none; text-align: center; padding: 2.5rem 1rem; background: var(--bg-subtle); border-radius: var(--radius-sm); border: 1.5px dashed var(--border-subtle);">
                            <div style="font-size: 2.2rem; margin-bottom: 0.4rem;">👨‍🎓</div>
                            <h4 style="font-size: 1rem; font-weight: 700; color: #1e293b; margin-bottom: 0.2rem;">
                                No Enrolled Students
                            </h4>
                            <p style="color: var(--text-muted); font-size: 0.85rem; max-width: 400px; margin: 0 auto;">
                                No students are registered for this course yet. Once students enroll, their names and attendance will appear here.
                            </p>
                        </div>

                        <!-- Roster Table -->
                        <div id="rosterTableContainer" class="table-responsive" style="border: 1px solid var(--border-subtle); border-radius: var(--radius-sm); max-height: 400px; overflow-y: auto;">
                            <table class="data-table" style="width: 100%; border-collapse: collapse;">
                                <thead style="position: sticky; top: 0; background: var(--bg-subtle); z-index: 10; border-bottom: 2px solid var(--border-subtle);">
                                    <tr>
                                        <th style="padding: 0.75rem; width: 60px; text-align: center;">S.No.</th>
                                        <th style="padding: 0.75rem; width: 140px;">Register Number</th>
                                        <th style="padding: 0.75rem;">Student Name</th>
                                        <th style="padding: 0.75rem; width: 180px;">Attendance</th>
                                    </tr>
                                </thead>
                                <tbody id="rosterTableBody">
                                    <!-- Populated dynamically and safely via session-roster.js -->
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>

                <!-- Modal Footer -->
                <div class="modal-footer">
                    <div style="display: flex; align-items: center; gap: 0.75rem;">
                        <button type="button" id="rosterRefreshBtn" class="btn btn-secondary btn-sm" onclick="refreshCurrentSessionRoster()">
                            🔄 Refresh Roster
                        </button>
                        <span id="rosterLastRefreshed" style="font-size: 0.78rem; color: var(--text-muted);">
                            Last refreshed: —
                        </span>
                    </div>
                    <button type="button" id="rosterCloseFooterBtn" class="btn btn-primary btn-sm" onclick="closeSessionRoster()">
                        Close
                    </button>
                </div>
            </div>
        </div>
    </main>

    <footer class="footer">
        <p>ScanIn Smart Attendance System • Faculty Course Management • College DBMS Project</p>
    </footer>

    <script>
        window.SCANIN_CONTEXT_PATH = '${pageContext.request.contextPath}';
    </script>
    <script src="${pageContext.request.contextPath}/js/session-roster.js"></script>

    <script>
        function toggleForm(elementId) {
            const el = document.getElementById(elementId);
            if (!el) return;
            if (el.style.display === "none" || el.style.display === "") {
                el.style.display = "block";
                el.scrollIntoView({ behavior: "smooth", block: "start" });
            } else {
                el.style.display = "none";
            }
        }

        function updateBlockPeriodInputs(count) {
            const c = parseInt(count, 10);
            const row3 = document.getElementById("periodRow_3");
            const row4 = document.getElementById("periodRow_4");
            const st3 = document.querySelector('input[name="startTime_3"]');
            const et3 = document.querySelector('input[name="endTime_3"]');
            const st4 = document.querySelector('input[name="startTime_4"]');
            const et4 = document.querySelector('input[name="endTime_4"]');

            if (c >= 3) {
                if (row3) row3.style.display = "flex";
                if (st3) st3.required = true;
                if (et3) et3.required = true;
            } else {
                if (row3) row3.style.display = "none";
                if (st3) st3.required = false;
                if (et3) et3.required = false;
            }

            if (c >= 4) {
                if (row4) row4.style.display = "flex";
                if (st4) st4.required = true;
                if (et4) et4.required = true;
            } else {
                if (row4) row4.style.display = "none";
                if (st4) st4.required = false;
                if (et4) et4.required = false;
            }
        }

        // Prevent duplicate form submissions
        function handleFormSubmit(form) {
            const btn = form.querySelector('button[type="submit"]');
            if (btn) {
                btn.disabled = true;
                btn.innerText = "Processing...";
            }
            return true;
        }

        function handleActivateSubmit(form) {
            const btn = form.querySelector('button[type="submit"]');
            if (btn) {
                btn.disabled = true;
                btn.innerText = "Activating...";
            }
            return true;
        }
    </script>
</body>
</html>
