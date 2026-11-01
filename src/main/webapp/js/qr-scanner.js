/**
 * ScanIn - Dynamic QR Generator & Scanner JS
 */

// ── Dynamic QR Session Live 7-Second Auto-Refresher (Teacher Screen) ──
function initDynamicQrSession(courseId, initialToken, initialScanUrl, initialNumericCode, contextPath) {
    const ROTATION_INTERVAL = 7;
    let timeLeft = ROTATION_INTERVAL;
    let isFetching = false;
    const timerElem = document.getElementById("qr-timer");
    const qrImg = document.getElementById("qr-image");
    const tokenDisplay = document.getElementById("token-display");
    const numericDisplay = document.getElementById("numeric-display");
    const countDisplay = document.getElementById("student-count");

    const base = (contextPath !== undefined && contextPath !== null) ? contextPath : "";
    const activeUrl = base + "/qr/active?courseId=" + courseId + "&format=json";

    function renderQr(token, scanUrl, numericCode) {
        if (qrImg) {
            // High reliability QR code generation API
            qrImg.src = "https://api.qrserver.com/v1/create-qr-code/?size=280x280&margin=10&data=" + encodeURIComponent(scanUrl);
        }
        if (tokenDisplay) {
            tokenDisplay.innerText = token;
        }
        if (numericDisplay && numericCode) {
            numericDisplay.innerText = numericCode;
        }
    }

    renderQr(initialToken, initialScanUrl, initialNumericCode);

    // Countdown interval every 1 second
    const interval = setInterval(async () => {
        timeLeft--;
        if (timerElem) {
            timerElem.innerText = timeLeft + "s";
            if (timeLeft <= 2) {
                timerElem.className = "timer-badge"; // alert pulse
                timerElem.style.background = "#fee2e2";
                timerElem.style.color = "#dc2626";
            } else {
                timerElem.className = "timer-badge safe";
                timerElem.style.background = "#dcfce7";
                timerElem.style.color = "#16a34a";
            }
        }

        // When countdown reaches 0, fetch fresh server-signed token via 7s rotation AJAX
        if (timeLeft <= 0) {
            if (isFetching) {
                // Prevent overlapping requests if a poll is already in flight
                return;
            }
            isFetching = true;
            timeLeft = ROTATION_INTERVAL; // Immediately reset countdown so it never stalls at 0

            try {
                const response = await fetch(activeUrl);
                if (response.ok) {
                    const data = await response.json();
                    if (data.status === "ok") {
                        timeLeft = data.expiresInSeconds || ROTATION_INTERVAL;
                        renderQr(data.token, data.scanUrl, data.numericCode);
                        if (countDisplay && data.markedCount !== undefined) {
                            countDisplay.innerText = data.markedCount;
                        }
                    } else if (data.status === "closed") {
                        clearInterval(interval);
                        if (timerElem) {
                            timerElem.innerText = "Closed";
                            timerElem.className = "timer-badge";
                            timerElem.style.background = "#fee2e2";
                            timerElem.style.color = "#dc2626";
                        }
                    }
                } else {
                    // Non-OK response: preserve active timer countdown for next attempt
                    timeLeft = ROTATION_INTERVAL;
                }
            } catch (err) {
                console.error("Failed to rotate 7s QR token:", err);
                // Recover timer so countdown does not freeze on network error
                timeLeft = ROTATION_INTERVAL;
            } finally {
                isFetching = false;
            }
        }
    }, 1000);
}

function copyNumericCode() {
    const numDisplay = document.getElementById("numeric-display");
    if (!numDisplay) return;
    const text = numDisplay.innerText.trim();
    if (navigator.clipboard) {
        navigator.clipboard.writeText(text).then(() => {
            alert("Code " + text + " copied!");
        }).catch(() => fallbackCopy(text));
    } else {
        fallbackCopy(text);
    }
}

function fallbackCopy(text) {
    const ta = document.createElement("textarea");
    ta.value = text;
    document.body.appendChild(ta);
    ta.select();
    document.execCommand("copy");
    document.body.removeChild(ta);
    alert("Code " + text + " copied!");
}

// ── Quick Demo Credential Autofill ────────────────────────────
function fillCredentials(email, password) {
    const emailField = document.getElementById("email");
    const passField = document.getElementById("password");
    if (emailField && passField) {
        emailField.value = email;
        passField.value = password;
        emailField.focus();
    }
}
