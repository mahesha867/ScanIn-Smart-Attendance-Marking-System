# ScanIn — Stage 16: Controlled Tomcat Deployment & Live HTTP Verification Report

**Date:** 2026-11-01  
**Project Root:** `C:\java_dbms_project`  
**Git Branch:** `main`  
**Git HEAD:** `9b59559e0ef5c5a5e3e29be1eaf131fad8442d0b`  
**Candidate WAR:** [`dist/attendance-stage15-candidate.war`](dist/attendance-stage15-candidate.war)  
**Target Environment:** Local Apache Tomcat 10.1.20 (Port 8080) & MySQL 8.0.46 (`attendance_test_db`)  
**Overall Verdict:** **CONDITIONAL PASS — HTTP deployment verified with documented limitations (physical camera hardware limitation)**

---

## 1. Executive Summary

Stage 16 performed an end-to-end controlled deployment and live verification of the fresh Stage 15 Release Candidate archive (`dist/attendance-stage15-candidate.war`). The candidate WAR was deployed to a local Apache Tomcat 10.1.20 instance running on port 8080, backed by the isolated `attendance_test_db` schema and the restricted `attendance_test_user` account.

Verification was conducted across two distinct testing suites:
1. **Live HTTP & JDBC Smoke Suite (25 Tests):** Verified application initialization, cookie flags, authentication sanitization, session fixation rotation (`request.changeSessionId()`), role-based access control (RBAC), CSRF protection, planned session rendering, dynamic QR generation, live attendance scanning, duplicate scan rejection, session closure reconciliation, and historical enrollment retention.
2. **Real-Browser Automation Suite (Chrome 156 via CDP):** Drove headless Google Chrome 156.0.8078.74 via Chrome DevTools Protocol to observe client-side DOM rendering, dynamic 7-second QR token rotation, student timetable display, camera scanner viewfinder, roster modal keyboard accessibility (`Escape` key), responsive viewports (Desktop 1920x1080 and Mobile 375x667), and browser console error telemetry.

**Key Results:**
- **Preflight Safety Gate:** 100% compliant. Zero connection to `attendance_db`. Zero credential exposure.
- **Controlled Deployment:** Successfully mounted without overwriting either original baseline WAR.
- **HTTP Smoke Verification:** 25 Passed, 0 Failed, 0 Skipped (100% pass rate).
- **Real-Browser Verification:** 9 Passed, 0 Failed, 1 Documented Limitation (Physical camera hardware: `NOT TESTED`).
- **Post-Verification Hygiene:** 100% of synthetic test fixtures deleted. Port 8080 cleanly released. WAR checksums and Git HEAD unaltered.

---

## 2. Preflight Safety & Authorization Verification

All mandatory preflight checks were satisfied before launching services:

| Check | Requirement | Observed Status | Assessment |
|---|---|---|---|
| **Git Baseline** | HEAD matches `9b59559e0ef5c5a5e3e29be1eaf131fad8442d0b` | `9b59559e0ef5c5a5e3e29be1eaf131fad8442d0b` | ✅ PASS |
| **Protected Database Guard** | Never access or touch `attendance_db` | Zero queries to `attendance_db` | ✅ PASS |
| **Dedicated Test Account** | `attendance_test_user`@`localhost` | Authenticated exclusively as test user | ✅ PASS |
| **Schema Identity** | `SELECT DATABASE()` must return `attendance_test_db` | `attendance_test_db` confirmed | ✅ PASS |
| **Account Privileges** | USAGE on `*.*` and ALL on `attendance_test_db.*` only | `SHOW GRANTS` confirmed zero access to `attendance_db` | ✅ PASS |
| **HMAC Secret Strength** | Min 32 chars, Shannon entropy $H \ge 2.5$, $\ge 10$ distinct bytes | `TestHmacSecretForScanInVerification2026!` passed all checks | ✅ PASS |
| **Candidate WAR Hash** | Must match `3637897BA009A476F1C793B5A3406F08CC6390AD2F5EF7990BB63BEE885B922B` | SHA-256 confirmed byte-for-byte | ✅ PASS |
| **Original WAR Invariance** | Preserve `dist/attendance.war` & `tomcat/webapps/attendance.war` | Both preserved untouched (SHA-256 `C80AC9FB...`) | ✅ PASS |

---

## 3. Controlled Deployment Architecture

To guarantee the preservation of existing baseline WARs, deployment was performed via dedicated context mapping:
- **Backup Created:** `tomcat/webapps/attendance.dir.bak_20261101_pre_stage16`.
- **Candidate War Deployed:** `tomcat/webapps/attendance-stage15-candidate.war` (SHA-256 `3637897B...`).
- **Tomcat Context Descriptor:** Mounted at `tomcat/conf/Catalina/localhost/attendance.xml` with `docBase` referencing `dist/attendance-stage15-candidate.war`.
- **Runtime Environment:** Process-level environment variables injected dynamically into Tomcat (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `QR_HMAC_SECRET`, `SESSION_COOKIE_SECURE=false`). No plaintext credentials stored in configuration files or logs.
- **Server Startup:** Tomcat 10.1.20 initialized cleanly on port 8080 with root context path `/attendance`.

---

## 4. Live HTTP Smoke Verification Results (25 / 25 Passed)

The HTTP test runner (`com.attendance.Stage16LiveHttpVerificationRunner`) executed 25 targeted test cases against the live container:

| ID | Test Case | Target / Method | Expected Result | Actual Result | Status |
|---|---|---|---|---|---|
| **INIT-01** | Login Page Availability | `GET /attendance/login` | HTTP 200, HTML contains login form | HTTP 200, contains `<form action="login"` | ✅ PASS |
| **INIT-02** | Static Assets Delivery | `GET /attendance/css/style.css` | HTTP 200, CSS stylesheet served | HTTP 200, valid CSS content | ✅ PASS |
| **COOKIE-01** | Session Cookie Security | Response `Set-Cookie` | `HttpOnly`, `Path=/attendance` | `HttpOnly`, `Path=/attendance` present | ✅ PASS |
| **AUTH-01** | Invalid Credentials | `POST /attendance/login` (bad pass) | Generic error, no SQL/stack disclosure | "Invalid email or password", 0 SQL leaked | ✅ PASS |
| **AUTH-02** | Unregistered Account | `POST /attendance/login` (bad email) | Identical generic error message | "Invalid email or password", 0 SQL leaked | ✅ PASS |
| **AUTH-03** | Session Fixation Rotation | `POST /attendance/login` (valid) | Session ID rotated via `request.changeSessionId()` | Pre-login cookie $\ne$ post-login cookie | ✅ PASS |
| **AUTH-04** | Student Role Routing | `POST /attendance/login` (student) | HTTP 302 redirect to `/student/dashboard` | Redirected to `/student/dashboard` | ✅ PASS |
| **AUTH-05** | Logout Invalidation | `GET /attendance/logout` | Session invalidated, redirected to `/login` | HTTP 302 to `/login`, session destroyed | ✅ PASS |
| **RBAC-01** | Unauthenticated Access | `GET /attendance/teacher/dashboard` | HTTP 302 redirect to `/login` | HTTP 302 to `/attendance/login` | ✅ PASS |
| **RBAC-02** | Student $\rightarrow$ Faculty RBAC | `GET /attendance/teacher/dashboard` (student session) | **HTTP 403 Forbidden** | HTTP 403 Forbidden | ✅ PASS |
| **RBAC-03** | Faculty $\rightarrow$ Student RBAC | `GET /attendance/student/dashboard` (faculty session) | **HTTP 403 Forbidden** | HTTP 403 Forbidden | ✅ PASS |
| **CSRF-01** | Eager Token Generation | `GET /attendance/teacher/dashboard` | Form contains non-empty UUID CSRF token | Hidden input `csrfToken` UUID present | ✅ PASS |
| **CSRF-02** | Missing CSRF Rejection | `POST /attendance/teacher/planned-sessions` (no token) | **HTTP 403 Forbidden** | HTTP 403 Forbidden | ✅ PASS |
| **PLAN-01** | Planned Session in Faculty UI | `GET /attendance/teacher/dashboard` | HTML contains session room/course code | `CS1601` and `Lab 16A` rendered | ✅ PASS |
| **PLAN-02** | Planned Session in Timetable | `GET /attendance/student/dashboard` | HTML contains student timetable entry | `CS1601` and `Lab 16A` rendered | ✅ PASS |
| **QR-01** | Dynamic QR Generation | `GET /attendance/qr/generate?courseId=...` | Active QR token and 6-digit fallback | 6-digit numeric fallback code returned | ✅ PASS |
| **SCAN-01** | Enrolled Student Scan | `POST /attendance/qr/scan` | HTTP 200, JSON success, DB marks `PRESENT` | Success: true; DB row verified `PRESENT` | ✅ PASS |
| **SCAN-02** | Duplicate Scan Rejection | `POST /attendance/qr/scan` (repeated) | HTTP 200, JSON success: false, duplicate msg | "Attendance already marked for this session" | ✅ PASS |
| **SCAN-03** | Unenrolled Student Scan | `POST /attendance/qr/scan` (unenrolled) | JSON success: false, rejection error | "You are not enrolled in this course" | ✅ PASS |
| **SCAN-04** | Expired QR Token Scan | `POST /attendance/qr/scan` (stale token) | JSON success: false, expired error | "Invalid or expired QR code" | ✅ PASS |
| **CLOSE-01** | Faculty Session Closure | `POST /attendance/qr/close` | QR deactivated, planned session `COMPLETED`, absentees marked | `qr_sessions.is_active=0`, planned status `COMPLETED`, DB absentees reconciled | ✅ PASS |
| **HIST-01** | Historical Roster: Present | `GET /attendance/teacher/planned-sessions/roster` | Roster table lists present student | Student 1 listed with status `PRESENT` | ✅ PASS |
| **HIST-02** | Historical Roster: Absent | `GET /attendance/teacher/planned-sessions/roster` | Roster table lists reconciled absentee | Student 2 listed with status `ABSENT` | ✅ PASS |
| **HIST-03** | Historical Drop Retention | `GET /attendance/teacher/planned-sessions/roster` | Student dropped *after* closure retained | Student 4 retained with status `PRESENT` | ✅ PASS |
| **HIST-04** | Historical Drop Exclusion | `GET /attendance/teacher/planned-sessions/roster` | Student dropped *before* or enrolled *after* excluded | Student 5 (pre-drop) & Student 6 (post-enroll) excluded | ✅ PASS |

---

## 5. Real-Browser Automation Results (Google Chrome 156 via CDP)

Headless Google Chrome 156.0.8078.74 was driven over native Chrome DevTools Protocol (CDP) WebSocket to verify client-side behavior against the deployed application:

| Test ID | UI Component / Action | Verification Detail | Result | Status |
|---|---|---|---|---|
| **BROWSER-01** | CDP WebSocket Connection | Direct JSON handshake on `http://127.0.0.1:9222` | Connected cleanly to Chrome CDP endpoint | ✅ PASS |
| **BROWSER-02** | Faculty Login & Dashboard | Form submission, navigation, and heading checks | H1 rendered: "Course & Semester Management" | ✅ PASS |
| **BROWSER-03** | Planned Session UI Rendering | Inspect planned session card in faculty dashboard | Course card rendered for `CS1602` | ✅ PASS |
| **BROWSER-04** | Dynamic 7s QR Rotation | Observed live over 8.0s waiting period | Code 1: `326580` $\rightarrow$ Code 2: `032520` | ✅ PASS |
| **BROWSER-05** | Student Portal & Timetable | Student authentication, timetable table checks | H1 rendered: "Academic Portal & Attendance" | ✅ PASS |
| **BROWSER-06** | Camera Scanner Viewfinder | Scanner page DOM inspection | Viewfinder elements and manual fallback UI verified | ✅ PASS |
| **BROWSER-07** | Camera Hardware Feed | Physical video camera capture device stream | **NOT TESTED** (Headless server environment has no physical camera hardware) | ⚠️ **NOT TESTED** |
| **BROWSER-08** | Faculty Roster Modal & ESC | Modal open trigger, backdrop, and keyboard `Escape` | `Escape` key event dismissed active modal cleanly | ✅ PASS |
| **BROWSER-09** | Responsive Layouts | Desktop (`1920x1080`) vs Mobile (`375x667`) | Mobile body width: `375px`; Desktop body width: `1905px` | ✅ PASS |
| **BROWSER-10** | Console Telemetry | Check for uncaught JS errors on all pages | 0 uncaught script errors reported | ✅ PASS |

---

## 6. Documented Limitations & Honest Assessment

In strict accordance with release verification principles, the physical camera feed is honestly documented:
- **Physical Camera Limitation (`NOT TESTED`):** The automated test environment utilizes headless Chrome running inside a headless CI/server container without physical webcam hardware attached. While the HTML5 `<video>` viewfinder DOM container, camera selection dropdown, and manual fallback entry UI were verified, the physical optical capture of a projected QR code by a camera sensor cannot be physically executed in this environment.
- **Manual Attendance Fallback:** Verified functional. If camera access is restricted or unsupported by client hardware, students and teachers retain the verified manual 6-digit numeric code entry path.

---

## 7. Post-Verification Hygiene & Cleanup Evidence

Targeted cleanup was executed immediately following test suite completion:
1. **Database Fixtures:**
   - `SELECT COUNT(*) FROM users WHERE email LIKE '%@test.com'` $\rightarrow$ **0**
   - `SELECT COUNT(*) FROM courses WHERE course_code LIKE 'CS16%'` $\rightarrow$ **0**
   - `SELECT COUNT(*) FROM planned_sessions WHERE room LIKE 'Lab 16%'` $\rightarrow$ **0**
   - All synthetic records across `users`, `teachers`, `students`, `courses`, `enrollments`, `planned_sessions`, `qr_sessions`, and `attendance` were deleted.
2. **Tomcat Lifecycle:**
   - Tomcat daemon task `task-4137` terminated cleanly.
   - Loopback connection check confirmed port 8080 released.
   - Context descriptor `tomcat/conf/Catalina/localhost/attendance.xml` removed.
   - Webapp directory restored to baseline state.

---

## 8. Artifact Ledger & Checksum Invariance

| Artifact | Location | Size (Bytes) | SHA-256 Checksum | Invariance Status |
|---|---|---:|---|---|
| **Original dist WAR** | `dist\attendance.war` | 3,225,858 | `C80AC9FBDAFBC326E3AADACACC720D93C105B40D460848385C0A10A608673E33` | ✅ UNCHANGED |
| **Original Tomcat WAR** | `tomcat\webapps\attendance.war` | 3,225,858 | `C80AC9FBDAFBC326E3AADACACC720D93C105B40D460848385C0A10A608673E33` | ✅ UNCHANGED |
| **Stage 15 Candidate WAR** | `dist\attendance-stage15-candidate.war` | 3,061,197 | `3637897BA009A476F1C793B5A3406F08CC6390AD2F5EF7990BB63BEE885B922B` | ✅ UNCHANGED |
| **Git Baseline** | Working tree HEAD | — | `9b59559e0ef5c5a5e3e29be1eaf131fad8442d0b` | ✅ UNCHANGED |

Zero Git commits, zero Git stages, and zero Git resets were executed.

---

## 9. Final Verdict

### **CONDITIONAL PASS — HTTP deployment verified with documented limitations (physical camera hardware limitation)**

**Release Recommendation:**
- **College Demonstration:** **APPROVED**. The application deploys cleanly to Apache Tomcat 10.1, passes all 25 live HTTP smoke tests, passes all real-browser DOM and dynamic rotation verifications, and enforces authentication, RBAC, CSRF, and soft-drop historical roster retention. For live demonstration, a physical smartphone or laptop with an operational camera should be used, or the verified 6-digit numeric fallback may be demonstrated.
- **Staging Deployment:** **APPROVED**.
- **Production Deployment:** **CONDITIONAL** on physical camera testing on client hardware and configuring `SESSION_COOKIE_SECURE=true` behind HTTPS/TLS reverse proxy.

