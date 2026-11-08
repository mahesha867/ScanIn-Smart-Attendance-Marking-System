<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Login - Smart Attendance System</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet">
</head>
<body>
    <%@ include file="common-icons.jspf" %>

    <nav class="navbar">
        <a href="${pageContext.request.contextPath}/login" class="nav-brand">
            <div class="brand-icon">
                <svg class="ui-icon"><use href="#icon-scanin"></use></svg>
            </div>
            <span>ScanIn &bull; SmartAttendance</span>
        </a>
        <div class="nav-actions">
            <span class="user-badge">
                <svg class="ui-icon ui-icon-xs" style="opacity: 0.85;"><use href="#icon-shield-check"></use></svg>
                <span>Academic Portal</span>
            </span>
        </div>
    </nav>

    <main class="container" style="display:flex; justify-content:center; align-items:center; min-height: calc(100vh - 160px);">
        <div class="glass-card" style="width: 100%; max-width: 440px; border-radius: 16px; box-shadow: var(--shadow-md);">
            <div style="text-align: center; margin-bottom: 2rem;">
                <div style="width: 56px; height: 56px; margin: 0 auto 1rem; border-radius: 14px; background: var(--purple-subtle); border: 1.5px solid var(--purple-border); display: flex; align-items: center; justify-content: center; color: #582c83;">
                    <svg class="ui-icon ui-icon-xl"><use href="#icon-scanin"></use></svg>
                </div>
                <h1 class="page-title" style="font-size: 1.75rem;">Welcome Back</h1>
                <p class="page-subtitle">Dynamic 7-Second QR Attendance Portal</p>
            </div>

            <% if (request.getAttribute("errorMessage") != null) { %>
                <div class="alert alert-danger">
                    <svg class="ui-icon ui-icon-sm"><use href="#icon-alert"></use></svg>
                    <span><%= request.getAttribute("errorMessage") %></span>
                </div>
            <% } %>

            <% if ("logged_out".equals(request.getParameter("msg"))) { %>
                <div class="alert alert-info">
                    <svg class="ui-icon ui-icon-sm"><use href="#icon-info"></use></svg>
                    <span>You have been successfully logged out.</span>
                </div>
            <% } else if ("login_to_scan".equals(request.getParameter("msg"))) { %>
                <div class="alert alert-info">
                    <svg class="ui-icon ui-icon-sm"><use href="#icon-qr"></use></svg>
                    <span>Please log in as a student to record your attendance.</span>
                </div>
            <% } %>

            <form action="${pageContext.request.contextPath}/login" method="POST">
                <div class="form-group">
                    <label class="form-label" for="email">Institutional Email</label>
                    <input type="email" id="email" name="email" class="form-input" 
                           placeholder="e.g. teacher@attendance.com"
                           value="<%= request.getAttribute("enteredEmail") != null ? request.getAttribute("enteredEmail") : "" %>" required>
                </div>

                <div class="form-group">
                    <label class="form-label" for="password">Password</label>
                    <input type="password" id="password" name="password" class="form-input" 
                           placeholder="Enter your password" required>
                </div>

                <button type="submit" class="btn btn-primary" style="width: 100%; margin-top: 0.5rem;">
                    <span>Sign In to Portal</span>
                    <svg class="ui-icon ui-icon-sm"><use href="#icon-arrow-right"></use></svg>
                </button>
            </form>

            <div class="demo-credentials">
                <div class="demo-title">
                    <svg class="ui-icon ui-icon-xs" style="color: #582c83;"><use href="#icon-keypad"></use></svg>
                    <span>Quick Demo Logins (Click to autofill)</span>
                </div>
                <button type="button" class="demo-pill" onclick="fillCredentials('teacher@attendance.com', 'teacher123')">
                    <svg class="ui-icon"><use href="#icon-teacher"></use></svg>
                    <span>Teacher (Prof. Ramesh)</span>
                </button>
                <button type="button" class="demo-pill" onclick="fillCredentials('aarav@student.com', 'student123')">
                    <svg class="ui-icon"><use href="#icon-student"></use></svg>
                    <span>Student (Aarav)</span>
                </button>
                <button type="button" class="demo-pill" onclick="fillCredentials('priya@student.com', 'student123')">
                    <svg class="ui-icon"><use href="#icon-student"></use></svg>
                    <span>Student (Priya)</span>
                </button>
            </div>
        </div>
    </main>

    <footer class="footer">
        <p>Smart Attendance Marking System &copy; 2026 &bull; Java 21 &bull; MySQL 8.0 &bull; Object-Oriented Design</p>
    </footer>

    <script src="${pageContext.request.contextPath}/js/qr-scanner.js"></script>
</body>
</html>
