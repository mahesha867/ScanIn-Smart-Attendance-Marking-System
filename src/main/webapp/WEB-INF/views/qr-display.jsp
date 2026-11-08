<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.attendance.model.Course" %>
<%
    Course course = (Course) request.getAttribute("course");
    String sessionToken = (String) request.getAttribute("sessionToken");
    String scanUrl = (String) request.getAttribute("scanUrl");
    int sessionId = request.getAttribute("sessionId") != null ? (Integer) request.getAttribute("sessionId") : 0;
    int markedCount = request.getAttribute("markedCount") != null ? (Integer) request.getAttribute("markedCount") : 0;
    int remainingSeconds = request.getAttribute("remainingSeconds") != null ? (Integer) request.getAttribute("remainingSeconds") : 7;
    long remainingMs = request.getAttribute("remainingMs") != null ? (Long) request.getAttribute("remainingMs") : 7000L;
    long nextRotationBoundary = request.getAttribute("nextRotationBoundary") != null ? (Long) request.getAttribute("nextRotationBoundary") : 0L;
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Live Attendance QR - <%= course != null ? course.getCourseCode() : "Class" %></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@500;700&display=swap" rel="stylesheet">
</head>
<body>
    <%@ include file="common-icons.jspf" %>

    <nav class="navbar">
        <a href="${pageContext.request.contextPath}/teacher/dashboard" class="nav-brand">
            <div class="brand-icon">
                <svg class="ui-icon"><use href="#icon-scanin"></use></svg>
            </div>
            <span>ScanIn &bull; Live Attendance Session</span>
        </a>
        <div class="nav-actions">
            <span class="user-badge" style="background: rgba(88, 44, 131, 0.1); color: var(--color-purple); border: 1px solid rgba(88, 44, 131, 0.25);">
                <span class="radar-dot"></span>
                <span>Active 7s Rotation</span>
            </span>
            <form action="${pageContext.request.contextPath}/qr/close" method="GET" style="display:inline; margin:0;">
                <input type="hidden" name="sessionId" value="<%= sessionId %>">
                <button type="submit" class="btn btn-secondary btn-sm" onclick="return confirm('End this attendance session now? Any unmarked students will be automatically marked ABSENT.')" style="color: #b91c1c; border-color: #fca5a5;">
                    <svg class="ui-icon ui-icon-xs" style="color: #b91c1c;"><use href="#icon-stop"></use></svg>
                    <span>End Session & Mark Absentees</span>
                </button>
            </form>
        </div>
    </nav>

    <main class="container" style="display:flex; justify-content:center; align-items:center; padding: 2rem 1rem;">
        <div class="card qr-container" style="max-width: 680px; width: 100%; text-align: center; background: #ffffff; border: 1px solid var(--border-card); border-radius: 20px; box-shadow: 0 12px 30px -5px rgba(88,44,131,0.1); padding: 2.5rem;">
            
            <div style="margin-bottom: 1.25rem;">
                <span class="badge" style="background: rgba(88, 44, 131, 0.08); color: var(--color-purple); font-weight: 700; border: 1px solid rgba(88, 44, 131, 0.2); font-size: 0.92rem; padding: 0.45rem 1.2rem; border-radius: 20px;">
                    <%= course != null ? course.getCourseCode() : "" %> &bull; <%= course != null ? course.getCourseName() : "" %>
                </span>
                <h1 class="page-title" style="margin-top: 0.85rem; font-size: 2.1rem; color: #0f172a;">Dynamic Attendance QR</h1>
                <p class="page-subtitle" style="color: #64748b;">Cryptographically server-signed tokens rotate every 7 seconds</p>
            </div>

            <!-- 7-Second Countdown Timer -->
            <div style="display: flex; align-items: center; justify-content: center; gap: 0.75rem; margin-bottom: 1.25rem;">
                <span style="font-size: 0.95rem; font-weight: 600; color: #475569;">Token rotates in:</span>
                <div id="qr-timer" class="timer-badge safe" style="font-size: 1.15rem; font-weight: 800; min-width: 56px; padding: 0.35rem 0.95rem; border-radius: 20px;"><%= remainingSeconds %>s</div>
            </div>

            <!-- Dynamic QR Code Display -->
            <div class="qr-box" style="margin: 0 auto 1.5rem; width: 284px; height: 284px; padding: 12px; background: #ffffff; border: 2px solid #582c83; border-radius: 18px; box-shadow: 0 8px 24px rgba(88, 44, 131, 0.16); display: flex; align-items: center; justify-content: center;">
                <img id="qr-image" src="" alt="Dynamic Attendance QR Code" style="width: 100%; height: 100%; object-fit: contain; border-radius: 10px;">
            </div>

            <!-- Attendance Stats Tracker -->
            <div style="display: flex; gap: 1.5rem; justify-content: center; margin-bottom: 1.5rem;">
                <div style="background: #f8fafc; border: 1px solid #e2e8f0; padding: 0.95rem 2rem; border-radius: 14px; display: flex; flex-direction: column; align-items: center;">
                    <span style="font-size: 0.78rem; font-weight: 700; color: #64748b; text-transform: uppercase; letter-spacing: 0.05em; display: flex; align-items: center; gap: 0.35rem;">
                        <svg class="ui-icon ui-icon-xs" style="color: #582c83;"><use href="#icon-users"></use></svg>
                        <span>Attendance Recorded</span>
                    </span>
                    <div style="margin-top: 0.2rem;">
                        <span id="student-count" style="font-size: 1.85rem; font-weight: 800; color: var(--color-purple);"><%= markedCount %></span>
                        <span style="font-size: 0.92rem; font-weight: 600; color: #64748b;"> students marked</span>
                    </div>
                </div>
            </div>

            <!-- 6-Digit Numeric Fallback Display Box -->
            <div style="margin-top: 1rem; background: #faf5ff; border: 1.5px solid #d8b4fe; border-radius: 14px; padding: 1.35rem; text-align: center;">
                <div style="font-size: 0.82rem; font-weight: 700; color: #582c83; margin-bottom: 0.5rem; text-transform: uppercase; letter-spacing: 0.05em; display: flex; align-items: center; justify-content: center; gap: 0.35rem;">
                    <svg class="ui-icon ui-icon-xs"><use href="#icon-keypad"></use></svg>
                    <span>6-Digit Numeric Fallback Code</span>
                </div>
                <div id="numeric-display" style="font-family: 'JetBrains Mono', monospace; font-size: 2.25rem; font-weight: 800; letter-spacing: 0.25em; color: #582c83; background: #ffffff; padding: 0.65rem 1.35rem; border-radius: 10px; border: 2px dashed #a855f7; display: inline-block; box-shadow: inset 0 2px 4px rgba(0,0,0,0.03);">
                    <%= request.getAttribute("numericCode") != null ? request.getAttribute("numericCode") : "------" %>
                </div>
                <div style="margin-top: 0.65rem;">
                    <button type="button" class="btn btn-secondary btn-sm" onclick="copyNumericCode()" style="padding: 0.3rem 0.85rem; font-size: 0.78rem;">
                        <svg class="ui-icon ui-icon-xs"><use href="#icon-copy"></use></svg>
                        <span>Copy Code</span>
                    </button>
                </div>
                <p style="font-size: 0.8rem; color: #6b21a8; margin-top: 0.5rem; margin-bottom: 0;">
                    Students without camera access can enter this <strong>6-digit code</strong> into the typed fallback box on their portal.
                </p>
                <div id="token-display" style="display: none;"><%= sessionToken %></div>
            </div>

        </div>
    </main>

    <footer class="footer" style="text-align: center; padding: 1.5rem 0; color: #94a3b8; font-size: 0.85rem;">
        <p>ScanIn &bull; Dynamic HMAC-SHA256 Token Rotates Every 7 Seconds &bull; Powered by Tomcat & MySQL</p>
    </footer>

    <script src="${pageContext.request.contextPath}/js/qr-scanner.js?v=<%= System.currentTimeMillis() %>"></script>
    <script>
        document.addEventListener("DOMContentLoaded", function() {
            initDynamicQrSession(<%= course != null ? course.getId() : 0 %>, "<%= sessionToken %>", "<%= scanUrl %>", "<%= request.getAttribute("numericCode") != null ? request.getAttribute("numericCode") : "" %>", "${pageContext.request.contextPath}", <%= remainingMs %>, <%= nextRotationBoundary %>);
        });

        function copyToken() {
            const tokenText = document.getElementById("token-display").innerText;
            navigator.clipboard.writeText(tokenText).then(() => {
                alert("Token copied to clipboard!");
            }).catch(() => {
                const ta = document.createElement("textarea");
                ta.value = tokenText;
                document.body.appendChild(ta);
                ta.select();
                document.execCommand("copy");
                document.body.removeChild(ta);
                alert("Token copied!");
            });
        }
    </script>
</body>
</html>
