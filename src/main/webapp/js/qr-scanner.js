/**
 * ScanIn - Dynamic QR Generator & Scanner JS
 */

// ── Dynamic QR Session Live 7-Second Auto-Refresher (Teacher Screen) ──
function initDynamicQrSession(courseId, initialToken, initialScanUrl, initialNumericCode, contextPath, initialRemainingMs, initialNextBoundary, plannedSessionId) {
    // 1. Singleton guard: Cancel any existing timers or polling loops
    if (window.__scanInSession) {
        if (window.__scanInSession.countdownTimer) {
            clearInterval(window.__scanInSession.countdownTimer);
        }
        if (window.__scanInSession.pollTimeout) {
            clearTimeout(window.__scanInSession.pollTimeout);
        }
    }

    const initialRem = (typeof initialRemainingMs === "number" && initialRemainingMs > 0) ? initialRemainingMs : 7000;
    const sessionState = {
        countdownTimer: null,
        pollTimeout: null,
        isFetching: false,
        currentToken: initialToken || "",
        currentNumericCode: initialNumericCode || "",
        // Monotonic target time anchored to server remaining milliseconds
        targetMonotonicTime: performance.now() + initialRem
    };
    window.__scanInSession = sessionState;

    const timerElem = document.getElementById("qr-timer");
    const qrImg = document.getElementById("qr-image");
    const tokenDisplay = document.getElementById("token-display");
    const numericDisplay = document.getElementById("numeric-display");
    const countDisplay = document.getElementById("student-count");

    const base = (contextPath !== undefined && contextPath !== null) ? contextPath : "";
    const pParam = (plannedSessionId && plannedSessionId > 0) ? "&plannedSessionId=" + plannedSessionId : "";
    const activeUrl = base + "/qr/active?courseId=" + courseId + pParam + "&format=json";

    function updateUi(token, scanUrl, numericCode) {
        if (token && token !== sessionState.currentToken) {
            sessionState.currentToken = token;
            if (qrImg) {
                // High reliability QR code generation API
                qrImg.src = "https://api.qrserver.com/v1/create-qr-code/?size=280x280&margin=10&data=" + encodeURIComponent(scanUrl);
            }
            if (tokenDisplay) {
                tokenDisplay.innerText = token;
            }
        }
        if (numericCode && numericCode !== sessionState.currentNumericCode) {
            sessionState.currentNumericCode = numericCode;
            if (numericDisplay) {
                numericDisplay.innerText = numericCode;
            }
        }
    }

    // Initial render
    updateUi(initialToken, initialScanUrl, initialNumericCode);

    async function pollActiveToken() {
        if (sessionState.isFetching) {
            return;
        }
        sessionState.isFetching = true;

        try {
            const response = await fetch(activeUrl);
            if (response.ok) {
                const data = await response.json();
                if (data.status === "ok") {
                    updateUi(data.token, data.scanUrl, data.numericCode);
                    if (countDisplay && data.markedCount !== undefined) {
                        countDisplay.innerText = data.markedCount;
                    }
                    // Server-synchronized rotation boundary:
                    // Use server's remainingMs to re-anchor local monotonic target time
                    const remMs = (typeof data.remainingMs === "number" && data.remainingMs > 0) ? data.remainingMs : 7000;
                    sessionState.targetMonotonicTime = performance.now() + remMs;
                } else if (data.status === "closed" || data.status === "expired") {
                    cleanup();
                    if (timerElem) {
                        timerElem.innerText = data.status === "expired" ? "Expired" : "Closed";
                        timerElem.className = "timer-badge";
                        timerElem.style.background = "#fee2e2";
                        timerElem.style.color = "#dc2626";
                    }
                }
            } else {
                // Non-OK HTTP response: retry in 1000ms without freezing
                sessionState.targetMonotonicTime = performance.now() + 1000;
            }
        } catch (err) {
            console.error("Failed to rotate 7s QR token:", err);
            // On network error: retry in 1000ms
            sessionState.targetMonotonicTime = performance.now() + 1000;
        } finally {
            sessionState.isFetching = false;
        }
    }

    function cleanup() {
        if (sessionState.countdownTimer) {
            clearInterval(sessionState.countdownTimer);
            sessionState.countdownTimer = null;
        }
        if (sessionState.pollTimeout) {
            clearTimeout(sessionState.pollTimeout);
            sessionState.pollTimeout = null;
        }
    }

    // Monotonic tick (every 100ms) for smooth countdown and immediate boundary detection
    sessionState.countdownTimer = setInterval(() => {
        const remainingMs = Math.max(0, sessionState.targetMonotonicTime - performance.now());
        const remainingSec = Math.ceil(remainingMs / 1000);

        if (timerElem) {
            timerElem.innerText = remainingSec + "s";
            if (remainingSec <= 2) {
                timerElem.className = "timer-badge"; // alert pulse
                timerElem.style.background = "#fee2e2";
                timerElem.style.color = "#dc2626";
            } else {
                timerElem.className = "timer-badge safe";
                timerElem.style.background = "#dcfce7";
                timerElem.style.color = "#16a34a";
            }
        }

        // When rotation boundary is reached, trigger poll
        if (remainingMs <= 0 && !sessionState.isFetching) {
            pollActiveToken();
        }
    }, 100);
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
