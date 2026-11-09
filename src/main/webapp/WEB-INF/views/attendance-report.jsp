<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.attendance.model.Attendance" %>
<%@ page import="com.attendance.model.AttendanceAuditLog" %>
<%@ page import="com.attendance.model.Course" %>
<%@ page import="com.attendance.model.QrSession" %>
<%@ page import="com.attendance.model.User" %>
<%@ page import="com.attendance.util.DateTimeUtil" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.util.List" %>
<%
    User user = (User) session.getAttribute("currentUser");
    List<Course> courses = (List<Course>) request.getAttribute("courses");
    Course selectedCourse = (Course) request.getAttribute("selectedCourse");
    List<Attendance> attendanceList = (List<Attendance>) request.getAttribute("attendanceList");
    List<AttendanceAuditLog> auditLogs = (List<AttendanceAuditLog>) request.getAttribute("auditLogs");
    Integer selectedCourseId = (Integer) request.getAttribute("selectedCourseId");
    List<QrSession> courseSessions = (List<QrSession>) request.getAttribute("courseSessions");
    Integer selectedSessionId = (Integer) request.getAttribute("selectedSessionId");
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    QrSession selectedSession = null;
    if (selectedSessionId != null && courseSessions != null) {
        for (QrSession qs : courseSessions) {
            if (qs.getId() == selectedSessionId) {
                selectedSession = qs;
                break;
            }
        }
    }

    int totalLogs = attendanceList != null ? attendanceList.size() : 0;
    int totalPresent = 0;
    int totalOd = 0;
    int totalLate = 0;
    int totalExcused = 0;
    int totalAbsent = 0;

    if (attendanceList != null) {
        for (Attendance a : attendanceList) {
            String st = a.getStatus() != null ? a.getStatus().toUpperCase() : "ABSENT";
            switch (st) {
                case "PRESENT": totalPresent++; break;
                case "OD": totalOd++; break;
                case "LATE": totalLate++; break;
                case "EXCUSED": totalExcused++; break;
                default: totalAbsent++; break;
            }
        }
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Attendance Reports & Audit History - ScanIn</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@500;700&display=swap" rel="stylesheet">
    <style>
        .badge-present { background: #dcfce7; color: #15803d; border: 1px solid #86efac; }
        .badge-absent  { background: #fee2e2; color: #b91c1c; border: 1px solid #fca5a5; }
        .badge-late    { background: #fef3c7; color: #b45309; border: 1px solid #fde68a; }
        .badge-od      { background: #f3e8ff; color: #6b21a8; border: 1px solid #d8b4fe; font-weight: 700; }
        .badge-excused { background: #e0f2fe; color: #0369a1; border: 1px solid #bae6fd; }

        .modal-overlay {
            display: none;
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: rgba(15, 23, 42, 0.65);
            backdrop-filter: blur(4px);
            z-index: 1000;
            align-items: center;
            justify-content: center;
        }
        .modal-card {
            background: #ffffff;
            border-radius: 18px;
            width: 100%;
            max-width: 480px;
            padding: 2rem;
            box-shadow: 0 20px 25px -5px rgba(0,0,0,0.25);
            border: 1px solid #e2e8f0;
        }

        /* Responsive 3-Column Integrated Filter Bar */
        .report-filter-bar {
            display: grid;
            grid-template-columns: minmax(240px, 1.35fr) minmax(210px, 1.15fr) minmax(220px, 1.2fr);
            gap: 1.15rem;
            align-items: center;
            width: 100%;
        }
        .filter-group {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            min-width: 0;
        }
        .filter-group label {
            font-weight: 700;
            font-size: 0.88rem;
            color: #334155;
            display: flex;
            align-items: center;
            gap: 0.35rem;
            white-space: nowrap;
        }
        .filter-group select {
            flex: 1;
            min-width: 0;
            padding: 0.62rem 0.85rem;
            text-overflow: ellipsis;
        }
        .filter-search {
            position: relative;
            display: flex;
            align-items: center;
            min-width: 0;
            width: 100%;
        }
        .filter-search input {
            width: 100%;
            padding: 0.62rem 0.85rem 0.62rem 2.35rem;
        }
        .filter-search .search-icon {
            position: absolute;
            left: 0.85rem;
            color: #94a3b8;
            pointer-events: none;
        }
        @media (max-width: 992px) {
            .report-filter-bar {
                grid-template-columns: 1fr 1fr;
            }
            .filter-search {
                grid-column: 1 / -1;
            }
        }
        @media (max-width: 640px) {
            .report-filter-bar {
                grid-template-columns: 1fr;
            }
        }
    </style>
</head>
<body>
    <%@ include file="common-icons.jspf" %>

    <nav class="navbar">
        <a href="${pageContext.request.contextPath}/teacher/dashboard" class="nav-brand">
            <div class="brand-icon">
                <svg class="ui-icon"><use href="#icon-scanin"></use></svg>
            </div>
            <span>ScanIn &bull; Reports & Audit Trail</span>
        </a>
        <div class="nav-actions">
            <a href="${pageContext.request.contextPath}/teacher/dashboard" class="btn btn-secondary btn-sm">
                <svg class="ui-icon ui-icon-xs"><use href="#icon-arrow-left"></use></svg>
                <span>Dashboard</span>
            </a>
            <% if (selectedCourseId != null && selectedCourseId > 0) { 
                String csvExportUrl = request.getContextPath() + "/reports?courseId=" + selectedCourseId + ((selectedSessionId != null && selectedSessionId > 0) ? "&sessionId=" + selectedSessionId : "") + "&format=csv";
            %>
                <a href="<%= csvExportUrl %>" class="btn btn-primary btn-sm" id="csvExportBtn">
                    <svg class="ui-icon ui-icon-xs"><use href="#icon-download"></use></svg>
                    <span>Export CSV</span>
                </a>
            <% } %>
        </div>
    </nav>

    <main class="container">
        <header class="page-header" style="margin-bottom: 1.5rem;">
            <div>
                <h1 class="page-title">Attendance Records & Audit History</h1>
                <p class="page-subtitle">Track live scans, manual overrides, OD approvals, and immutable audit logs</p>
            </div>
            <div style="display: flex; gap: 0.75rem; align-items: center;">
                <% if (selectedCourseId != null && selectedCourseId > 0) { 
                    String csvExportUrl = request.getContextPath() + "/reports?courseId=" + selectedCourseId + ((selectedSessionId != null && selectedSessionId > 0) ? "&sessionId=" + selectedSessionId : "") + "&format=csv";
                %>
                    <a href="<%= csvExportUrl %>" class="btn btn-primary">
                        <svg class="ui-icon ui-icon-sm"><use href="#icon-download"></use></svg>
                        <span><%= (selectedSessionId != null && selectedSessionId > 0) ? "Download Session CSV" : "Download CSV Report" %></span>
                    </a>
                <% } %>
                <button type="button" class="btn btn-secondary" onclick="window.print()">
                    <svg class="ui-icon ui-icon-sm"><use href="#icon-print"></use></svg>
                    <span>Print</span>
                </button>
            </div>
        </header>

        <% if (request.getParameter("msg") != null) { 
            String msg = request.getParameter("msg"); %>
            <div class="alert alert-success" style="margin-bottom: 1.5rem;">
                <svg class="ui-icon ui-icon-sm"><use href="#icon-check"></use></svg>
                <span>
                    <% if ("status_updated".equals(msg)) { %>
                        Attendance record updated successfully and recorded in immutable audit log.
                    <% } else if ("attendance_created".equals(msg)) { %>
                        Attendance entry recorded and logged to audit trail.
                    <% } else { %>
                        Action completed successfully.
                    <% } %>
                </span>
            </div>
        <% } %>

        <!-- Course & Session Selector Bar -->
        <div class="card" style="padding: 1.15rem 1.5rem; margin-bottom: 1.5rem; background: #ffffff; border: 1px solid var(--border-card); border-radius: 14px;">
            <form id="filterForm" class="report-filter-bar" action="${pageContext.request.contextPath}/reports" method="GET">
                <div class="filter-group">
                    <label for="courseSelect">
                        <svg class="ui-icon ui-icon-xs" style="color: #582c83;"><use href="#icon-book"></use></svg>
                        <span>Course:</span>
                    </label>
                    <select id="courseSelect" name="courseId" class="form-input" onchange="onCourseChanged()">
                        <% if (courses != null) {
                            for (Course c : courses) { %>
                                <option value="<%= c.getId() %>" <%= (selectedCourseId != null && selectedCourseId == c.getId()) ? "selected" : "" %>>
                                    <%= c.getCourseCode() %> &bull; <%= c.getCourseName() %>
                                </option>
                        <%  }
                        } %>
                    </select>
                </div>

                <div class="filter-group">
                    <label for="sessionSelect">
                        <svg class="ui-icon ui-icon-xs" style="color: #582c83;"><use href="#icon-calendar"></use></svg>
                        <span>Session:</span>
                    </label>
                    <select id="sessionSelect" name="sessionId" class="form-input" onchange="this.form.submit()">
                        <option value="all" <%= (selectedSessionId == null) ? "selected" : "" %>>All Sessions (Consolidated)</option>
                        <% if (courseSessions != null && !courseSessions.isEmpty()) {
                            for (QrSession qs : courseSessions) { %>
                                <option value="<%= qs.getId() %>" <%= (selectedSessionId != null && selectedSessionId == qs.getId()) ? "selected" : "" %>>
                                    #SES-<%= qs.getId() %> &bull; <%= qs.getScheduleFormatted() != null ? ("[" + qs.getScheduleFormatted() + "] &bull; ") : "" %><%= qs.getCreatedAt() != null ? DateTimeUtil.formatToIst(qs.getCreatedAt()) : "Session " + qs.getId() %><%= qs.isActive() ? " (Active)" : "" %>
                                </option>
                        <%  }
                        } %>
                    </select>
                </div>

                <div class="filter-search">
                    <svg class="ui-icon ui-icon-xs search-icon"><use href="#icon-search"></use></svg>
                    <input type="text" id="searchInput" placeholder="Filter by Student or Roll No..." class="form-input" onkeyup="filterTable()">
                </div>
            </form>
        </div>

        <% if (selectedSessionId != null && selectedSessionId > 0) { %>
            <div style="display: flex; align-items: center; justify-content: space-between; background: #faf5ff; border: 1px solid #d8b4fe; border-radius: 12px; padding: 0.85rem 1.25rem; margin-bottom: 1.75rem; flex-wrap: wrap; gap: 0.75rem;">
                <div style="display: flex; align-items: center; gap: 0.65rem; flex-wrap: wrap;">
                    <span class="badge" style="background: #582c83; color: #ffffff; font-weight: 700; font-size: 0.85rem; padding: 0.35rem 0.75rem;">
                        Session #SES-<%= selectedSessionId %>
                    </span>
                    <span style="color: #4c1d95; font-size: 0.9rem; font-weight: 600;">
                        Filtered view: Showing attendance records exclusively for Session #SES-<%= selectedSessionId %>
                        <% if (selectedSession != null && selectedSession.getScheduleFormatted() != null) { %>
                            &bull; <strong>Scheduled: <%= selectedSession.getScheduleFormatted() %></strong>
                        <% } %>
                        <% if (selectedSession != null && selectedSession.getCreatedAt() != null) { %>
                            &bull; Held on <%= DateTimeUtil.formatToIst(selectedSession.getCreatedAt()) %>
                            <% if (selectedSession.isActive()) { %>
                                <span style="color: #15803d; font-weight: 700;">(&bull; Currently Active)</span>
                            <% } %>
                        <% } %>
                    </span>
                </div>
                <a href="${pageContext.request.contextPath}/reports?courseId=<%= selectedCourseId %>&sessionId=all" class="btn btn-secondary btn-sm" style="background: #ffffff; border: 1px solid #c084fc;">
                    <svg class="ui-icon ui-icon-xs"><use href="#icon-close"></use></svg>
                    <span>Clear Filter (Show All)</span>
                </a>
            </div>
        <% } %>

        <!-- Metric Summary Cards -->
        <div class="grid-cols-4" style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem; margin-bottom: 2rem;">
            <div class="stat-card" style="background:#ffffff; border:1px solid #e2e8f0; border-radius:14px; padding:1.25rem;">
                <div>
                    <div class="stat-val" style="font-size: 1.85rem; font-weight: 800; color: #0f172a;"><%= totalLogs %></div>
                    <div class="stat-label" style="color: #64748b; font-size: 0.8rem; font-weight: 700; text-transform: uppercase;">Total Records</div>
                </div>
            </div>
            <div class="stat-card" style="background:#ffffff; border:1px solid #86efac; border-radius:14px; padding:1.25rem;">
                <div>
                    <div class="stat-val" style="font-size: 1.85rem; font-weight: 800; color: #15803d;"><%= totalPresent %></div>
                    <div class="stat-label" style="color: #15803d; font-size: 0.8rem; font-weight: 700; text-transform: uppercase;">Present</div>
                </div>
            </div>
            <div class="stat-card" style="background:#ffffff; border:1px solid #d8b4fe; border-radius:14px; padding:1.25rem;">
                <div>
                    <div class="stat-val" style="font-size: 1.85rem; font-weight: 800; color: #6b21a8;"><%= totalOd %></div>
                    <div class="stat-label" style="color: #6b21a8; font-size: 0.8rem; font-weight: 700; text-transform: uppercase;">On-Duty (OD)</div>
                </div>
            </div>
            <div class="stat-card" style="background:#ffffff; border:1px solid #bae6fd; border-radius:14px; padding:1.25rem;">
                <div>
                    <div class="stat-val" style="font-size: 1.85rem; font-weight: 800; color: #0369a1;"><%= totalExcused %></div>
                    <div class="stat-label" style="color: #0369a1; font-size: 0.8rem; font-weight: 700; text-transform: uppercase;">Excused / Medical</div>
                </div>
            </div>
        </div>

        <!-- Attendance Records Table -->
        <div class="card" style="background: #ffffff; border: 1px solid var(--border-card); border-radius: 16px; overflow: hidden; margin-bottom: 2.5rem; box-shadow: var(--shadow-sm);">
            <div style="padding: 1.25rem 1.5rem; border-bottom: 1px solid #e2e8f0; display: flex; justify-content: space-between; align-items: center;">
                <h2 style="font-size: 1.15rem; font-weight: 700; color: #0f172a; display: flex; align-items: center; gap: 0.45rem;">
                    <svg class="ui-icon ui-icon-xs" style="color: #582c83;"><use href="#icon-chart"></use></svg>
                    <span><%= (selectedSessionId != null && selectedSessionId > 0) ? ("Attendance Records &bull; Session #SES-" + selectedSessionId) : "Attendance Records (All Sessions)" %></span>
                </h2>
                <span style="font-size: 0.85rem; color: #64748b;"><%= attendanceList != null ? attendanceList.size() : 0 %> records found</span>
            </div>
            <div class="table-responsive" style="border: none; border-radius: 0;">
                <table class="data-table" id="reportTable" style="width: 100%; border-collapse: collapse;">
                    <thead>
                        <tr style="background: #f8fafc; border-bottom: 1px solid #e2e8f0; text-align: left;">
                            <th style="padding: 0.85rem 1.25rem; font-size: 0.76rem; text-transform: uppercase; color: #64748b;">Roll No</th>
                            <th style="padding: 0.85rem 1.25rem; font-size: 0.76rem; text-transform: uppercase; color: #64748b;">Student Name</th>
                            <th style="padding: 0.85rem 1.25rem; font-size: 0.76rem; text-transform: uppercase; color: #64748b;">Date & Time</th>
                            <th style="padding: 0.85rem 1.25rem; font-size: 0.76rem; text-transform: uppercase; color: #64748b;">Session</th>
                            <th style="padding: 0.85rem 1.25rem; font-size: 0.76rem; text-transform: uppercase; color: #64748b;">Status</th>
                            <th style="padding: 0.85rem 1.25rem; font-size: 0.76rem; text-transform: uppercase; color: #64748b; text-align: right;">Action</th>
                        </tr>
                    </thead>
                    <tbody>
                        <% if (attendanceList != null && !attendanceList.isEmpty()) { 
                            for (Attendance a : attendanceList) { 
                                String status = a.getStatus() != null ? a.getStatus().toUpperCase() : "ABSENT";
                                String badgeClass = "badge-absent";
                                if ("PRESENT".equals(status)) badgeClass = "badge-present";
                                else if ("LATE".equals(status)) badgeClass = "badge-late";
                                else if ("OD".equals(status)) badgeClass = "badge-od";
                                else if ("EXCUSED".equals(status)) badgeClass = "badge-excused";
                        %>
                                <tr style="border-bottom: 1px solid #f1f5f9;">
                                    <td style="padding: 0.85rem 1.25rem;">
                                        <span class="badge" style="background: rgba(88, 44, 131, 0.08); color: var(--color-purple); font-family: 'JetBrains Mono', monospace; font-weight: 700;">
                                            <%= a.getRollNumber() != null ? a.getRollNumber() : "&mdash;" %>
                                        </span>
                                    </td>
                                    <td style="padding: 0.85rem 1.25rem; font-weight: 600; color: #1e293b;">
                                        <%= a.getStudentName() %>
                                    </td>
                                    <td style="padding: 0.85rem 1.25rem; color: #475569; font-size: 0.88rem;">
                                        <%= DateTimeUtil.formatToIst(a.getMarkedAt()) %>
                                    </td>
                                    <td style="padding: 0.85rem 1.25rem;">
                                        <a href="${pageContext.request.contextPath}/reports?courseId=<%= selectedCourseId %>&sessionId=<%= a.getQrSessionId() %>" 
                                           title="Filter to session #SES-<%= a.getQrSessionId() %>" 
                                           style="text-decoration: none; display: inline-block;">
                                            <span class="badge" style="background: <%= (selectedSessionId != null && selectedSessionId == a.getQrSessionId()) ? "#582c83; color: #ffffff;" : "rgba(88, 44, 131, 0.08); color: var(--color-purple);" %> font-family: 'JetBrains Mono', monospace; font-size: 0.82rem; font-weight: 700; cursor: pointer;">
                                                #SES-<%= a.getQrSessionId() %>
                                            </span>
                                        </a>
                                        <% if (a.getScheduleFormatted() != null) { %>
                                            <div style="font-size: 0.72rem; color: #64748b; margin-top: 0.2rem;">
                                                <%= a.getScheduleFormatted() %>
                                            </div>
                                        <% } %>
                                    </td>
                                    <td style="padding: 0.85rem 1.25rem;">
                                        <span class="badge <%= badgeClass %>" style="padding: 0.28rem 0.65rem; border-radius: 6px; font-size: 0.8rem; font-weight: 700;">
                                            <%= status %>
                                        </span>
                                    </td>
                                    <td style="padding: 0.85rem 1.25rem; text-align: right;">
                                        <button type="button" class="btn btn-secondary btn-sm" 
                                                onclick="openEditModal(<%= a.getId() %>, '<%= a.getStudentName().replace("'", "\\'") %>', '<%= a.getRollNumber() %>', '<%= status %>')" 
                                                style="padding: 0.32rem 0.75rem; font-size: 0.8rem;">
                                            <svg class="ui-icon ui-icon-xs"><use href="#icon-edit"></use></svg>
                                            <span>Edit Status</span>
                                        </button>
                                    </td>
                                </tr>
                            <% }
                        } else { %>
                            <tr>
                                <td colspan="6" style="text-align: center; color: #64748b; padding: 3rem;">
                                    No attendance records found for this course yet.
                                </td>
                            </tr>
                        <% } %>
                    </tbody>
                </table>
            </div>
        </div>

        <!-- Immutable Audit Trail History -->
        <div class="card" style="background: #ffffff; border: 1px solid var(--border-card); border-radius: 16px; overflow: hidden; box-shadow: var(--shadow-sm);">
            <div style="padding: 1.25rem 1.5rem; border-bottom: 1px solid #e2e8f0; display: flex; justify-content: space-between; align-items: center; background: #faf5ff;">
                <div>
                    <h2 style="font-size: 1.15rem; font-weight: 700; color: #582c83; display: flex; align-items: center; gap: 0.5rem;">
                        <svg class="ui-icon ui-icon-sm"><use href="#icon-shield-check"></use></svg>
                        <span>Immutable Attendance Audit History</span>
                    </h2>
                    <p style="font-size: 0.82rem; color: #6b21a8; margin: 0.2rem 0 0 0;">
                        Every manual change, OD grant, and status correction is recorded with actor ID, timestamp, and justification
                    </p>
                </div>
                <span class="badge" style="background: #e9d5ff; color: #582c83; font-weight: 700;">
                    <%= auditLogs != null ? auditLogs.size() : 0 %> Logged Changes
                </span>
            </div>
            <div class="table-responsive" style="border: none; border-radius: 0;">
                <table class="data-table" style="width: 100%; border-collapse: collapse;">
                    <thead>
                        <tr style="background: #f8fafc; border-bottom: 1px solid #e2e8f0; text-align: left;">
                            <th style="padding: 0.85rem 1.25rem; font-size: 0.76rem; text-transform: uppercase; color: #64748b;">Timestamp</th>
                            <th style="padding: 0.85rem 1.25rem; font-size: 0.76rem; text-transform: uppercase; color: #64748b;">Student</th>
                            <th style="padding: 0.85rem 1.25rem; font-size: 0.76rem; text-transform: uppercase; color: #64748b;">Modified By (Actor)</th>
                            <th style="padding: 0.85rem 1.25rem; font-size: 0.76rem; text-transform: uppercase; color: #64748b;">Status Transition</th>
                            <th style="padding: 0.85rem 1.25rem; font-size: 0.76rem; text-transform: uppercase; color: #64748b;">Audit Reason / Justification</th>
                        </tr>
                    </thead>
                    <tbody>
                        <% if (auditLogs != null && !auditLogs.isEmpty()) { 
                            for (AttendanceAuditLog log : auditLogs) { %>
                                <tr style="border-bottom: 1px solid #f1f5f9;">
                                    <td style="padding: 0.85rem 1.25rem; font-size: 0.85rem; color: #475569;">
                                        <%= DateTimeUtil.formatToIst(log.getCreatedAt()) %>
                                    </td>
                                    <td style="padding: 0.85rem 1.25rem;">
                                        <div style="font-weight: 600; color: #1e293b;"><%= log.getStudentName() != null ? log.getStudentName() : ("Student #" + log.getStudentId()) %></div>
                                        <div style="font-family: monospace; font-size: 0.78rem; color: #64748b;"><%= log.getRollNumber() != null ? log.getRollNumber() : "" %></div>
                                    </td>
                                    <td style="padding: 0.85rem 1.25rem; font-size: 0.88rem; color: #1e293b; font-weight: 600;">
                                        <%= log.getChangedByName() != null ? log.getChangedByName() : ("User #" + log.getChangedByUserId()) %>
                                    </td>
                                    <td style="padding: 0.85rem 1.25rem;">
                                        <div style="display: flex; align-items: center; gap: 0.5rem; font-size: 0.82rem;">
                                            <span style="color: #64748b; font-weight: 600;"><%= log.getOldStatus() != null ? log.getOldStatus() : "NONE" %></span>
                                            <span>&rarr;</span>
                                            <span style="color: #582c83; font-weight: 800;"><%= log.getNewStatus() %></span>
                                        </div>
                                    </td>
                                    <td style="padding: 0.85rem 1.25rem; font-size: 0.88rem; color: #334155;">
                                        <div style="background: #f8fafc; border: 1px solid #e2e8f0; padding: 0.45rem 0.85rem; border-radius: 8px;">
                                            &ldquo;<%= log.getReason() %>&rdquo;
                                        </div>
                                    </td>
                                </tr>
                            <% }
                        } else { %>
                            <tr>
                                <td colspan="5" style="text-align: center; color: #64748b; padding: 2.5rem;">
                                    No manual attendance adjustments logged yet for this course.
                                </td>
                            </tr>
                        <% } %>
                    </tbody>
                </table>
            </div>
        </div>
    </main>

    <!-- Modal for Manual Status Edit -->
    <div id="editModal" class="modal-overlay">
        <div class="modal-card">
            <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 0.5rem;">
                <h3 style="font-size: 1.25rem; font-weight: 700; color: #0f172a;">Manual Attendance Correction</h3>
                <button type="button" onclick="closeEditModal()" style="background: none; border: none; color: #94a3b8; cursor: pointer; padding: 4px;">
                    <svg class="ui-icon ui-icon-xs"><use href="#icon-close"></use></svg>
                </button>
            </div>
            <p style="font-size: 0.85rem; color: #64748b; margin-bottom: 1.25rem;">
                Modifying attendance requires a formal audit reason that will be permanently logged.
            </p>

            <form action="${pageContext.request.contextPath}/teacher/attendance/update" method="POST">
                <input type="hidden" id="modalAttendanceId" name="attendanceId" value="">

                <div style="margin-bottom: 1rem; background: #f8fafc; border: 1px solid #e2e8f0; padding: 0.75rem 1rem; border-radius: 10px;">
                    <div style="font-size: 0.75rem; text-transform: uppercase; color: #64748b; font-weight: 700; letter-spacing: 0.04em;">Student</div>
                    <div id="modalStudentName" style="font-weight: 700; color: #0f172a; font-size: 1rem;"></div>
                    <div id="modalStudentRoll" style="font-size: 0.82rem; color: #582c83; font-family: monospace;"></div>
                </div>

                <div style="margin-bottom: 1rem;">
                    <label for="modalStatus" style="display: block; font-size: 0.85rem; font-weight: 600; color: #334155; margin-bottom: 0.4rem;">Corrected Status:</label>
                    <select id="modalStatus" name="status" class="form-input" style="width: 100%;" required>
                        <option value="PRESENT">PRESENT (Attended)</option>
                        <option value="OD">OD (On-Duty Approved - Counts towards attendance)</option>
                        <option value="EXCUSED">EXCUSED (Medical / Formal leave - Excluded from denominator)</option>
                        <option value="LATE">LATE (Counts towards attendance)</option>
                        <option value="ABSENT">ABSENT (Did not attend)</option>
                    </select>
                </div>

                <div style="margin-bottom: 1.5rem;">
                    <label for="modalReason" style="display: block; font-size: 0.85rem; font-weight: 600; color: #334155; margin-bottom: 0.4rem;">
                        Audit Reason / Justification <span style="color: #b91c1c;">*</span>:
                    </label>
                    <textarea id="modalReason" name="reason" class="form-input" rows="3" style="width: 100%;" placeholder="e.g. Approved medical leave letter submitted to department / Inter-college athletic competition on-duty" required minlength="3"></textarea>
                </div>

                <div style="display: flex; gap: 0.75rem; justify-content: flex-end;">
                    <button type="button" class="btn btn-secondary" onclick="closeEditModal()">Cancel</button>
                    <button type="submit" class="btn btn-primary">Save with Audit Trail</button>
                </div>
            </form>
        </div>
    </div>

    <footer class="footer" style="text-align: center; padding: 2rem 0; color: #94a3b8; font-size: 0.85rem;">
        <p>ScanIn Smart Attendance System &copy; 2026 &bull; Strict Role Isolation &bull; Immutable Audit Logs</p>
    </footer>

    <script>
        function onCourseChanged() {
            const sessSelect = document.getElementById("sessionSelect");
            if (sessSelect) sessSelect.value = "all";
            document.getElementById("filterForm").submit();
        }

        function filterTable() {
            const input = document.getElementById("searchInput");
            const filter = input.value.toLowerCase();
            const table = document.getElementById("reportTable");
            const tr = table.getElementsByTagName("tr");

            for (let i = 1; i < tr.length; i++) {
                const tdRoll = tr[i].getElementsByTagName("td")[0];
                const tdName = tr[i].getElementsByTagName("td")[1];
                if (tdRoll || tdName) {
                    const rollTxt = tdRoll ? tdRoll.textContent || tdRoll.innerText : "";
                    const nameTxt = tdName ? tdName.textContent || tdName.innerText : "";
                    if (rollTxt.toLowerCase().indexOf(filter) > -1 || nameTxt.toLowerCase().indexOf(filter) > -1) {
                        tr[i].style.display = "";
                    } else {
                        tr[i].style.display = "none";
                    }
                }
            }
        }

        function openEditModal(attendanceId, name, roll, currentStatus) {
            document.getElementById("modalAttendanceId").value = attendanceId;
            document.getElementById("modalStudentName").innerText = name;
            document.getElementById("modalStudentRoll").innerText = roll;
            document.getElementById("modalStatus").value = currentStatus;
            document.getElementById("modalReason").value = "";
            document.getElementById("editModal").style.display = "flex";
        }

        function closeEditModal() {
            document.getElementById("editModal").style.display = "none";
        }

        window.onclick = function(event) {
            const modal = document.getElementById("editModal");
            if (event.target === modal) {
                closeEditModal();
            }
        }
    </script>
</body>
</html>
