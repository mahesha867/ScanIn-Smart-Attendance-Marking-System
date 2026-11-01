/**
 * ScanIn — Stage 13-C4.4.2: Teacher Attendance Roster Frontend Controller
 *
 * Corrections Applied (C4.4.2):
 * - Correction A (Async Request Safety):
 *   1. Monotonically increasing request ID generator (activeRequestId) combined with AbortController.
 *   2. Session switching cancels/supersedes in-flight requests immediately.
 *   3. Stale responses and errors from earlier requests are discarded before reaching the DOM.
 *   4. Modal closure invalidates pending requests and aborts in-flight network calls.
 *   5. isFetching guard prevents duplicate refresh clicks for the same session without blocking session switches.
 *   6. Stale finally blocks cannot alter loading or error states of newer requests.
 * - Correction B (Modal Keyboard Accessibility):
 *   1. Remembers invoking button (lastTriggerButton) and restores focus upon modal close.
 *   2. Automatically moves keyboard focus to the close button when opened.
 *   3. Enforces full bidirectional Tab / Shift+Tab focus trapping within modal dialog.
 *   4. Supports Escape key and backdrop dismissal with safe focus restoration.
 */

(function () {
    'use strict';

    let currentSessionId = null;
    let isFetching = false;
    let activeRequestId = 0;
    let currentAbortController = null;
    let lastTriggerButton = null;

    /**
     * Resolves the application context path.
     */
    function getContextPath() {
        if (typeof window.SCANIN_CONTEXT_PATH === 'string') {
            return window.SCANIN_CONTEXT_PATH;
        }
        const pathname = window.location.pathname;
        const idx = pathname.indexOf('/', 1);
        return idx > 0 ? pathname.substring(0, idx) : '';
    }

    /**
     * Opens the attendance roster modal for a given planned session.
     * @param {number|string} plannedSessionId
     * @param {HTMLElement} [triggerElement]
     */
    function openSessionRoster(plannedSessionId, triggerElement) {
        // Capture invoking trigger element for keyboard focus restoration
        if (triggerElement && typeof triggerElement.focus === 'function') {
            lastTriggerButton = triggerElement;
        } else if (document.activeElement && typeof document.activeElement.focus === 'function') {
            lastTriggerButton = document.activeElement;
        }

        const idNum = Number(plannedSessionId);
        if (!Number.isInteger(idNum) || idNum <= 0) {
            console.error('Invalid plannedSessionId:', plannedSessionId);
            showErrorState('Invalid planned session ID. Please select a valid session.');
            showModal();
            return;
        }

        showModal();
        fetchSessionRoster(idNum);
    }

    /**
     * Closes the attendance roster modal and restores state.
     */
    function closeSessionRoster() {
        // Invalidate and cancel any in-flight request
        if (currentAbortController) {
            try {
                currentAbortController.abort();
            } catch (ignored) {}
            currentAbortController = null;
        }
        activeRequestId++;
        isFetching = false;

        const modal = document.getElementById('sessionRosterModal');
        if (modal) {
            modal.style.display = 'none';
            document.body.style.overflow = '';
        }

        // Restore focus to original triggering button
        if (lastTriggerButton && typeof lastTriggerButton.focus === 'function') {
            try {
                lastTriggerButton.focus();
            } catch (ignored) {}
            lastTriggerButton = null;
        }
    }

    /**
     * Re-fetches the roster for the currently selected session.
     */
    function refreshCurrentSessionRoster() {
        if (currentSessionId && !isFetching) {
            fetchSessionRoster(currentSessionId);
        }
    }

    /**
     * Shows the modal container, locks body scrolling, and moves focus into the dialog.
     */
    function showModal() {
        const modal = document.getElementById('sessionRosterModal');
        if (modal) {
            modal.style.display = 'flex';
            document.body.style.overflow = 'hidden';

            // Initial focus placement on close button or modal container
            setTimeout(function () {
                if (!modal || modal.style.display !== 'flex') {
                    return;
                }
                const headerCloseBtn = document.getElementById('rosterCloseHeaderBtn');
                if (headerCloseBtn) {
                    headerCloseBtn.focus();
                } else {
                    const focusables = getFocusableElements(modal);
                    if (focusables.length > 0) {
                        focusables[0].focus();
                    } else {
                        const container = modal.querySelector('.modal-container');
                        if (container) container.focus();
                    }
                }
            }, 30);
        }
    }

    /**
     * Returns all visible, keyboard-focusable elements inside a container.
     * @param {HTMLElement} container
     * @returns {HTMLElement[]}
     */
    function getFocusableElements(container) {
        if (!container) return [];
        const selector = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
        const elements = Array.prototype.slice.call(container.querySelectorAll(selector));
        return elements.filter(function (el) {
            return el.offsetWidth > 0 || el.offsetHeight > 0 || el.getClientRects().length > 0;
        });
    }

    /**
     * Shows the loading indicator and hides content/errors.
     */
    function showLoadingState() {
        const loading = document.getElementById('rosterLoadingIndicator');
        const content = document.getElementById('rosterContent');
        const error = document.getElementById('rosterErrorContainer');
        const refreshBtn = document.getElementById('rosterRefreshBtn');

        if (loading) loading.style.display = 'flex';
        if (content) content.style.display = 'none';
        if (error) error.style.display = 'none';
        if (refreshBtn) refreshBtn.disabled = true;
    }

    /**
     * Shows the error container with a user-safe message.
     * @param {string} message
     */
    function showErrorState(message) {
        const loading = document.getElementById('rosterLoadingIndicator');
        const content = document.getElementById('rosterContent');
        const error = document.getElementById('rosterErrorContainer');
        const errorText = document.getElementById('rosterErrorText');
        const refreshBtn = document.getElementById('rosterRefreshBtn');

        if (loading) loading.style.display = 'none';
        if (content) content.style.display = 'none';
        if (error) error.style.display = 'block';
        if (errorText) errorText.textContent = message || 'An error occurred while loading the session roster.';
        if (refreshBtn) refreshBtn.disabled = false;
    }

    /**
     * Fetches session roster from the backend endpoint with race-condition protection.
     * @param {number} sessionId
     */
    function fetchSessionRoster(sessionId) {
        // Prevent duplicate concurrent requests for the exact same session
        if (isFetching && currentSessionId === sessionId) {
            return;
        }

        // Cancel previous in-flight request if switching sessions
        if (currentAbortController) {
            try {
                currentAbortController.abort();
            } catch (ignored) {}
            currentAbortController = null;
        }

        // Establish current request generation
        currentSessionId = sessionId;
        const requestId = ++activeRequestId;
        isFetching = true;

        if (typeof AbortController !== 'undefined') {
            currentAbortController = new AbortController();
        }

        showLoadingState();

        const endpoint = getContextPath() + '/teacher/planned-sessions/roster?plannedSessionId=' + encodeURIComponent(sessionId);
        const fetchOptions = {
            method: 'GET',
            headers: {
                'Accept': 'application/json'
            }
        };
        if (currentAbortController) {
            fetchOptions.signal = currentAbortController.signal;
        }

        fetch(endpoint, fetchOptions)
        .then(function (response) {
            // Drop response if a newer request was dispatched
            if (requestId !== activeRequestId) {
                return null;
            }

            if (response.status === 200) {
                return response.json().then(function (data) {
                    if (requestId === activeRequestId) {
                        renderRosterData(data);
                    }
                });
            } else if (response.status === 400) {
                if (requestId === activeRequestId) {
                    showErrorState('Invalid session request. Please select a valid planned session.');
                }
            } else if (response.status === 403) {
                if (requestId === activeRequestId) {
                    showErrorState('Access Denied: You do not have faculty authorization for this planned session.');
                }
            } else if (response.status === 404) {
                if (requestId === activeRequestId) {
                    showErrorState('Planned session not found or has been removed.');
                }
            } else if (response.status === 500) {
                if (requestId === activeRequestId) {
                    showErrorState('A server error occurred while retrieving the session roster. Please try again.');
                }
            } else {
                if (requestId === activeRequestId) {
                    showErrorState('Unexpected server response (HTTP ' + response.status + '). Please try again.');
                }
            }
        })
        .catch(function (err) {
            // Discard aborted or superseded requests
            if (requestId !== activeRequestId || (err && err.name === 'AbortError')) {
                return;
            }
            console.error('Failed to fetch session roster:', err);
            showErrorState('Network connection error. Please check your network and retry.');
        })
        .finally(function () {
            // Only release request state if this is still the active generation
            if (requestId === activeRequestId) {
                isFetching = false;
                currentAbortController = null;
                const refreshBtn = document.getElementById('rosterRefreshBtn');
                if (refreshBtn) refreshBtn.disabled = false;
            }
        });
    }

    /**
     * Renders parsed roster JSON safely into the DOM.
     * @param {Object} data
     */
    function renderRosterData(data) {
        const loading = document.getElementById('rosterLoadingIndicator');
        const content = document.getElementById('rosterContent');
        const error = document.getElementById('rosterErrorContainer');

        if (loading) loading.style.display = 'none';
        if (error) error.style.display = 'none';
        if (content) content.style.display = 'block';

        const ps = data.plannedSession || {};
        const metrics = data.metrics || {};
        const roster = Array.isArray(data.roster) ? data.roster : [];

        // ── 1. Session Information ──────────────────────────────────────────
        const courseCodeEl = document.getElementById('rosterCourseCode');
        const courseNameEl = document.getElementById('rosterCourseName');
        const sessionMetaEl = document.getElementById('rosterSessionMeta');
        const statusBadgeEl = document.getElementById('rosterStatusBadge');
        const inProgressEl = document.getElementById('rosterInProgressBadge');

        if (courseCodeEl) {
            courseCodeEl.textContent = ps.courseCode || ('Course #' + (ps.courseId || ''));
        }
        if (courseNameEl) {
            courseNameEl.textContent = ps.courseName || '';
        }
        if (sessionMetaEl) {
            const dateStr = ps.sessionDate ? ps.sessionDate : 'Date TBD';
            const timeStr = (ps.startTime && ps.endTime) ? (ps.startTime + ' – ' + ps.endTime) : '';
            const typeStr = ps.sessionType ? ps.sessionType : 'LECTURE';
            const roomStr = ps.room ? ('Room: ' + ps.room) : 'Room: TBD';
            sessionMetaEl.textContent = [dateStr, timeStr, typeStr, roomStr].filter(Boolean).join(' • ');
        }

        if (statusBadgeEl) {
            const plannedStatus = ps.status || 'SCHEDULED';
            statusBadgeEl.textContent = plannedStatus;
            statusBadgeEl.className = 'badge ' + getPlannedStatusBadgeClass(plannedStatus);
        }

        // In-progress indicator: independent of percentage applicability
        if (inProgressEl) {
            if (metrics.inProgress) {
                inProgressEl.style.display = 'inline-flex';
                inProgressEl.textContent = '● LIVE / IN PROGRESS';
                inProgressEl.className = 'badge badge-status-present';
            } else {
                inProgressEl.style.display = 'inline-flex';
                inProgressEl.textContent = 'CLOSED / CONCLUDED';
                inProgressEl.className = 'badge badge-status-not-recorded';
            }
        }

        // ── 2. Inconsistency Alert ──────────────────────────────────────────
        const inconsistencyBanner = document.getElementById('rosterInconsistencyBanner');
        const inconsistencyText = document.getElementById('rosterInconsistencyText');

        const hasInconsistency = Boolean(metrics.containsInconsistencies) || (Number(metrics.inconsistencyCount) > 0);
        if (inconsistencyBanner && inconsistencyText) {
            if (hasInconsistency) {
                inconsistencyBanner.style.display = 'flex';
                inconsistencyText.textContent = 'Attendance Inconsistencies Detected: This session contains ' +
                    (metrics.inconsistencyCount || 0) + ' record(s) with lifecycle inconsistencies. ' +
                    'Attendance percentage is withheld (N/A) to prevent misleading calculations. ' +
                    'Inconsistent records are highlighted below and are NOT counted as Absent.';
            } else {
                inconsistencyBanner.style.display = 'none';
            }
        }

        // ── 3. Metrics Cards ────────────────────────────────────────────────
        setTextContent('metricTotalEnrolled', metrics.totalEnrolled != null ? metrics.totalEnrolled : 0);
        setTextContent('metricPresent', metrics.presentCount != null ? metrics.presentCount : 0);
        setTextContent('metricLate', metrics.lateCount != null ? metrics.lateCount : 0);
        setTextContent('metricAbsent', metrics.absentCount != null ? metrics.absentCount : 0);
        setTextContent('metricNotRecorded', metrics.notRecordedCount != null ? metrics.notRecordedCount : 0);
        setTextContent('metricInconsistent', metrics.inconsistencyCount != null ? metrics.inconsistencyCount : 0);

        // Attendance Percentage: strictly respect percentageApplicable
        const pctEl = document.getElementById('metricPercentage');
        if (pctEl) {
            if (metrics.percentageApplicable === true && metrics.attendancePercentage !== null && metrics.attendancePercentage !== undefined) {
                pctEl.textContent = Number(metrics.attendancePercentage).toFixed(2) + '%';
                pctEl.style.color = Number(metrics.attendancePercentage) >= 75.0 ? '#15803d' : '#582c83';
            } else {
                pctEl.textContent = 'N/A';
                pctEl.style.color = '#64748b';
            }
        }

        // ── 4. Student Roster Table ─────────────────────────────────────────
        const tbody = document.getElementById('rosterTableBody');
        const emptyState = document.getElementById('rosterEmptyState');
        const tableContainer = document.getElementById('rosterTableContainer');

        if (tbody) {
            tbody.textContent = ''; // Safe wipe of existing rows

            if (roster.length === 0) {
                if (emptyState) emptyState.style.display = 'block';
                if (tableContainer) tableContainer.style.display = 'none';
            } else {
                if (emptyState) emptyState.style.display = 'none';
                if (tableContainer) tableContainer.style.display = 'block';

                roster.forEach(function (student, index) {
                    const tr = document.createElement('tr');
                    tr.style.borderBottom = '1px solid var(--border-subtle)';

                    // Column 1: S.No.
                    const tdSno = document.createElement('td');
                    tdSno.textContent = String(index + 1);
                    tdSno.style.padding = '0.75rem';
                    tdSno.style.fontWeight = '600';
                    tdSno.style.color = 'var(--text-muted)';
                    tr.appendChild(tdSno);

                    // Column 2: Register Number
                    const tdRoll = document.createElement('td');
                    tdRoll.textContent = student.rollNumber || '—';
                    tdRoll.style.padding = '0.75rem';
                    tdRoll.style.fontWeight = '700';
                    tr.appendChild(tdRoll);

                    // Column 3: Student Name (NO student email)
                    const tdName = document.createElement('td');
                    tdName.textContent = student.studentName || '—';
                    tdName.style.padding = '0.75rem';
                    tdName.style.fontWeight = '500';
                    tr.appendChild(tdName);

                    // Column 4: Attendance Status Badge
                    const tdStatus = document.createElement('td');
                    tdStatus.style.padding = '0.75rem';

                    const badge = document.createElement('span');
                    const pStatus = student.presentationStatus || 'NOT RECORDED';
                    badge.className = 'badge ' + getStatusBadgeClass(pStatus);
                    badge.textContent = getStatusLabel(pStatus);
                    tdStatus.appendChild(badge);

                    // Recorded timestamp if available
                    if (student.markedAt) {
                        const timeSpan = document.createElement('div');
                        timeSpan.style.fontSize = '0.75rem';
                        timeSpan.style.color = 'var(--text-muted)';
                        timeSpan.style.marginTop = '0.2rem';
                        timeSpan.textContent = '🕒 ' + student.markedAt;
                        tdStatus.appendChild(timeSpan);
                    }

                    tr.appendChild(tdStatus);
                    tbody.appendChild(tr);
                });
            }
        }

        // Update footer last-refreshed timestamp
        const lastRefreshedEl = document.getElementById('rosterLastRefreshed');
        if (lastRefreshedEl) {
            const now = new Date();
            lastRefreshedEl.textContent = 'Last refreshed: ' + now.toLocaleTimeString();
        }
    }

    /**
     * Maps presentation status to CSS badge class.
     * @param {string} status
     * @returns {string}
     */
    function getStatusBadgeClass(status) {
        switch (status) {
            case 'PRESENT': return 'badge-status-present';
            case 'LATE': return 'badge-status-late';
            case 'ABSENT': return 'badge-status-absent';
            case 'NOT ACTIVATED': return 'badge-status-not-activated';
            case 'NOT YET RECORDED': return 'badge-status-not-yet-recorded';
            case 'NOT RECORDED': return 'badge-status-not-recorded';
            case 'DATA INCONSISTENCY': return 'badge-status-inconsistency';
            default: return 'badge-status-not-recorded';
        }
    }

    /**
     * Formats human-readable status label with symbol.
     * @param {string} status
     * @returns {string}
     */
    function getStatusLabel(status) {
        switch (status) {
            case 'PRESENT': return '✓ PRESENT';
            case 'LATE': return '⏰ LATE';
            case 'ABSENT': return '✕ ABSENT';
            case 'NOT ACTIVATED': return '🗓️ NOT ACTIVATED';
            case 'NOT YET RECORDED': return '⏳ NOT YET RECORDED';
            case 'NOT RECORDED': return '— NOT RECORDED';
            case 'DATA INCONSISTENCY': return '⚠️ DATA INCONSISTENCY';
            default: return status || 'UNKNOWN';
        }
    }

    /**
     * Maps planned session status to badge CSS class.
     * @param {string} status
     * @returns {string}
     */
    function getPlannedStatusBadgeClass(status) {
        switch (status) {
            case 'SCHEDULED': return 'badge-status-not-activated';
            case 'ATTENDANCE_OPEN': return 'badge-status-present';
            case 'COMPLETED': return 'badge-status-not-recorded';
            default: return 'badge-status-not-recorded';
        }
    }

    /**
     * Safe helper to set textContent.
     */
    function setTextContent(elementId, text) {
        const el = document.getElementById(elementId);
        if (el) {
            el.textContent = String(text);
        }
    }

    // ── Global Event Bindings ───────────────────────────────────────────────
    window.openSessionRoster = openSessionRoster;
    window.closeSessionRoster = closeSessionRoster;
    window.refreshCurrentSessionRoster = refreshCurrentSessionRoster;

    document.addEventListener('DOMContentLoaded', function () {
        const modal = document.getElementById('sessionRosterModal');

        // Keyboard handler: Escape and bidirectional Tab focus trapping
        document.addEventListener('keydown', function (e) {
            if (!modal || modal.style.display !== 'flex') return;

            // Escape key closes modal with focus restoration
            if (e.key === 'Escape' || e.key === 'Esc') {
                e.preventDefault();
                closeSessionRoster();
                return;
            }

            // Tab key focus trapping
            if (e.key === 'Tab') {
                const focusables = getFocusableElements(modal);
                if (focusables.length === 0) {
                    e.preventDefault();
                    return;
                }
                if (focusables.length === 1) {
                    e.preventDefault();
                    focusables[0].focus();
                    return;
                }

                const firstEl = focusables[0];
                const lastEl = focusables[focusables.length - 1];

                if (e.shiftKey) {
                    // Shift + Tab: reverse wrap from first to last
                    if (document.activeElement === firstEl || !modal.contains(document.activeElement)) {
                        e.preventDefault();
                        lastEl.focus();
                    }
                } else {
                    // Tab: forward wrap from last to first
                    if (document.activeElement === lastEl || !modal.contains(document.activeElement)) {
                        e.preventDefault();
                        firstEl.focus();
                    }
                }
            }
        });

        // Backdrop click dismisses modal with focus restoration
        if (modal) {
            modal.addEventListener('click', function (e) {
                if (e.target === modal) {
                    closeSessionRoster();
                }
            });
        }

        // Retry button click
        const retryBtn = document.getElementById('rosterRetryBtn');
        if (retryBtn) {
            retryBtn.addEventListener('click', function () {
                if (currentSessionId) {
                    fetchSessionRoster(currentSessionId);
                }
            });
        }

        // Auto-open if query parameter exists (e.g. ?viewRoster=12)
        try {
            const urlParams = new URLSearchParams(window.location.search);
            const autoId = urlParams.get('viewRoster') || urlParams.get('rosterSessionId');
            if (autoId) {
                const parsedId = parseInt(autoId, 10);
                if (!isNaN(parsedId) && parsedId > 0) {
                    openSessionRoster(parsedId);
                }
            }
        } catch (ignored) {}
    });

})();
