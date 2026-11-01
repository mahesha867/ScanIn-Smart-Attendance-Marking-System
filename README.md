# ⚡ ScanIn — Smart Attendance Marking System
### Dynamic QR Attendance with 7-Second Cryptographic Rotation & Anti-Proxy Architecture

[![Live on Render](https://img.shields.io/badge/Render-Live%20Deployment-success?logo=render&logoColor=white)](https://scanin-attendance.onrender.com/)
[![Stage 8 Status](https://img.shields.io/badge/Stage%208-APPROVED-brightgreen.svg?logo=checkmarx&logoColor=white)]()
[![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://www.oracle.com/java/)
[![Tomcat 10.1](https://img.shields.io/badge/Tomcat-10.1.20-blue.svg)](https://tomcat.apache.org/)
[![Jakarta Servlet 6.0](https://img.shields.io/badge/Jakarta%20Servlet-6.0-blueviolet.svg)](https://jakarta.ee/)
[![MySQL 8.0 / TiDB](https://img.shields.io/badge/MySQL%20%2F%20TiDB-8.0%20Compatible-blue.svg)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-Containerized-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)
[![Automated Tests](https://img.shields.io/badge/Automated%20Tests-92%2F92%20PASS-brightgreen.svg)]()

---

## 🌐 Live Production Deployment

- **Production URL:** [https://scanin-attendance.onrender.com/](https://scanin-attendance.onrender.com/)
- **Hosting Platform:** Render Web Service (Dockerized Apache Tomcat 10.1 / OpenJDK 21)
- **Database Engine:** TiDB Cloud Serverless (MySQL 8.0 Protocol Compatible, TLS/SSL Encrypted)
- **Zero-Config Migrations:** Self-healing automated schema migrations on container startup via [`AppContextListener`](file:///c:/java_dbms_project/src/main/java/com/attendance/listener/AppContextListener.java).
- **Stage Approval Status:** **Stage 8 Approved** (Final Academic & Security Verification Passed).

---

## 📋 Executive Overview

**ScanIn** is an enterprise-grade academic attendance management system engineered in **Java 21 LTS**, **Jakarta Servlet 6.0**, and **MySQL 8.0 / TiDB Cloud**, containerized with **Docker** and deployed on **Apache Tomcat 10.1.20**.

The system prevents classroom proxy attendance and screenshot reuse through:
1. **7-Second Dynamic HMAC-SHA256 QR Tokens:** Cryptographically signed tokens rotate continuously every 7 seconds on the instructor's display. All enrolled students in the room can scan the active token during its 10-second validity window (7s display rotation + 3s network transmission tolerance).
2. **6-Digit Session-Bound Numeric Fallback Code:** A synchronized 6-digit numeric OTP derived from HMAC dynamic truncation for students with camera or browser issues, rotating every 7 seconds and bound to the active course/session with multi-window tolerance.
3. **Throttled Camera Scanner & Anti-Flicker Protection:** Client-side scanner pauses camera stream during token submission and enforces a 2.5-second cooling-off period on invalid attempts to eliminate rapid re-scanning loops.
4. **Database-Backed Rate Limiting:** Rapid guessing protection in MySQL/TiDB (`scan_attempts`) blocks student accounts after $\ge 5$ failed attempts within 60 seconds, persisting across server restarts.
5. **Strict ACID Concurrency Handling:** Multi-thread race conditions during simultaneous student scans are resolved using database constraints (`UNIQUE KEY (student_id, qr_session_id)`) and automatic transaction rollbacks.
6. **Application-Enforced Append-Only Audit Trail:** Any reasoned manual status override (On-Duty `OD`, Medical `EXCUSED`, `PRESENT`, `ABSENT`, `LATE`) is logged with actor user ID, timestamp, and justification.
7. **Centralized Role-Based Access Control:** Strict server-side filtering (`SecurityFilter`) ensures role boundaries (students receive HTTP 403 Forbidden when probing faculty resources).

---

## 🏛️ Object-Oriented Programming (OOP) Architecture

The application strictly models core domain concepts using Java OOP principles:

```mermaid
classDiagram
    class User {
        <<abstract>>
        #int id
        #String name
        #String email
        #String password
        #String role
        #String phone
        +getDashboardPath()* String
        +getRoleInfo()* String
    }

    class Teacher {
        -int teacherId
        -String employeeId
        -String department
        +getDashboardPath() String
        +getRoleInfo() String
    }

    class Student {
        -int studentId
        -String rollNumber
        -String department
        -int semester
        +getDashboardPath() String
        +getRoleInfo() String
        +canMarkAttendance(String token) boolean
    }

    class Attendable {
        <<interface>>
        +canMarkAttendance(String token)* boolean
    }

    class Dao~T~ {
        <<interface>>
        +findById(int id)* Optional~T~
        +findAll()* List~T~
        +save(T entity)* boolean
        +update(T entity)* boolean
        +delete(int id)* boolean
    }

    User <|-- Teacher : Inheritance
    User <|-- Student : Inheritance
    Attendable <|.. Student : Realization
```

### OOP Principles to Code Mapping

| Principle | Concrete Implementation | Source Location |
|---|---|---|
| **Abstraction** | Abstract base class `User` defines template methods `getDashboardPath()` and `getRoleInfo()`, hiding role routing complexity. | [`User.java`](file:///c:/java_dbms_project/src/main/java/com/attendance/model/User.java) |
| **Inheritance** | `Teacher` and `Student` inherit identity attributes (`id`, `name`, `email`, `password`, `phone`) while extending domain-specific fields (`employeeId`, `rollNumber`, `semester`). | [`Teacher.java`](file:///c:/java_dbms_project/src/main/java/com/attendance/model/Teacher.java), [`Student.java`](file:///c:/java_dbms_project/src/main/java/com/attendance/model/Student.java) |
| **Polymorphism** | Runtime dynamic dispatch via `user.getDashboardPath()` and polymorphic contract `student.canMarkAttendance(token)` in scanning pipeline. | [`LoginServlet.java`](file:///c:/java_dbms_project/src/main/java/com/attendance/servlet/LoginServlet.java), [`QrScanServlet.java`](file:///c:/java_dbms_project/src/main/java/com/attendance/servlet/QrScanServlet.java) |
| **Encapsulation** | Strict `private` state across all entities, validated via getters and setters. Password hashes and cryptographic secrets never exposed. | Model package (`com.attendance.model.*`) |
| **Interface Segregation** | Type-safe generic data access contract `Dao<T>` and scanning capability contract `Attendable`. | [`Dao.java`](file:///c:/java_dbms_project/src/main/java/com/attendance/dao/Dao.java), [`Attendable.java`](file:///c:/java_dbms_project/src/main/java/com/attendance/model/Attendable.java) |

---

## 🗄️ Relational Database Schema & Architecture

The database `attendance_db` uses MySQL 8.0 InnoDB storage engine with strict foreign key constraints, unique composite indices, and pooled connections via Tomcat JDBC Pool (`maxActive=20`, `testOnBorrow=true`).

```mermaid
erDiagram
    users ||--o| teachers : "profile"
    users ||--o| students : "profile"
    teachers ||--o{ courses : "instructs"
    courses ||--o{ enrollments : "enrolled in"
    students ||--o{ enrollments : "takes"
    courses ||--o{ course_schedules : "timetable"
    courses ||--o{ qr_sessions : "conducts"
    qr_sessions ||--o{ attendance : "records"
    students ||--o{ attendance : "marks"
    attendance ||--o{ attendance_audit_log : "tracked by"
    qr_sessions ||--o{ scan_attempts : "attempts"

    users {
        int id PK
        varchar name
        varchar email UK
        varchar password "BCrypt $2a$10"
        enum role "TEACHER, STUDENT"
        timestamp created_at
    }

    students {
        int student_id PK
        int user_id FK
        varchar roll_number UK
        varchar department
        int semester
    }

    teachers {
        int teacher_id PK
        int user_id FK
        varchar employee_id UK
        varchar department
    }

    courses {
        int id PK
        varchar course_code UK
        varchar course_name
        int teacher_id FK
    }

    qr_sessions {
        int id PK
        varchar session_token UK
        int course_id FK
        int teacher_id FK
        timestamp expires_at
        boolean is_active
        timestamp closed_at
    }

    attendance {
        int id PK
        int student_id FK
        int course_id FK
        int qr_session_id FK
        enum status "PRESENT, ABSENT, LATE, OD, EXCUSED"
        timestamp marked_at
    }

    attendance_audit_log {
        int id PK
        int attendance_id FK
        int student_id FK
        int session_id FK
        int changed_by_user_id FK
        varchar old_status
        varchar new_status
        text reason
        timestamp created_at
    }

    scan_attempts {
        int id PK
        int session_id FK
        int student_id FK
        varchar token_hash "SHA-256 only"
        varchar outcome "SUCCESS, INVALID, EXPIRED, REPLAYED, DUPLICATE, WRONG_COURSE, CLOSED_SESSION, RATE_LIMITED"
        timestamp attempted_at
    }
```

---

## 🛡️ Anti-Proxy & Security Architecture

1. **7-Second Dynamic HMAC-SHA256 Token Rotation:**
   - Server signs payloads containing `sessionId.courseId.timestampMs.nonce` using a constant-time HMAC-SHA256 signature.
   - Tokens expire strictly after 10 seconds (7-second display rotation + 3-second network transmission grace). Expired tokens are immediately rejected as `EXPIRED`.
2. **Replay Attack Neutralization:**
   - As soon as a student marks attendance, the token hash is logged in `scan_attempts`. Subsequent attempts with the same token are rejected as `REPLAYED` or `DUPLICATE`.
3. **Session-Bound 6-Digit Numeric Fallback Code:**
   - Derived using TOTP-style dynamic truncation (`computeNumericCode`) rotating synchronously with the QR code every 7 seconds.
   - Strictly course-bound and session-bound: students cannot use a code for a course they are not enrolled in.
4. **Throttled Camera Scanner & Anti-Flicker Protection:**
   - The in-app camera scanner (`qr-scan.jsp`) pauses video frame analysis during token verification and enforces a 2.5-second cooling-off period on invalid attempts to eliminate rapid re-scanning loops.
5. **Rapid Guessing & Brute-Force Rate Limiting:**
   - If an account accumulates $\ge 5$ failed scans within a 60-second sliding window, `QrScanServlet` blocks further requests with `RATE_LIMITED`.
6. **ACID Concurrency & Race-Condition Safe:**
   - Database constraint `UNIQUE KEY unique_attendance (student_id, qr_session_id)` prevents concurrent double-marking. Rolled back automatically on code 1062.
7. **Centralized Server-Side RBAC (`SecurityFilter`):**
   - Direct URL tampering (e.g. students trying to access `/reports`, `/qr/generate`, or `/teacher/*`) triggers **HTTP 403 Forbidden**.
8. **BCrypt Password Storage:**
   - All passwords hashed using salted BCrypt (`$2a$10$`). Plaintext storage and hardcoded bypasses completely removed.
9. **Data Minimization:**
   - Raw reusable tokens and IP addresses are never persisted. Only one-way SHA-256 hashes are stored in `scan_attempts`.

---

## 📊 Standardized Attendance Calculation

Implemented uniformly across all database queries, Student Academics Dashboard, and Faculty Course Rosters:

$$\text{Attendance Percentage} = \frac{\text{Attended Sessions (PRESENT + LATE + OD)}}{\text{Total Sessions Conducted} - \text{EXCUSED}} \times 100$$

- **`OD` (On-Duty Approved):** Counted in the attended numerator.
- **`EXCUSED` (Medical / Formal Leave):** Deducted from the total sessions denominator (reducing the denominator burden).
- **Compliance Threshold:** Minimum 75.0%. System displays visual alert badges (`Low Attendance < 75%`) if attendance dips below compliance.

---

## 🚀 Quick Start & Deployment

### Option A: Cloud Production Deployment (Render + TiDB Cloud)
The system is fully containerized and production-ready on Render with TiDB Cloud Serverless:
- **Live URL:** [https://scanin-attendance.onrender.com/](https://scanin-attendance.onrender.com/)
- **Configuration:** Defined in [`render.yaml`](file:///c:/java_dbms_project/render.yaml) and [`Dockerfile`](file:///c:/java_dbms_project/Dockerfile).
- **Environment Variables:**
  - `DB_URL`: `jdbc:mysql://<host>:4000/attendance_db?sslMode=VERIFY_IDENTITY`
  - `DB_USER`: Database username
  - `DB_PASSWORD`: Database password

### Option B: Local 1-Click Launch (Windows)
#### Prerequisites
- **Java 21 LTS** installed and available on `PATH`.
- **MySQL 8.0** running on `localhost:3306` with credentials `root` / `root`.

#### 1-Click Launch
Execute the build and run scripts in Command Prompt or PowerShell:
```bat
.\build.bat
.\run.bat
```
The application will launch on embedded Apache Tomcat 10.1:
👉 **[http://localhost:8080/attendance/login](http://localhost:8080/attendance/login)**

---

## 🔑 Demo Credentials

| Role | Name | Email | Password | Primary Courses |
|---|---|---|---|---|
| **Faculty (Teacher 1)** | Prof. Ramesh Kumar | `teacher@attendance.com` | `teacher123` | CS501 (DBMS), CS502 (OS) |
| **Faculty (Teacher 2)** | Prof. Sunita Sharma | `sunita@attendance.com` | `teacher123` | IT501 (Web Tech) |
| **Student 1** | Aarav Singh | `aarav@student.com` | `student123` | CS501, CS502 |
| **Student 2** | Priya Sharma | `priya@student.com` | `student123` | CS501, CS502 |
| **Student 3** | Rahul Verma | `rahul@student.com` | `student123` | CS501 |

*(The login page features 1-click credential autofill buttons for rapid demonstration.)*

---

## ⏱️ 3–5 Minute Evaluator Presentation & Demo Script

Follow this structured walkthrough when presenting to evaluators or faculty panels:

### Phase 1: Authentication & Role Boundary Isolation (1 Minute)
1. Navigate to `/attendance/login`. Click the **Teacher** autofill pill and sign in as `teacher@attendance.com`.
2. Demonstrate the Faculty Dashboard displaying managed courses (CS501, CS502), enrolled counts, and timetable.
3. Open an Incognito window, log in as `aarav@student.com` (Student).
4. In the student window, attempt to navigate directly to `/attendance/reports` or `/attendance/teacher/dashboard`.
5. **Key Takeaway for Evaluators:** Point out the immediate **HTTP 403 Forbidden** error response enforced by the server-side `SecurityFilter`.

### Phase 2: Dynamic Live QR & Anti-Proxy Rotation (1.5 Minutes)
1. On the Faculty screen, click **"📱 Launch Live QR Session"** for CS501.
2. Observe the dynamic 7-second countdown timer. Show evaluators how the QR code and the **6-digit numeric fallback code** (e.g. `431944`) regenerate synchronously every 7 seconds.
3. On the Student screen (or mobile device camera), click **"📷 Launch Camera Scanner"** or scan directly.
4. Scan the live QR code or type the active 6-digit numeric fallback code.
5. Immediately observe:
   - Student receives confirmation: *"Attendance successfully marked PRESENT!"*
   - Faculty projector counter instantly increments: *"1 student marked"*.
6. Submit the exact same code a second time to show duplicate rejection (*"Attendance was already marked for this session"*).
7. Submit an expired code or rapid random guesses to show rate-limiting protection (*"Rapid guessing blocked. Please wait 1 minute"*).

### Phase 3: Session Reopen, Reasoned Manual Overrides & Audit Trail (1.5 Minutes)
1. On the Faculty screen, click **"⏹ End Session & Mark Absentees"**.
2. Explain to evaluators how absentee reconciliation automatically marks all unmarked enrolled students `ABSENT` in a single atomic query.
3. Navigate to **"📊 Reports & Audit Trail"** (`/attendance/reports?courseId=1`).
4. Click **"✏️ Edit Status"** for an absent student:
   - Select **`OD` (On-Duty)** or **`EXCUSED` (Medical Leave)**.
   - Enter a mandatory audit reason: *"Representing college at Inter-University Coding Hackathon"*.
   - Click **Save with Audit Trail**.
5. Scroll down to the **"🛡️ Immutable Attendance Audit History"** table:
   - Highlight the permanent audit log displaying: Timestamp, Student Name, Faculty Actor (`teacher@attendance.com`), Status Transition (`ABSENT → OD`), and formal Reason.
6. Click **"📥 Download CSV Report"** to export the live audit report directly into an Excel/CSV file.
7. Switch back to Student Dashboard to show the real-time updated Attendance % applying the $(Attended / (Total - Excused))$ formula.

---

## 🧪 Comprehensive Automated Test Evidence

The repository includes complete automated test suites executing live against Tomcat 10.1.20 and MySQL 8.0.46:

```bash
# Compile test suites into bin
javac -encoding UTF-8 -cp "lib/*;tomcat/lib/*;src/main/webapp/WEB-INF/classes" -d bin src/test/java/com/attendance/*.java

# Run full regression suites
java -cp "bin;lib/*;tomcat/lib/*;src/main/webapp/WEB-INF/classes" com.attendance.Stage2VerificationTest
java -cp "bin;lib/*;tomcat/lib/*;src/main/webapp/WEB-INF/classes" com.attendance.Stage3VerificationTest
java -cp "bin;lib/*;tomcat/lib/*;src/main/webapp/WEB-INF/classes" com.attendance.Stage4VerificationTest
java -cp "bin;lib/*;tomcat/lib/*;src/main/webapp/WEB-INF/classes" com.attendance.Stage5VerificationTest
java -cp "bin;lib/*;tomcat/lib/*;src/main/webapp/WEB-INF/classes" com.attendance.Stage6VerificationTest
java -cp "bin;lib/*;tomcat/lib/*;src/main/webapp/WEB-INF/classes" com.attendance.Stage7VerificationTest
java -cp "bin;lib/*;tomcat/lib/*;src/main/webapp/WEB-INF/classes" com.attendance.Stage8VerificationTest
java -cp "bin;lib/*;tomcat/lib/*;src/main/webapp/WEB-INF/classes" com.attendance.ClassMeetingAttendanceVerificationTest
java -cp "bin;lib/*;tomcat/lib/*;src/main/webapp/WEB-INF/classes" com.attendance.QrTimingVerificationTest
java -cp "bin;lib/*;tomcat/lib/*;src/main/webapp/WEB-INF/classes" com.attendance.TimezoneVerificationTest
```

| Verification Suite | Target Areas | Tests | Result | Stage Status |
|---|---|---|---|---|
| [`Stage2VerificationTest.java`](file:///c:/java_dbms_project/src/test/java/com/attendance/Stage2VerificationTest.java) | Connection Pooling, BCrypt, Schema Constraints, Data Minimization, Concurrency Rollback | 7 / 7 | ✅ PASS | Stage 2 Approved |
| [`Stage3VerificationTest.java`](file:///c:/java_dbms_project/src/test/java/com/attendance/Stage3VerificationTest.java) | BCrypt Authentication, Legacy Migration, Unauthenticated Redirection, RBAC 403 Forbidden | 7 / 7 | ✅ PASS | Stage 3 Approved |
| [`Stage4VerificationTest.java`](file:///c:/java_dbms_project/src/test/java/com/attendance/Stage4VerificationTest.java) | Student Official Record, Registration Rules, Drop Flow, Timetable, Low Attendance Warning | 7 / 7 | ✅ PASS | Stage 4 Approved |
| [`Stage5VerificationTest.java`](file:///c:/java_dbms_project/src/test/java/com/attendance/Stage5VerificationTest.java) | Faculty Course CRUD, Cross-Faculty Edit/Delete Blocking, Roster Authorization, Schedule Validation | 7 / 7 | ✅ PASS | Stage 5 Approved |
| [`Stage6VerificationTest.java`](file:///c:/java_dbms_project/src/test/java/com/attendance/Stage6VerificationTest.java) | 7s HMAC Token Rotation, Multi-Student Active QR, Expired/Replay/Duplicate Rejection, Auto-Close, Concurrent Scans | 11 / 11 | ✅ PASS | Stage 6 Approved |
| [`Stage7VerificationTest.java`](file:///c:/java_dbms_project/src/test/java/com/attendance/Stage7VerificationTest.java) | Session Reopen/Reclose, Reasoned Manual Overrides, Append-Only Audit Trail, CSV Export, 6-Digit Code | 8 / 8 | ✅ PASS | Stage 7 Approved |
| [`Stage8VerificationTest.java`](file:///c:/java_dbms_project/src/test/java/com/attendance/Stage8VerificationTest.java) | Session-Bound 6-Digit Code, Database-Backed Rate Limiting, Formula Uniformity, Full Regression | 5 / 5 | ✅ PASS | **Stage 8 Approved** |
| [`ClassMeetingAttendanceVerificationTest.java`](file:///c:/java_dbms_project/src/test/java/com/attendance/ClassMeetingAttendanceVerificationTest.java) | Persistent Class Meetings, Ephemeral 7s QR Tokens, UTC Timezone, Reopen Idempotency, Reporting Invariance, Merged Session Exclusion | 10 / 10 | ✅ PASS | Stage 9 Approved |
| [`QrTimingVerificationTest.java`](file:///c:/java_dbms_project/src/test/java/com/attendance/QrTimingVerificationTest.java) | 7-Second QR Rotation, 10s Token Validity, Monotonic Synchronization, Step Invariance, Security Regressions | 23 / 23 | ✅ PASS | Stage 10 Approved |
| [`TimezoneVerificationTest.java`](file:///c:/java_dbms_project/src/test/java/com/attendance/TimezoneVerificationTest.java) | Indian Standard Time (IST) Display, UTC Storage Invariance, Midnight Crossing, DB/CSV Reporting | 7 / 7 | ✅ PASS | Stage 11 Approved |
| **Total Automated Tests** | **All Layers & Life-Cycle Flows (7+7+7+7+11+8+5+10+23+7)** | **92 / 92** | **✅ 100% PASS** | **All Stages Approved** |

---

## ⚠️ Known Limitations for Academic Demonstration

When presenting or analyzing this project in an academic setting, note the following verified boundaries:
1. **Live Video Relay / Screen Mirroring:** Short-lived (5-second) rotating tokens prevent screenshot reuse, but cannot prevent an in-class student from streaming their screen or sharing a live video feed to an absent peer outside the classroom.
2. **Physical Device Sharing:** The system does not incorporate biometric device binding or GPS geofencing; an attendee physically present in the room could potentially log into an absent student's account on a shared device.
3. **Database-Level Immutability:** The audit log is append-only at the application DAO layer (`AttendanceAuditLogDao` only implements `INSERT` and `SELECT`), but MySQL administrative users with direct database access (`root`) retain raw DML rights unless database-level table privileges are restricted.
4. **Rate Limiting Scope:** Rate limiting is enforced strictly per `student_id` in the database. Client IP rate limiting is intentionally not enforced to comply with student privacy data minimization principles.

---

## 👥 Authors & Academic Context
- **Project:** ScanIn Smart Attendance Marking System
- **Runtime Environment:** OpenJDK 21 LTS, Apache Tomcat 10.1.20, Jakarta Servlet 6.0, MySQL 8.0.46
- **Course / Degree:** Object-Oriented Programming & Database Management Systems Project
