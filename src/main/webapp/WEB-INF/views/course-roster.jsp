<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.attendance.model.Course" %>
<%@ page import="com.attendance.model.Student" %>
<%@ page import="com.attendance.model.Teacher" %>
<%@ page import="com.attendance.model.User" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%
    Course course = (Course) request.getAttribute("course");
    Teacher teacher = (Teacher) request.getAttribute("teacher");
    User user = (User) session.getAttribute("currentUser");
    List<Student> enrolledStudents = (List<Student>) request.getAttribute("enrolledStudents");
    Map<Integer, Double> attendanceMap = (Map<Integer, Double>) request.getAttribute("attendanceMap");
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><%= course != null ? course.getCourseCode() : "Course" %> Roster - Faculty Portal</title>
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
        <!-- Breadcrumb & Back -->
        <div style="margin-bottom: 1.25rem;">
            <a href="${pageContext.request.contextPath}/teacher/dashboard" class="btn btn-secondary btn-sm">
                <svg class="ui-icon ui-icon-xs"><use href="#icon-arrow-left"></use></svg>
                <span>Back to Faculty Dashboard</span>
            </a>
        </div>

        <header class="page-header">
            <div>
                <h1 class="page-title">
                    <%= course != null ? course.getCourseCode() + ": " + course.getCourseName() : "Course Roster" %>
                </h1>
                <p class="page-subtitle">
                    <%= course != null ? course.getDepartment() + " &bull; Semester " + course.getSemester() + " &bull; Faculty: " + (teacher != null ? teacher.getName() : "") : "" %>
                </p>
            </div>
            <div>
                <a href="${pageContext.request.contextPath}/qr/generate?courseId=<%= course != null ? course.getId() : "" %>" class="btn btn-primary">
                    <svg class="ui-icon ui-icon-sm"><use href="#icon-qr"></use></svg>
                    <span>Launch Live QR Session</span>
                </a>
            </div>
        </header>

        <!-- Roster Card -->
        <section class="glass-card" style="border-radius: 16px;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; border-bottom: 1.5px solid var(--border-subtle); padding-bottom: 0.75rem;">
                <h2 style="font-size: 1.25rem; font-weight: 700; color: #582c83; display: flex; align-items: center; gap: 0.5rem;">
                    <svg class="ui-icon ui-icon-sm"><use href="#icon-users"></use></svg>
                    <span>Enrolled Student Roster</span>
                </h2>
                <span class="badge badge-present" style="font-size: 0.8rem;">
                    Total Enrolled: <%= enrolledStudents != null ? enrolledStudents.size() : 0 %>
                </span>
            </div>

            <% if (enrolledStudents == null || enrolledStudents.isEmpty()) { %>
                <div style="text-align: center; padding: 2.5rem 1rem; background: var(--bg-subtle); border-radius: var(--radius-sm); border: 1.5px dashed var(--border-subtle);">
                    <div style="width: 42px; height: 42px; margin: 0 auto 0.65rem; border-radius: 50%; background: #ffffff; display: flex; align-items: center; justify-content: center; color: var(--text-muted); border: 1px solid var(--border-subtle);">
                        <svg class="ui-icon ui-icon-md"><use href="#icon-student"></use></svg>
                    </div>
                    <h3 style="font-size: 1.15rem; font-weight: 700; color: #1e293b; margin-bottom: 0.3rem;">No Students Enrolled Yet</h3>
                    <p style="color: var(--text-muted); font-size: 0.92rem; max-width: 450px; margin: 0 auto;">
                        No students have registered for this course yet. Once students enroll from their portal, their academic details and attendance records will appear here.
                    </p>
                </div>
            <% } else { %>
                <div class="table-responsive">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>Roll Number</th>
                                <th>Student Full Name</th>
                                <th>Institutional Email</th>
                                <th>Department</th>
                                <th>Semester</th>
                                <th>Attendance %</th>
                                <th>Status</th>
                            </tr>
                        </thead>
                        <tbody>
                            <% for (Student s : enrolledStudents) { 
                                Double pct = attendanceMap != null ? attendanceMap.get(s.getStudentId()) : 100.0;
                                if (pct == null) pct = 100.0;
                                boolean isWarning = pct < 75.0;
                            %>
                                <tr>
                                    <td>
                                        <span class="badge" style="background: rgba(88, 44, 131, 0.08); color: var(--color-purple); font-family: 'JetBrains Mono', monospace; font-weight: 700;">
                                            <%= s.getRollNumber() %>
                                        </span>
                                    </td>
                                    <td style="font-weight: 600; color: #1e293b;"><%= s.getName() %></td>
                                    <td style="color: var(--text-secondary);"><%= s.getEmail() %></td>
                                    <td><%= s.getDepartment() %></td>
                                    <td>Sem <%= s.getSemester() %></td>
                                    <td style="width: 150px;">
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
                                                <span>Low (&lt; 75%)</span>
                                            </span>
                                        <% } else { %>
                                            <span class="badge badge-present">
                                                <svg class="ui-icon ui-icon-xs"><use href="#icon-check"></use></svg>
                                                <span>Eligible</span>
                                            </span>
                                        <% } %>
                                    </td>
                                </tr>
                            <% } %>
                        </tbody>
                    </table>
                </div>
            <% } %>
        </section>
    </main>

    <footer class="footer">
        <p>ScanIn Smart Attendance System &bull; Faculty Course Management &bull; College DBMS Project</p>
    </footer>

</body>
</html>
