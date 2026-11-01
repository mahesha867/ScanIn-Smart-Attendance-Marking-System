<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.attendance.util.SessionUtil" %>
<%
    String prefilledToken = (String) request.getAttribute("prefilledToken");
    boolean hasPrefilledToken = (prefilledToken != null && !prefilledToken.trim().isEmpty());
    String encodedPrefilledToken = hasPrefilledToken ? SessionUtil.escapeHtmlAttribute(prefilledToken.trim()) : "";
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="${csrfToken}">
    <title>Scan Dynamic QR - Smart Attendance</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet">
    <!-- jsQR scanner library for in-browser client camera decoding -->
    <script src="https://cdn.jsdelivr.net/npm/jsqr@1.4.0/dist/jsQR.min.js"></script>
</head>
<body>

    <nav class="navbar">
        <a href="${pageContext.request.contextPath}/student/dashboard" class="nav-brand">
            <div class="brand-icon">📷</div>
            <span>SmartAttendance &bull; Camera Scanner</span>
        </a>
        <div class="nav-actions">
            <a href="${pageContext.request.contextPath}/student/dashboard" class="btn btn-secondary btn-sm">
                ← Back to Dashboard
            </a>
        </div>
    </nav>

    <main class="container" style="display:flex; justify-content:center; align-items:center;">
        <div class="glass-card" style="width: 100%; max-width: 520px; text-align: center;">
            
            <h1 class="page-title" style="font-size: 1.6rem; margin-bottom: 0.3rem;">Scan Classroom QR</h1>
            <p class="page-subtitle" style="margin-bottom: 1.5rem;">Align the live QR code on the teacher's screen within the box</p>

            <% if (hasPrefilledToken) { %>
                <!-- Token Detected Explicit Confirmation Banner -->
                <div id="token-detected-box" class="alert alert-info" style="display: block; margin-bottom: 1.5rem; text-align: center; border: 1.5px solid #c7d2fe; background: #eef2ff; border-radius: var(--radius-md); padding: 1.25rem;">
                    <div style="font-size: 1.15rem; font-weight: 700; color: #3730a3; margin-bottom: 0.35rem;">
                        ⚡ Attendance Token Detected
                    </div>
                    <p style="font-size: 0.88rem; color: #4338ca; margin: 0 0 1rem 0;">
                        An attendance session token was detected from your classroom link or recent scan. Please click below to verify and record your attendance.
                    </p>
                    <button type="button" id="confirm-attendance-btn" class="btn btn-primary" onclick="confirmAttendanceClick()" style="font-weight: 700; padding: 0.6rem 2rem; font-size: 1rem; width: 100%; box-shadow: 0 4px 12px rgba(99, 102, 241, 0.35);">
                        ✅ Confirm Attendance
                    </button>
                </div>
            <% } %>

            <div id="scan-feedback" style="display: none; margin-bottom: 1rem;"></div>

            <!-- Video Camera Viewfinder Box -->
            <div style="position: relative; width: 100%; max-width: 360px; height: 320px; margin: 0 auto 1.5rem; border-radius: var(--radius-md); overflow: hidden; background: #000; border: 2px dashed rgba(99, 102, 241, 0.4);">
                <video id="video" style="width: 100%; height: 100%; object-fit: cover;" playsinline></video>
                <canvas id="qr-canvas" style="display: none;"></canvas>

                <!-- Scanning animation overlay -->
                <div style="position: absolute; inset: 20px; border: 2px solid #6366f1; border-radius: 12px; pointer-events: none; box-shadow: 0 0 15px rgba(99, 102, 241, 0.5);"></div>

                <!-- Initial Idle Overlay (Camera Off by Default) -->
                <div id="camera-idle" style="position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; background: rgba(15, 23, 42, 0.9); color: #fff; padding: 1.5rem; text-align: center; z-index: 5;">
                    <div style="font-size: 2.2rem; margin-bottom: 0.5rem;">📷</div>
                    <div style="font-size: 1rem; font-weight: 700; margin-bottom: 0.35rem;">Camera Scanner Ready</div>
                    <div style="font-size: 0.8rem; color: #94a3b8; margin-bottom: 1.2rem; max-width: 280px; line-height: 1.4;">
                        Click below to turn on your camera and scan the dynamic classroom QR code.
                    </div>
                    <button type="button" id="start-camera-overlay-btn" class="btn btn-primary" onclick="startCamera()" style="padding: 0.55rem 1.5rem; font-weight: 700; font-size: 0.95rem; box-shadow: 0 4px 14px rgba(99, 102, 241, 0.4);">
                        ▶ Start Camera
                    </button>
                </div>

                <!-- Loading / Permission Overlay -->
                <div id="camera-loading" style="position: absolute; inset: 0; display: none; flex-direction: column; align-items: center; justify-content: center; background: rgba(0,0,0,0.85); color: #fff; font-size: 0.9rem; z-index: 6;">
                    <span>🎥 Starting Camera...</span>
                    <span style="font-size: 0.78rem; color: #94a3b8; margin-top: 0.4rem;">Please grant camera permission</span>
                </div>
            </div>

            <!-- Camera Control Buttons -->
            <div style="display: flex; gap: 0.5rem; justify-content: center; margin-bottom: 1.5rem;">
                <button type="button" id="start-camera-btn" class="btn btn-primary btn-sm" onclick="startCamera()">▶ Start Camera</button>
                <button type="button" id="stop-camera-btn" class="btn btn-secondary btn-sm" onclick="stopCamera()" style="display: none;">⏹ Stop Camera</button>
            </div>

            <!-- Manual 6-Digit Code & Token Fallback Section -->
            <div style="border-top: 1px solid var(--border-subtle); padding-top: 1.25rem;">
                <label for="manual-token" style="display: block; font-size: 0.88rem; font-weight: 600; color: #582c83; margin-bottom: 0.5rem;">
                    🔢 Or Enter 6-Digit Code / Token
                </label>
                <div style="display: flex; gap: 0.5rem;">
                    <input type="text" id="manual-token" class="form-input" 
                           placeholder="e.g. 492018 or full token..." 
                           value="<%= encodedPrefilledToken %>"
                           style="font-size: 0.95rem; font-family: 'JetBrains Mono', monospace; text-align: center; letter-spacing: 0.1em;" 
                           aria-label="Enter 6-digit attendance code or session token"
                           maxlength="250">
                    <button type="button" class="btn btn-primary btn-sm" onclick="submitManualToken()">Verify Code</button>
                </div>
                <p style="font-size: 0.78rem; color: #64748b; margin-top: 0.4rem; margin-bottom: 0;">
                    Type the 6-digit code displayed on the classroom screen if your camera cannot scan.
                </p>
            </div>
        </div>
    </main>

    <footer class="footer">
        <p>Smart Attendance Marking System &bull; Dynamic Token Verification</p>
    </footer>

    <script>
        const video = document.getElementById("video");
        const canvasElement = document.getElementById("qr-canvas");
        const canvas = canvasElement.getContext("2d", { willReadFrequently: true });
        const feedback = document.getElementById("scan-feedback");
        const loading = document.getElementById("camera-loading");
        const idleOverlay = document.getElementById("camera-idle");
        let scanning = false;
        let currentStream = null;

        function stopCameraTracks() {
            if (currentStream) {
                currentStream.getTracks().forEach(track => {
                    try { track.stop(); } catch (e) {}
                });
                currentStream = null;
            }
            if (video) {
                video.srcObject = null;
            }
        }

        function stopCamera() {
            scanning = false;
            stopCameraTracks();
            if (idleOverlay) idleOverlay.style.display = "flex";
            if (loading) loading.style.display = "none";
            const startBtn = document.getElementById("start-camera-btn");
            const stopBtn = document.getElementById("stop-camera-btn");
            if (startBtn) {
                startBtn.style.display = "inline-flex";
                startBtn.innerText = "▶ Start Camera";
            }
            if (stopBtn) stopBtn.style.display = "none";
        }

        async function startCamera() {
            if (isSubmitting) return;
            try {
                stopCameraTracks();
                if (idleOverlay) idleOverlay.style.display = "none";
                if (loading) {
                    loading.style.display = "flex";
                    loading.innerHTML = "<span>🎥 Accessing Camera...</span><span style='font-size:0.78rem; color:#94a3b8; margin-top:0.4rem;'>Please grant camera permission</span>";
                }

                const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" } });
                currentStream = stream;
                video.srcObject = stream;
                video.setAttribute("playsinline", true);
                await video.play();

                if (loading) loading.style.display = "none";
                scanning = true;

                const startBtn = document.getElementById("start-camera-btn");
                const stopBtn = document.getElementById("stop-camera-btn");
                if (startBtn) startBtn.style.display = "none";
                if (stopBtn) stopBtn.style.display = "inline-flex";

                requestAnimationFrame(tick);
            } catch (err) {
                console.error("Camera access error:", err);
                if (loading) {
                    loading.style.display = "flex";
                    loading.innerHTML = "<span style='color:#f87171;'>⚠️ Camera unavailable / denied</span><span style='font-size:0.75rem; color:#94a3b8; margin-top:0.3rem;'>Please use the manual code or token entry below</span>";
                }
                const startBtn = document.getElementById("start-camera-btn");
                if (startBtn) {
                    startBtn.style.display = "inline-flex";
                    startBtn.innerText = "🔄 Retry Camera";
                }
            }
        }

        window.addEventListener("pagehide", stopCameraTracks);
        window.addEventListener("beforeunload", stopCameraTracks);

        function tick() {
            if (!scanning) return;
            if (video.readyState === video.HAVE_ENOUGH_DATA) {
                canvasElement.height = video.videoHeight;
                canvasElement.width = video.videoWidth;
                canvas.drawImage(video, 0, 0, canvasElement.width, canvasElement.height);
                const imageData = canvas.getImageData(0, 0, canvasElement.width, canvasElement.height);
                
                if (window.jsQR) {
                    const code = jsQR(imageData.data, imageData.width, imageData.height, {
                        inversionAttempts: "dontInvert",
                    });
                    if (code && code.data) {
                        scanning = false;
                        handleDetectedUrl(code.data);
                        return;
                    }
                }
            }
            requestAnimationFrame(tick);
        }

        let isSubmitting = false;

        function handleDetectedUrl(detectedData) {
            if (isSubmitting) return;
            let token = detectedData ? detectedData.trim() : "";
            if (!token) return;

            // If full URL scanned, extract token parameter
            try {
                if (token.includes("token=")) {
                    const url = new URL(token, window.location.origin);
                    token = url.searchParams.get("token") || token;
                }
            } catch (e) {
                const match = token.match(/[?&]token=([^&]+)/);
                if (match && match[1]) {
                    token = decodeURIComponent(match[1]);
                }
            }

            submitToken(token);
        }

        async function submitManualToken() {
            if (isSubmitting) return;
            const val = document.getElementById("manual-token").value.trim();
            if (!val) return;
            handleDetectedUrl(val);
        }

        function confirmAttendanceClick() {
            if (isSubmitting) return;
            const val = document.getElementById("manual-token").value.trim();
            if (!val) return;
            handleDetectedUrl(val);
        }

        async function submitToken(token) {
            if (isSubmitting) return;
            isSubmitting = true;
            scanning = false; // Pause camera scanning while verification is in-flight
            stopCameraTracks(); // Release camera hardware during verification and redirect

            feedback.style.display = "block";
            feedback.className = "alert alert-info";
            feedback.innerText = "⏳ Verifying attendance token...";

            const csrfMeta = document.querySelector('meta[name="csrf-token"]');
            const csrfToken = csrfMeta ? (csrfMeta.getAttribute('content') || '') : '';

            try {
                const response = await fetch("${pageContext.request.contextPath}/qr/scan", {
                    method: "POST",
                    headers: { 
                        "Content-Type": "application/x-www-form-urlencoded",
                        "Accept": "application/json",
                        "X-CSRF-TOKEN": csrfToken
                    },
                    body: "token=" + encodeURIComponent(token) + "&csrfToken=" + encodeURIComponent(csrfToken)
                });

                if (response.status === 401) {
                    feedback.className = "alert alert-danger";
                    feedback.innerText = "⚠️ Session expired. Please log in again.";
                    setTimeout(() => {
                        window.location.href = "${pageContext.request.contextPath}/login?msg=session_expired";
                    }, 1500);
                    return;
                }

                if (response.status === 403) {
                    feedback.className = "alert alert-danger";
                    feedback.innerHTML = "⚠️ Security validation failed: Invalid or missing CSRF token.<br>" +
                        "<button type='button' class='btn btn-secondary btn-sm' style='margin-top:0.6rem;' onclick='window.location.reload()'>🔄 Refresh Page & Retry</button>";
                    isSubmitting = false;
                    return;
                }

                let res;
                try {
                    res = await response.json();
                } catch (jsonErr) {
                    feedback.className = "alert alert-danger";
                    feedback.innerText = "⚠️ Verification response was invalid. Please try again.";
                    setTimeout(() => {
                        isSubmitting = false;
                        const startBtn = document.getElementById("start-camera-btn");
                        if (startBtn) startBtn.innerText = "▶ Restart Camera";
                    }, 2500);
                    return;
                }

                if (res.success) {
                    feedback.className = "alert alert-success";
                    feedback.innerText = "✅ " + res.message;
                    setTimeout(() => {
                        window.location.href = "${pageContext.request.contextPath}/student/dashboard";
                    }, 1500);
                } else {
                    feedback.className = "alert alert-danger";
                    feedback.innerText = "⚠️ " + res.message;
                    // Provide 2.5s cooling-off period so student can read message, then reset submit flag
                    setTimeout(() => {
                        isSubmitting = false;
                        const startBtn = document.getElementById("start-camera-btn");
                        if (startBtn) startBtn.innerText = "▶ Restart Camera";
                    }, 2500);
                }
            } catch (e) {
                feedback.className = "alert alert-danger";
                feedback.innerText = "⚠️ Network error while verifying attendance. Retrying in 2 seconds...";
                setTimeout(() => {
                    isSubmitting = false;
                    const startBtn = document.getElementById("start-camera-btn");
                    if (startBtn) startBtn.innerText = "▶ Restart Camera";
                }, 2500);
            }
        }

        document.addEventListener("DOMContentLoaded", function() {
            // CRITICAL SECURITY RULE (Phase 13-C2.2):
            // 1. NEVER automatically initialize or start the camera on page load.
            // 2. NEVER automatically submit any token on page load.
            // Explicit clicks on 'Start Camera' or 'Confirm Attendance' are strictly required.
        });
    </script>
</body>
</html>
