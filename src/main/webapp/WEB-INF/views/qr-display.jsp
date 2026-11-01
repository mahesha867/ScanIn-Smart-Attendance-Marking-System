<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.attendance.model.Course" %>
<%@ page import="com.attendance.model.PlannedSession" %>
<%
    Course course = (Course) request.getAttribute("course");
    String sessionToken = (String) request.getAttribute("sessionToken");
    String scanUrl = (String) request.getAttribute("scanUrl");
    int sessionId = request.getAttribute("sessionId") != null ? (Integer) request.getAttribute("sessionId") : 0;
    int markedCount = request.getAttribute("markedCount") != null ? (Integer) request.getAttribute("markedCount") : 0;
    int remainingSeconds = request.getAttribute("remainingSeconds") != null ? (Integer) request.getAttribute("remainingSeconds") : 7;
    long remainingMs = request.getAttribute("remainingMs") != null ? (Long) request.getAttribute("remainingMs") : 7000L;
    long nextRotationBoundary = request.getAttribute("nextRotationBoundary") != null ? (Long) request.getAttribute("nextRotationBoundary") : 0L;
    boolean isExpired = Boolean.TRUE.equals(request.getAttribute("isExpired"));
    int plannedSessionId = request.getAttribute("plannedSessionId") != null ? (Integer) request.getAttribute("plannedSessionId") : 0;
    PlannedSession plannedSession = (PlannedSession) request.getAttribute("plannedSession");
    String csrfToken = (String) request.getAttribute("csrfToken");
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><%= isExpired ? "Session Expired" : "Live Attendance QR" %> - <%= course != null ? course.getCourseCode() : "Class" %></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@500;700&display=swap" rel="stylesheet">
</head>
<body>

    <nav class="navbar">
        <a href="${pageContext.request.contextPath}/teacher/dashboard" class="nav-brand">
            <div class="brand-icon">⚡</div>
            <span>ScanIn &bull; <%= isExpired ? "Session Expired" : "Live Attendance Session" %></span>
        </a>
        <div class="nav-actions">
            <% if (!isExpired) { %>
                <span class="user-badge" style="background: rgba(88, 44, 131, 0.1); color: var(--color-purple); border: 1px solid rgba(88, 44, 131, 0.25);">
                    <span class="radar-dot"></span>
                    <span>Active 7s Rotation</span>
                </span>
            <% } else { %>
                <span class="badge" style="background: #fee2e2; color: #dc2626; border: 1px solid #fca5a5; font-size: 0.85rem; padding: 0.4rem 0.8rem;">
                    ⏰ Scheduled End Reached
                </span>
            <% } %>
            <% if (sessionId > 0) { %>
                <form action="${pageContext.request.contextPath}/qr/close" method="POST" style="display:inline; margin:0;">
                    <input type="hidden" name="sessionId" value="<%= sessionId %>">
                    <input type="hidden" name="csrfToken" value="<%= csrfToken != null ? csrfToken : "" %>">
                    <button type="submit" class="btn btn-secondary btn-sm" onclick="return confirm('End this attendance session now? Any unmarked students will be automatically marked ABSENT.')" style="color: #b91c1c; border-color: #fca5a5;">
                        ⏹ <%= isExpired ? "Finalize & Mark Absentees" : "End Session & Mark Absentees" %>
                    </button>
                </form>
            <% } %>
            <a href="${pageContext.request.contextPath}/teacher/dashboard" class="btn btn-secondary btn-sm">
                ← Dashboard
            </a>
        </div>
    </nav>

    <main class="container" style="display:flex; justify-content:center; align-items:center; padding: 2rem 1rem;">
        <div class="card qr-container" style="max-width: 680px; width: 100%; text-align: center; background: #ffffff; border: 1px solid var(--border-color); border-radius: 16px; box-shadow: 0 10px 25px -5px rgba(88,44,131,0.08); padding: 2.25rem;">
            
            <div style="margin-bottom: 1.25rem;">
                <span class="badge" style="background: rgba(88, 44, 131, 0.08); color: var(--color-purple); font-weight: 700; border: 1px solid rgba(88, 44, 131, 0.2); font-size: 0.92rem; padding: 0.4rem 1.1rem; border-radius: 20px;">
                    <%= course != null ? course.getCourseCode() : "" %> &bull; <%= course != null ? course.getCourseName() : "" %>
                </span>
                <% if (plannedSession != null) { %>
                    <div style="margin-top: 0.5rem; font-size: 0.85rem; color: #64748b; font-weight: 600;">
                        🗓️ Planned Meeting: <%= plannedSession.getSessionDate() %> (<%= plannedSession.getStartTime() %> - <%= plannedSession.getEndTime() %>) &bull; <%= plannedSession.getSessionType() %> (<%= plannedSession.getRoom() %>)
                    </div>
                <% } %>
                <h1 class="page-title" style="margin-top: 0.85rem; font-size: 2rem; color: #0f172a;">
                    <%= isExpired ? "Attendance Window Expired" : "Dynamic Attendance QR" %>
                </h1>
                <p class="page-subtitle" style="color: #64748b;">
                    <%= isExpired ? "This session has reached its scheduled end time and is no longer accepting scans." : "Cryptographically server-signed tokens rotate every 7 seconds" %>
                </p>
            </div>

            <% if (isExpired) { %>
                <!-- Expired State Notification Box -->
                <div style="background: #fef2f2; border: 1.5px solid #fecaca; border-radius: 12px; padding: 1.5rem; margin-bottom: 1.75rem; text-align: center;">
                    <div style="font-size: 2.2rem; margin-bottom: 0.5rem;">⏱️</div>
                    <h3 style="font-size: 1.15rem; font-weight: 700; color: #991b1b; margin-bottom: 0.4rem;">
                        Class Meeting Scheduled Window Concluded
                    </h3>
                    <p style="font-size: 0.88rem; color: #7f1d1d; margin: 0 auto 1rem auto; max-width: 480px;">
                        The scheduled duration for this academic session has ended. To maintain strict attendance integrity, dynamic QR rotation has been stopped and new scan attempts will be rejected.
                    </p>
                    <% if (sessionId > 0) { %>
                        <form action="${pageContext.request.contextPath}/qr/close" method="POST" style="margin: 0;">
                            <input type="hidden" name="sessionId" value="<%= sessionId %>">
                            <input type="hidden" name="csrfToken" value="<%= csrfToken != null ? csrfToken : "" %>">
                            <button type="submit" class="btn btn-primary" onclick="return confirm('Finalize attendance? All unmarked enrolled students will be recorded as ABSENT.')" style="background: #dc2626; border-color: #b91c1c;">
                                ⏹ Finalize Attendance & Reconcile Absentees
                            </button>
                        </form>
                    <% } %>
                </div>
            <% } else { %>
                <!-- 7-Second Countdown Timer -->
                <div style="display: flex; align-items: center; justify-content: center; gap: 0.75rem; margin-bottom: 1.25rem;">
                    <span style="font-size: 0.95rem; font-weight: 600; color: #475569;">Token rotates in:</span>
                    <div id="qr-timer" class="timer-badge safe" style="font-size: 1.1rem; font-weight: 800; min-width: 52px; padding: 0.35rem 0.85rem; border-radius: 20px;"><%= remainingSeconds %>s</div>
                </div>

                <!-- Dynamic QR Code Display -->
                <div class="qr-box" style="margin: 0 auto 1.5rem; width: 280px; height: 280px; padding: 12px; background: #ffffff; border: 2px solid rgba(88, 44, 131, 0.2); border-radius: 16px; box-shadow: 0 4px 12px rgba(0,0,0,0.05); display: flex; align-items: center; justify-content: center;">
                    <img id="qr-image" src="" alt="Dynamic Attendance QR Code" style="width: 100%; height: 100%; object-fit: contain; border-radius: 8px;">
                </div>
            <% } %>

            <!-- Attendance Stats Tracker -->
            <div style="display: flex; gap: 1.5rem; justify-content: center; margin-bottom: 1.5rem;">
                <div style="background: #f8fafc; border: 1px solid #e2e8f0; padding: 0.85rem 1.75rem; border-radius: 12px;">
                    <span style="font-size: 0.78rem; font-weight: 700; color: #64748b; text-transform: uppercase; letter-spacing: 0.05em; display: block;">Attendance Recorded</span>
                    <span id="student-count" style="font-size: 1.6rem; font-weight: 800; color: var(--color-purple);"><%= markedCount %></span>
                    <span style="font-size: 0.9rem; font-weight: 600; color: #64748b;"> students marked</span>
                </div>
            </div>

            <% if (!isExpired) { %>
                <!-- 6-Digit Numeric Fallback Display Box -->
                <div style="margin-top: 1rem; background: #faf5ff; border: 1.5px solid #d8b4fe; border-radius: 12px; padding: 1.25rem; text-align: center;">
                    <div style="font-size: 0.85rem; font-weight: 700; color: #582c83; margin-bottom: 0.4rem; text-transform: uppercase; letter-spacing: 0.05em;">
                        🔢 6-Digit Numeric Fallback Code
                    </div>
                    <div id="numeric-display" style="font-family: 'JetBrains Mono', monospace; font-size: 2.2rem; font-weight: 800; letter-spacing: 0.25em; color: #582c83; background: #ffffff; padding: 0.6rem 1.25rem; border-radius: 8px; border: 2px dashed #a855f7; display: inline-block; box-shadow: inset 0 2px 4px rgba(0,0,0,0.03);">
                        <%= request.getAttribute("numericCode") != null ? request.getAttribute("numericCode") : "------" %>
                    </div>
                    <div style="margin-top: 0.5rem;">
                        <button type="button" class="btn btn-secondary btn-sm" onclick="copyNumericCode()" style="padding: 0.25rem 0.75rem; font-size: 0.75rem;">Copy Code</button>
                    </div>
                    <p style="font-size: 0.78rem; color: #6b21a8; margin-top: 0.5rem; margin-bottom: 0;">
                        Students without camera access can enter this <strong>6-digit code</strong> into the typed fallback box on their portal.
                    </p>
                    <div id="token-display" style="display: none;"><%= sessionToken != null ? sessionToken : "" %></div>
                </div>
            <% } %>

        </div>
    </main>

    <footer class="footer" style="text-align: center; padding: 1.5rem 0; color: #94a3b8; font-size: 0.85rem;">
        <p>ScanIn &bull; Dynamic HMAC-SHA256 Token Rotates Every 7 Seconds &bull; Powered by Tomcat & MySQL</p>
    </footer>

    <% if (!isExpired) { %>
        <script src="${pageContext.request.contextPath}/js/qr-scanner.js?v=<%= System.currentTimeMillis() %>"></script>
        <script>
            document.addEventListener("DOMContentLoaded", function() {
                initDynamicQrSession(<%= course != null ? course.getId() : 0 %>, "<%= sessionToken != null ? sessionToken : "" %>", "<%= scanUrl != null ? scanUrl : "" %>", "<%= request.getAttribute("numericCode") != null ? request.getAttribute("numericCode") : "" %>", "${pageContext.request.contextPath}", <%= remainingMs %>, <%= nextRotationBoundary %>, <%= plannedSessionId %>);
            });

            function copyToken() {
                const tokenElem = document.getElementById("token-display");
                if (!tokenElem) return;
                const tokenText = tokenElem.innerText;
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
    <% } %>
</body>
</html>
