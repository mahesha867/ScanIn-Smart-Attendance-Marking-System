<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
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

            <div id="scan-feedback" style="display: none; margin-bottom: 1rem;"></div>

            <!-- Video Camera Viewfinder Box -->
            <div style="position: relative; width: 100%; max-width: 360px; height: 320px; margin: 0 auto 1.5rem; border-radius: var(--radius-md); overflow: hidden; background: #000; border: 2px dashed rgba(99, 102, 241, 0.4);">
                <video id="video" style="width: 100%; height: 100%; object-fit: cover;" playsinline></video>
                <canvas id="qr-canvas" style="display: none;"></canvas>

                <!-- Scanning animation overlay -->
                <div style="position: absolute; inset: 20px; border: 2px solid #6366f1; border-radius: 12px; pointer-events: none; box-shadow: 0 0 15px rgba(99, 102, 241, 0.5);"></div>
                <div id="camera-loading" style="position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; background: rgba(0,0,0,0.7); color: #fff; font-size: 0.9rem;">
                    <span>🎥 Starting Camera...</span>
                    <span style="font-size: 0.78rem; color: #94a3b8; margin-top: 0.4rem;">Please allow camera permission</span>
                </div>
            </div>

            <div style="display: flex; gap: 0.5rem; justify-content: center; margin-bottom: 1.5rem;">
                <button type="button" class="btn btn-secondary btn-sm" onclick="startCamera()">Restart Camera</button>
            </div>

            <!-- Manual 6-Digit Code & Token Fallback Section -->
            <div style="border-top: 1px solid var(--border-subtle); padding-top: 1.25rem;">
                <label for="manual-token" style="display: block; font-size: 0.88rem; font-weight: 600; color: #582c83; margin-bottom: 0.5rem;">
                    🔢 Or Enter 6-Digit Code / Token
                </label>
                <div style="display: flex; gap: 0.5rem;">
                    <input type="text" id="manual-token" class="form-input" 
                           placeholder="e.g. 492018 or full token..." 
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
        let scanning = true;

        async function startCamera() {
            try {
                const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" } });
                video.srcObject = stream;
                video.setAttribute("playsinline", true);
                video.play();
                loading.style.display = "none";
                requestAnimationFrame(tick);
            } catch (err) {
                loading.innerHTML = "<span style='color:#f87171;'>⚠️ Camera unavailable</span><span style='font-size:0.75rem; color:#94a3b8; margin-top:0.3rem;'>Use manual token verification below</span>";
            }
        }

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

        async function submitToken(token) {
            if (isSubmitting) return;
            isSubmitting = true;
            scanning = false; // Pause camera scanning while verification is in-flight

            feedback.style.display = "block";
            feedback.className = "alert alert-info";
            feedback.innerText = "⏳ Verifying attendance token...";

            try {
                const response = await fetch("${pageContext.request.contextPath}/qr/scan", {
                    method: "POST",
                    headers: { 
                        "Content-Type": "application/x-www-form-urlencoded",
                        "Accept": "application/json"
                    },
                    body: "token=" + encodeURIComponent(token)
                });

                if (response.status === 401) {
                    feedback.className = "alert alert-danger";
                    feedback.innerText = "⚠️ Session expired. Please log in again.";
                    setTimeout(() => {
                        window.location.href = "${pageContext.request.contextPath}/login?msg=session_expired";
                    }, 1500);
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
                        scanning = true;
                        requestAnimationFrame(tick);
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
                    // Provide 2.5s cooling-off period so student can read message, then resume scanning
                    setTimeout(() => {
                        isSubmitting = false;
                        scanning = true;
                        requestAnimationFrame(tick);
                    }, 2500);
                }
            } catch (e) {
                feedback.className = "alert alert-danger";
                feedback.innerText = "⚠️ Network error while verifying attendance. Retrying in 2 seconds...";
                setTimeout(() => {
                    isSubmitting = false;
                    scanning = true;
                    requestAnimationFrame(tick);
                }, 2500);
            }
        }

        document.addEventListener("DOMContentLoaded", startCamera);
    </script>
</body>
</html>
