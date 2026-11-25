# ☕ Java & Object-Oriented Programming (OOP) Implementation Guide — ScanIn

This comprehensive technical document provides a clean, in-depth, and well-structured explanation of the **Java architecture**, **Object-Oriented Programming (OOP)** paradigms, **design patterns**, **concurrency/thread safety**, and **cryptographic primitives** implemented across the **ScanIn Smart Attendance Marking System**.

Use this document as an authoritative technical reference for project presentations, viva defenses, architectural reviews, and developer onboarding.

---

## 📑 Table of Contents
1. [Core Architectural Overview](#1-core-architectural-overview)
2. [Object-Oriented Programming (OOP) Pillars](#2-object-oriented-programming-oop-pillars)
   - 2.1 [Abstraction (`User.java`, `Dao<T>`)](#21-abstraction--userjava-daot)
   - 2.2 [Inheritance (`Teacher.java`, `Student.java`)](#22-inheritance--teacherjava-studentjava)
   - 2.3 [Polymorphism & Dynamic Dispatch (`LoginServlet.java`)](#23-polymorphism--dynamic-dispatch--loginservletjava)
   - 2.4 [Encapsulation & Defensive Design (Domain Models)](#24-encapsulation--defensive-design-domain-models)
   - 2.5 [Interface Contracts (`Dao<T>`, `Attendable.java`)](#25-interface-contracts--daot-attendablejava)
3. [Software Design Patterns](#3-software-design-patterns)
   - 3.1 [Singleton Pattern (`DBConnection.java`)](#31-singleton-pattern--dbconnectionjava)
   - 3.2 [Data Access Object (DAO) Pattern](#32-data-access-object-dao-pattern)
   - 3.3 [Model-View-Controller (MVC) Architectural Pattern](#33-model-view-controller-mvc-architectural-pattern)
   - 3.4 [Utility / Pure Static Class Pattern (`QrCodeUtil`, `DateTimeUtil`)](#34-utility--pure-static-class-pattern--qrcodeutil-datetimeutil)
   - 3.5 [Lifecycle State Pattern (`CourseSchedule.java`)](#35-lifecycle-state-pattern--courseschedulejava)
4. [Enterprise Web Architecture (Jakarta Servlet & Filter API)](#4-enterprise-web-architecture-jakarta-servlet--filter-api)
   - 4.1 [Servlet Lifecycle & Request Pipeline](#41-servlet-lifecycle--request-pipeline)
   - 4.2 [Interception & Role-Based Access Control (`SecurityFilter.java`)](#42-interception--role-based-access-control--securityfilterjava)
   - 4.3 [HTTP State & Session Management (`SessionUtil.java`)](#43-http-state--session-management--sessionutiljava)
   - 4.4 [Application Bootstrapping (`AppContextListener.java`)](#44-application-bootstrapping--appcontextlistenerjava)
5. [Database Persistence & Concurrency (JDBC)](#5-database-persistence--concurrency-jdbc)
   - 5.1 [SQL Injection Prevention via `PreparedStatement`](#51-sql-injection-prevention-via-preparedstatement)
   - 5.2 [Automatic Resource Management (`try-with-resources`)](#52-automatic-resource-management--try-with-resources)
   - 5.3 [Transaction Demarcation (`commit` & `rollback`)](#53-transaction-demarcation--commit--rollback)
   - 5.4 [Handling Database Race Conditions (Error Code 1062)](#54-handling-database-race-conditions-error-code-1062)
6. [Cryptography & Anti-Proxy Security Engine](#6-cryptography--anti-proxy-security-engine)
   - 6.1 [HMAC-SHA256 Cryptographic Token Generation](#61-hmac-sha256-cryptographic-token-generation)
   - 6.2 [Constant-Time Signature Verification (Anti-Timing Attacks)](#62-constant-time-signature-verification-anti-timing-attacks)
   - 6.3 [6-Digit TOTP Dynamic Truncation (RFC 6238 Fallback)](#63-6-digit-totp-dynamic-truncation-rfc-6238-fallback)
   - 6.4 [Adaptive Salting & BCrypt Password Hashing](#64-adaptive-salting--bcrypt-password-hashing)
7. [Modern Java LTS Idioms & Quality Standards](#7-modern-java-lts-idioms--quality-standards)
   - 7.1 [Null Safety with `Optional<T>`](#71-null-safety-with-optionalt)
   - 7.2 [Modern Date-Time API (`java.time.*`) & Centralized UTC/IST Engine](#72-modern-date-time-api-javatime-centralized-utcist-engine)
   - 7.3 [Type-Safe Generics & Collections Framework](#73-type-safe-generics--collections-framework)
   - 7.4 [Thread-Safety & Multi-Threading Model](#74-thread-safety--multi-threading-model)
8. [Summary Mapping (Concepts → Source Files)](#8-summary-mapping-concepts--source-files)
9. [Viva Defense / Interview Questions & Master Answers](#9-viva-defense--interview-questions--master-answers)

---

## 1. Core Architectural Overview

ScanIn is built using **Java 21 LTS** running on the **Apache Tomcat 10.1+ (Jakarta EE 10)** servlet container. The application enforces a strict **Model-View-Controller (MVC)** separation of concerns:

```
[ Client Browser / Scanner ]
           │  HTTP Requests (GET / POST)
           ▼
[ SecurityFilter (RBAC) ] ── (Rejects unauthorized sessions / redirects to /login)
           │
           ▼
   [ Controller Layer ]
  (Jakarta Servlets: LoginServlet, QrScanServlet, QrGenerateServlet, ReportServlet)
           │
           ├──► Business / Crypto Logic: QrCodeUtil, PasswordUtil, DateTimeUtil
           │
           ▼
     [ Model Layer ]
 (Domain Entities: User, Teacher, Student, Course, QrSession, Attendance)
           │
           ▼
     [ DAO Layer ] ◄── Uses Generic Contract: Dao<T>
 (UserDao, StudentDao, TeacherDao, CourseDao, QrSessionDao, AttendanceDao)
           │
           ▼
   [ DBConnection ] ── (Singleton Connection Pool: Tomcat JDBC Pool)
           │
           ▼
 [ MySQL 8.0 / TiDB Serverless Cloud DB ]
```

---

## 2. Object-Oriented Programming (OOP) Pillars

OOP enables clean domain modeling, robust security boundaries, modular maintainability, and code reuse across the application.

```
                           ┌────────────────────────┐
                           │  <<abstract>> User     │
                           ├────────────────────────┤
                           │ - id: int              │
                           │ - name: String         │
                           │ - email: String        │
                           │ - password: String     │
                           ├────────────────────────┤
                           │ + getDashboardPath()*  │
                           │ + getRoleInfo()*       │
                           └───────────▲────────────┘
                                       │
                      ┌────────────────┴────────────────┐
                      │                                 │
           ┌──────────┴──────────┐           ┌──────────┴──────────┐
           │       Teacher       │           │       Student       │
           ├─────────────────────┤           ├─────────────────────┤
           │ - teacherId: int    │           │ - studentId: int    │
           │ - employeeId: String│           │ - rollNumber: String│
           │ - department: String│           │ - department: String│
           │                     │           │ - semester: int     │
           ├─────────────────────┤           ├─────────────────────┤
           │ + getDashboardPath()│           │ + getDashboardPath()│
           │ + getRoleInfo()     │           │ + getRoleInfo()     │
           └─────────────────────┘           └──────────▲──────────┘
                                                        │ implements
                                             ┌──────────┴──────────┐
                                             │    <<interface>>    │
                                             │     Attendable      │
                                             ├─────────────────────┤
                                             │+canMarkAttendance() │
                                             └─────────────────────┘
```

### 2.1 Abstraction (`User.java`, `Dao<T>`)

**Concept:** Abstraction isolates essential interface definitions from underlying execution mechanisms. In Java, abstraction is realized through `abstract` classes and `interface` declarations.

**In ScanIn:**
- In [`User.java`](../src/main/java/com/attendance/model/User.java), the class is declared `public abstract class User`. The system prevents instantiation of a generic "User" because every authenticated actor is either a faculty member or an enrolled student.
- The base class specifies abstract contractual methods that every subclass must implement:
  ```java
  public abstract class User {
      private int id;
      private String name;
      private String email;
      private String password; // BCrypt hashed string ($2a$10$...)
      private String role;     // TEACHER, STUDENT, ADMIN
      private String phone;
      private Timestamp createdAt;

      // Abstract template methods: WHAT to execute is mandated; HOW is delegated
      public abstract String getDashboardPath();
      public abstract String getRoleInfo();
  }
  ```
- **Architectural Benefit:** Higher-level application code interacts purely with `User` abstractions without coupling itself to concrete role hierarchies.

---

### 2.2 Inheritance (`Teacher.java`, `Student.java`)

**Concept:** Inheritance allows a specialized class to extend a generalized base class (`class Child extends Parent`), inheriting fields and method implementations while introducing domain-specific state.

**In ScanIn:**
- [`Teacher.java`](../src/main/java/com/attendance/model/Teacher.java) and [`Student.java`](../src/main/java/com/attendance/model/Student.java) both extend `User`:
  ```java
  public class Teacher extends User {
      private int teacherId;
      private String employeeId;
      private String department;

      @Override
      public String getDashboardPath() {
          return "/teacher/dashboard";
      }

      @Override
      public String getRoleInfo() {
          return "Faculty | " + department;
      }
  }

  public class Student extends User implements Attendable {
      private int studentId;
      private String rollNumber;
      private String department;
      private int semester;

      @Override
      public String getDashboardPath() {
          return "/student/dashboard";
      }

      @Override
      public String getRoleInfo() {
          return "Student | " + department + " | Sem " + semester;
      }
  }
  ```
- **Architectural Benefit:** Common identity fields (`id`, `name`, `email`, `password`, `createdAt`) are defined in exactly one place (`User.java`), eliminating duplicated fields across models.

---

### 2.3 Polymorphism & Dynamic Dispatch (`LoginServlet.java`)

**Concept:** Polymorphism allows a single method call to execute different runtime logic based on the actual concrete type of the object invoking it (dynamic method dispatch).

**In ScanIn:**
- In [`LoginServlet.java`](../src/main/java/com/attendance/servlet/LoginServlet.java), post-authentication routing is executed without fragile conditional branching:
  ```java
  // Polymorphic reference: runtime object can be Teacher or Student
  User user = userDao.findByEmail(email);

  // Dynamic dispatch resolves destination URL at runtime:
  String redirectUrl = request.getContextPath() + user.getDashboardPath();
  response.sendRedirect(redirectUrl);
  ```
- **Why this is superior:** No `if (user instanceof Teacher)` or `switch (user.getRole())` checks are required. If an `Admin` or `Auditor` role is introduced in the future, only a new `Admin extends User` class is created—`LoginServlet` requires **zero code modifications**, satisfying the **Open/Closed Principle (OCP)**.

---

### 2.4 Encapsulation & Defensive Design (Domain Models)

**Concept:** Encapsulation hides the internal state of an object, requiring all external operations to pass through controlled `public` accessor (getter) and mutator (setter) methods.

**In ScanIn:**
- All entity fields are declared strictly `private`.
- **Defensive Immutability:** Sensitive properties (such as password hashes, cryptographic salt values, and session IDs) do not expose open public setters.
  ```java
  public class QrSession {
      private int id;
      private String sessionToken;
      private boolean active;
      private LocalDateTime expiresAt;

      // Accessors provide controlled, read-only visibility
      public int getId() { return id; }
      public String getSessionToken() { return sessionToken; }
      public boolean isActive() { return active; }
      public LocalDateTime getExpiresAt() { return expiresAt; }

      // Mutators validate invariant boundaries before applying state changes
      public void setActive(boolean active) {
          this.active = active;
      }
  }
  ```
- **Password Security Guarantee:** The plain-text password is never stored or returned as a field anywhere in the application. Once hashed via BCrypt, only the irreversible hash is held in memory.

---

### 2.5 Interface Contracts (`Dao<T>`, `Attendable.java`)

**Concept:** An `interface` defines a contract of behavior without coupling callers to specific data stores, protocols, or storage engines.

#### 1. Generic Data Access Interface: `Dao<T>`
[`Dao.java`](../src/main/java/com/attendance/dao/Dao.java) standardizes CRUD operations across all database entities:
```java
public interface Dao<T> {
    Optional<T> findById(int id);
    List<T> findAll();
    boolean save(T entity);
    boolean update(T entity);
    boolean delete(int id);
}
```
All concrete DAO implementations (`UserDao`, `StudentDao`, `QrSessionDao`, `AttendanceDao`) implement this interface, guaranteeing consistent method signatures throughout the persistence layer.

#### 2. Scanner Behavioral Capability: `Attendable.java`
[`Attendable.java`](../src/main/java/com/attendance/model/Attendable.java) defines scanner participation:
```java
public interface Attendable {
    boolean canMarkAttendance(String token);
}
```
`Student` implements `Attendable`, allowing servlets to verify scanning privileges at compile-time rather than relying solely on string-based role comparisons.

---

## 3. Software Design Patterns

ScanIn incorporates proven enterprise Gang-of-Four (GoF) design patterns to solve common architectural challenges.

### 3.1 Singleton Pattern (`DBConnection.java`)

**Problem:** Establishing a new database connection over TCP/TLS takes ~100ms. If every incoming HTTP request opened a separate connection, the application would exhaust database file descriptors and crash under high concurrency.

**Solution:** The Singleton Pattern ensures only **one** instance of the connection pool manager exists in the entire JVM:

```java
public class DBConnection {
    // The single JVM-wide instance
    private static DBConnection instance;
    private final DataSource dataSource;

    // Private constructor blocks direct instantiation (new DBConnection())
    private DBConnection() {
        PoolProperties p = new PoolProperties();
        p.setUrl(Config.get("db.url"));
        p.setUsername(Config.get("db.user"));
        p.setPassword(Config.get("db.password"));
        p.setDriverClassName("com.mysql.cj.jdbc.Driver");
        p.setInitialSize(5);
        p.setMaxActive(20);
        p.setMaxIdle(10);
        p.setMinIdle(5);
        p.setTestOnBorrow(true);
        p.setValidationQuery("SELECT 1");

        org.apache.tomcat.jdbc.pool.DataSource ds = new org.apache.tomcat.jdbc.pool.DataSource();
        ds.setPoolProperties(p);
        this.dataSource = ds;
    }

    // Thread-safe synchronized accessor
    public static synchronized DBConnection getInstance() {
        if (instance == null) {
            instance = new DBConnection();
        }
        return instance;
    }

    // Lends a pre-warmed connection from the Tomcat JDBC pool
    public Connection getConnection() throws SQLException {
        return this.dataSource.getConnection();
    }
}
```

---

### 3.2 Data Access Object (DAO) Pattern

**Concept:** Decouples the presentation and servlet layer from SQL queries, connection logic, and relational mapping.

**Implementation Architecture:**
```
[Servlet Layer: QrScanServlet]
        │ Calls high-level method: attendanceDao.markAttendance(...)
        ▼
[DAO Layer: AttendanceDao]
        │ Prepares SQL, sets parameters, executes updates
        ▼
[Data Mapper Helper]
        │ Maps ResultSet rows into strongly-typed Java domain objects
        ▼
[Model: Attendance]
```

**Concrete Example from `QrSessionDao.java`:**
```java
public Optional<QrSession> findActiveSession(int courseId) {
    String sql = "SELECT * FROM qr_sessions WHERE course_id = ? AND is_active = TRUE LIMIT 1";
    try (Connection conn = DBConnection.getInstance().getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        
        ps.setInt(1, courseId);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return Optional.of(mapResultSetToQrSession(rs));
            }
        }
    } catch (SQLException e) {
        LOGGER.error("Error reading active QR session for course: " + courseId, e);
    }
    return Optional.empty();
}
```

---

### 3.3 Model-View-Controller (MVC) Architectural Pattern

ScanIn divides responsibilities across distinct tiers:
1. **Model:** Plain Old Java Objects (POJOs) in `com.attendance.model.*` representing database records and operational data structures (`User`, `Course`, `Attendance`).
2. **View:** JavaServer Pages (JSPs) in `/src/main/webapp/WEB-INF/views/` handling HTML rendering, Tailwind styling, and presentation.
3. **Controller:** Jakarta Servlets in `com.attendance.servlet.*` processing incoming HTTP requests, orchestrating business logic, updating models via DAOs, and forwarding to views.

```java
// Controller: TeacherDashboardServlet.java
@Override
protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
        throws ServletException, IOException {
    User user = SessionUtil.getLoggedInUser(req);
    Teacher teacher = teacherDao.findByUserId(user.getId()).orElseThrow();
    
    // Model hydration
    List<Course> courses = courseDao.findByTeacherId(teacher.getTeacherId());
    req.setAttribute("courses", courses);
    req.setAttribute("teacher", teacher);

    // Forwarding to View
    req.getRequestDispatcher("/WEB-INF/views/teacher-dashboard.jsp").forward(req, resp);
}
```

---

### 3.4 Utility / Pure Static Class Pattern (`QrCodeUtil`, `DateTimeUtil`)

Classes containing stateless mathematical, cryptographic, or conversion algorithms are implemented as final utility classes:
- Declared `public final class`.
- Private default constructor (`private QrCodeUtil() {}`) to prevent instantiation.
- Methods declared `public static` and side-effect-free (pure functions).

---

### 3.5 Lifecycle State Pattern (`CourseSchedule.java`)

Academic lecture scheduling tracks clear state progressions:
$$\text{Planned / Scheduled} \longrightarrow \text{Active Live QR Session} \longrightarrow \text{Conducted / Retired}$$

- When faculty clicks **"Start Now"**, the schedule ID is linked to the newly generated `qr_sessions` record.
- When faculty clicks **"End Session & Mark Absentees"**, the system flags `course_schedules.is_conducted = TRUE`.
- **Soft Lifecycle State:** The scheduled slot is retired from the active dashboard view without deleting historical database records, keeping past attendance reports intact.

---

## 4. Enterprise Web Architecture (Jakarta Servlet & Filter API)

### 4.1 Servlet Lifecycle & Request Pipeline

Each servlet in ScanIn follows the standard Jakarta EE container lifecycle:

```
[Tomcat Boot / First Request]
        │
        ├──► 1. init(ServletConfig config) ── Instantiates DAOs and read-only resources
        │
[Per Incoming Request Thread]
        │
        ├──► 2. service(request, response)
        │         ├── Calls doGet(request, response)
        │         └── Calls doPost(request, response)
        │
[Server Shutdown / Undeploy]
        │
        └──► 3. destroy() ── Closes any held resources
```

**Thread Safety Rule:** Servlets are instantiated as singletons by Tomcat. Multiple threads execute `doGet()` and `doPost()` simultaneously. ScanIn maintains zero mutable instance variables inside servlets; all per-request state is confined strictly to method-local variables.

---

### 4.2 Interception & Role-Based Access Control (`SecurityFilter.java`)

[`SecurityFilter.java`](../src/main/java/com/attendance/filter/SecurityFilter.java) forms the perimeter security gateway:

```java
@WebFilter(filterName = "SecurityFilter", urlPatterns = {"/*"})
public class SecurityFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String uri = req.getRequestURI();

        // 1. Allow static resources (CSS, JS, SVGs, images)
        if (uri.contains("/css/") || uri.contains("/js/") || uri.contains("/assets/")) {
            chain.doFilter(request, response);
            return;
        }

        // 2. Allow unauthenticated authentication endpoints
        if (uri.endsWith("/login") || uri.endsWith("/register")) {
            chain.doFilter(request, response);
            return;
        }

        // 3. Verify active server-side HTTP session
        User user = SessionUtil.getLoggedInUser(req);
        if (user == null) {
            res.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        // 4. Role-Based Access Control (RBAC) checks
        if (uri.startsWith(req.getContextPath() + "/teacher") && !"TEACHER".equals(user.getRole())) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized role access");
            return;
        }
        if (uri.startsWith(req.getContextPath() + "/student") && !"STUDENT".equals(user.getRole())) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized role access");
            return;
        }

        // Authorized: continue through filter chain to the servlet
        chain.doFilter(request, response);
    }
}
```

---

### 4.3 HTTP State & Session Management (`SessionUtil.java`)

HTTP is a stateless protocol. ScanIn manages state through server-side sessions bound to an encrypted browser cookie (`JSESSIONID`).

- Server-side state is stored in memory by Tomcat; the client only receives an opaque session identifier.
- `SessionUtil.java` wraps session handling to ensure safe casting and centralized access:
  ```java
  public class SessionUtil {
      private static final String USER_ATTR = "LOGGED_IN_USER";

      public static void setLoggedInUser(HttpServletRequest req, User user) {
          HttpSession session = req.getSession(true);
          session.setAttribute(USER_ATTR, user);
      }

      public static User getLoggedInUser(HttpServletRequest req) {
          HttpSession session = req.getSession(false); // Do not force creation
          return (session != null) ? (User) session.getAttribute(USER_ATTR) : null;
      }

      public static void invalidate(HttpServletRequest req) {
          HttpSession session = req.getSession(false);
          if (session != null) {
              session.invalidate();
          }
      }
  }
  ```

---

### 4.4 Application Bootstrapping (`AppContextListener.java`)

[`AppContextListener.java`](../src/main/java/com/attendance/listener/AppContextListener.java) implements `ServletContextListener`. It initializes application-level configurations on container startup before any servlet receives traffic:
- Sets the default JVM timezone globally to UTC (`TimeZone.setDefault(TimeZone.getTimeZone("UTC"))`).
- Runs database migration scripts (`CREATE TABLE IF NOT EXISTS`, schema alterations) to ensure cold-start compatibility on cloud platforms like TiDB Cloud or Render.

---

## 5. Database Persistence & Concurrency (JDBC)

### 5.1 SQL Injection Prevention via `PreparedStatement`

ScanIn strictly bans string concatenation in SQL queries (`SELECT * FROM users WHERE email = '` + email + `'`). Instead, all SQL queries use parameterized `PreparedStatement` placeholders (`?`):

```java
// Vulnerable to SQL injection (NEVER used in ScanIn):
// "SELECT * FROM users WHERE email = '" + input + "'" -> attacker injects: ' OR '1'='1

// Safe, parameterized execution:
String sql = "SELECT * FROM users WHERE email = ?";
try (Connection conn = DBConnection.getInstance().getConnection();
     PreparedStatement ps = conn.prepareStatement(sql)) {
    ps.setString(1, email); // Database engine treats input purely as literal data
    try (ResultSet rs = ps.executeQuery()) { ... }
}
```

---

### 5.2 Automatic Resource Management (`try-with-resources`)

Java 7+ `try-with-resources` ensures that `Connection`, `PreparedStatement`, and `ResultSet` instances are closed automatically—even if an unchecked `RuntimeException` or SQL error occurs. This prevents database connection leaks and pool exhaustion.

---

### 5.3 Transaction Demarcation (`commit` & `rollback`)

In workflows modifying multiple related tables—such as manual attendance overrides in `AttendanceManualServlet.java`—the application disables auto-commit to ensure full **ACID Atomicity**:

```java
Connection conn = DBConnection.getInstance().getConnection();
try {
    conn.setAutoCommit(false); // Begin ACID Transaction boundary

    // 1. Update attendance status
    attendanceDao.updateStatus(conn, attendanceId, newStatus);

    // 2. Insert audit log record
    auditLogDao.insertRecord(conn, attendanceId, teacherId, oldStatus, newStatus, reason);

    conn.commit(); // Persist changes atomically
} catch (SQLException e) {
    conn.rollback(); // Revert all modifications on failure
    throw e;
} finally {
    conn.setAutoCommit(true);
    conn.close(); // Return connection to pool
}
```

---

### 5.4 Handling Database Race Conditions (Error Code 1062)

If a student attempts rapid duplicate scans, an application-level `if (!marked)` check is susceptible to a Time-Of-Check to Time-Of-Use (TOCTOU) race condition.

ScanIn offloads concurrency control to the database using a composite unique constraint:
```sql
CONSTRAINT unique_attendance UNIQUE (student_id, qr_session_id)
```

When two identical requests execute concurrently:
1. Thread 1 acquires the row lock and inserts successfully.
2. Thread 2 is immediately rejected by the InnoDB engine with **MySQL Error Code 1062 (Duplicate Key)**.
3. `AttendanceDao.java` intercepts this error code and returns an `ALREADY_MARKED` status instead of throwing an unhandled exception:

```java
try {
    ps.executeUpdate();
    return AttendanceResult.SUCCESS;
} catch (SQLException ex) {
    if (ex.getErrorCode() == 1062) {
        LOGGER.warn("Duplicate attendance attempt blocked at DB level for student: {}", studentId);
        return AttendanceResult.ALREADY_MARKED;
    }
    throw ex;
}
```

---

## 6. Cryptography & Anti-Proxy Security Engine

[`QrCodeUtil.java`](../src/main/java/com/attendance/util/QrCodeUtil.java) handles the cryptographic security pipeline protecting against proxy attendance, screenshot sharing, and token replay attacks.

```
                  Rotating 7-Second Window Cycle
               (step = currentTimestamp / 7000ms)
                               │
               ┌───────────────┴───────────────┐
               ▼                               ▼
       HMAC-SHA256 Token            6-Digit Dynamic OTP
  [5-Part Cryptographic String]     (RFC 6238 TOTP Truncation)
  - sessionId                       - 6-digit numeric fallback
  - courseId                        - For poor camera lenses
  - stepStartMs                     - Validated against current
  - 8-char Nonce                      or adjacent (t - 1) step
  - SHA-256 HMAC Signature
```

### 6.1 HMAC-SHA256 Cryptographic Token Generation

Each dynamic QR code embeds a signed 5-part token structured as:
$$\text{token} = \text{sessionId} \,.\, \text{courseId} \,.\, \text{stepStartMs} \,.\, \text{nonce} \,.\, \text{HMAC-SHA256}(\text{payload})$$

```java
public static String generateRotatingToken(int sessionId, int courseId, long step) {
    long stepStartMs = step * ROTATION_INTERVAL_MS; // 7000ms per step
    String nonce = computeHmac(sessionId + ":nonce:" + step).substring(0, 8);
    String payload = sessionId + ":" + courseId + ":" + stepStartMs + ":" + nonce;
    String signature = computeHmac(payload);
    return sessionId + "." + courseId + "." + stepStartMs + "." + nonce + "." + signature;
}

private static String computeHmac(String data) {
    try {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] rawBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(rawBytes);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
        throw new SecurityException("Cryptographic failure in HMAC initialization", e);
    }
}
```

---

### 6.2 Constant-Time Signature Verification (Anti-Timing Attacks)

Standard `String.equals()` compares characters sequentially and returns `false` on the first mismatched byte. An attacker measuring request response times with high-precision clocks can deduce valid signature bytes one by one (a **timing attack**).

ScanIn uses `MessageDigest.isEqual()` to enforce **constant-time byte comparison**:

```java
// Vulnerable to timing attack:
// if (receivedSignature.equals(expectedSignature)) ...

// Constant-time comparison: executes in uniform time regardless of mismatch location
if (!MessageDigest.isEqual(
        receivedSignature.getBytes(StandardCharsets.UTF_8),
        expectedSignature.getBytes(StandardCharsets.UTF_8))) {
    return TokenStatus.INVALID_SIGNATURE;
}
```

---

### 6.3 6-Digit TOTP Dynamic Truncation (RFC 6238 Fallback)

To accommodate students with damaged camera lenses or low-light conditions, ScanIn computes a dynamic 6-digit one-time PIN (OTP) synchronized with the current 7-second step:

```java
public static String computeNumericCode(int sessionId, int courseId, long timestamp) {
    long step = timestamp / ROTATION_INTERVAL_MS;
    String data = sessionId + ":" + courseId + ":" + step;
    String hmac = computeHmac(data);

    // Dynamic Truncation: extract lowest nibble to determine offset (0 to 15)
    int offset = Integer.parseInt(hmac.substring(hmac.length() - 1), 16);
    String part = hmac.substring(offset * 2, offset * 2 + 8);
    long binary = Long.parseLong(part, 16) & 0x7fffffffL; // Mask to 31-bit integer
    int otp = (int) (binary % 1_000_000L);               // Constrain to 6 digits

    return String.format("%06d", otp);
}
```

---

### 6.4 Adaptive Salting & BCrypt Password Hashing

[`PasswordUtil.java`](../src/main/java/com/attendance/util/PasswordUtil.java) handles password security using BCrypt:
- **Salt Generation:** Incorporates a 128-bit cryptographically secure random salt into each hash. Two users with identical passwords produce distinct hashes, neutralizing precomputed rainbow table attacks.
- **Adaptive Work Factor:** Uses a cost factor of `10` ($2^{10} = 1024$ key expansion rounds), balancing fast login times against brute-force resistance.

```java
public class PasswordUtil {
    public static String hashPassword(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(10));
    }

    public static boolean checkPassword(String plainPassword, String hashedPassword) {
        if (hashedPassword == null || !hashedPassword.startsWith("$2a$")) {
            return false;
        }
        return BCrypt.checkpw(plainPassword, hashedPassword);
    }
}
```

---

## 7. Modern Java LTS Idioms & Quality Standards

### 7.1 Null Safety with `Optional<T>`

To eliminate `NullPointerException` errors, all DAO query methods return `Optional<T>` rather than raw, potentially null object references:

```java
Optional<Student> studentOpt = studentDao.findByRollNumber("CS2026001");

// Functional evaluation:
studentOpt.ifPresentOrElse(
    student -> markAttendance(student),
    () -> logWarning("Student roll number not registered")
);
```

---

### 7.2 Modern Date-Time API (`java.time.*`) & Centralized UTC/IST Engine

Legacy date classes (`java.util.Date`, `SimpleDateFormat`) are mutable and not thread-safe. ScanIn uses the modern Java 8+ `java.time` package:

```
[Database / Storage Layer]  ──► Canonical UTC (ZoneOffset.UTC)
                                         │
                                         ▼
[DateTimeUtil Conversion]    ──► ZonedDateTime.withZoneSameInstant(Asia/Kolkata)
                                         │
                                         ▼
[UI / Presentation Layer]    ──► Formatted IST String ("dd MMM yyyy, hh:mm a")
```

[`DateTimeUtil.java`](../src/main/java/com/attendance/util/DateTimeUtil.java) converts database UTC timestamps to Indian Standard Time (IST) for presentation:

```java
public final class DateTimeUtil {
    public static final ZoneId UTC_ZONE = ZoneOffset.UTC;
    public static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    public static final DateTimeFormatter IST_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

    public static ZonedDateTime toIst(LocalDateTime utcDateTime) {
        if (utcDateTime == null) return null;
        return utcDateTime.atZone(UTC_ZONE).withZoneSameInstant(IST_ZONE);
    }

    public static String formatToIst(LocalDateTime utcDateTime) {
        if (utcDateTime == null) return "N/A";
        return toIst(utcDateTime).format(IST_FORMATTER);
    }
}
```
`ZonedDateTime.withZoneSameInstant()` correctly handles midnight day boundary crossovers (e.g. `23:30 UTC` $\rightarrow$ `05:00 IST next day`), ensuring calendar accuracy.

---

### 7.3 Type-Safe Generics & Collections Framework

Generic type parameters prevent runtime `ClassCastException` errors by enforcing type constraints at compile time:

```java
// Type-safe generic method
public <T extends User> List<T> filterByDepartment(List<T> users, String dept) {
    return users.stream()
                .filter(u -> dept.equalsIgnoreCase(u.getRoleInfo()))
                .collect(Collectors.toList());
}
```

---

### 7.4 Thread-Safety & Multi-Threading Model

Tomcat services concurrent requests using a thread pool. ScanIn maintains thread safety across all layers:
1. **Stateless Servlets:** Servlets hold no mutable state across requests.
2. **Immutable Utilities:** `DateTimeUtil` and `QrCodeUtil` operate purely on local function variables.
3. **Synchronized Singleton Pool:** `DBConnection.getInstance()` synchronizes initialization to guarantee a single pool instance across concurrent threads.
4. **Thread-Safe Cryptography:** `Mac` instances are allocated fresh per operation within `computeHmac()` to prevent race conditions during concurrent HMAC calculations.

---

## 8. Summary Mapping (Concepts → Source Files)

| Concept / Pattern | Source Code Location |
| :--- | :--- |
| **Abstract Class & Template Methods** | [`src/main/java/com/attendance/model/User.java`](../src/main/java/com/attendance/model/User.java) |
| **Inheritance Specialization** | [`src/main/java/com/attendance/model/Teacher.java`](../src/main/java/com/attendance/model/Teacher.java)<br>[`src/main/java/com/attendance/model/Student.java`](../src/main/java/com/attendance/model/Student.java) |
| **Polymorphic Dispatch** | [`src/main/java/com/attendance/servlet/LoginServlet.java`](../src/main/java/com/attendance/servlet/LoginServlet.java) |
| **Encapsulation & Access Control** | All models in `com.attendance.model.*` |
| **Generic DAO Interface** | [`src/main/java/com/attendance/dao/Dao.java`](../src/main/java/com/attendance/dao/Dao.java) |
| **Capability Interface** | [`src/main/java/com/attendance/model/Attendable.java`](../src/main/java/com/attendance/model/Attendable.java) |
| **Singleton Connection Pool** | [`src/main/java/com/attendance/util/DBConnection.java`](../src/main/java/com/attendance/util/DBConnection.java) |
| **Model-View-Controller (MVC)** | `servlet/*` (Controller) $\leftrightarrow$ `model/*` (Model) $\leftrightarrow$ `webapp/WEB-INF/views/*` (View) |
| **Filter & RBAC Interception** | [`src/main/java/com/attendance/filter/SecurityFilter.java`](../src/main/java/com/attendance/filter/SecurityFilter.java) |
| **Stateful Session Wrapper** | [`src/main/java/com/attendance/util/SessionUtil.java`](../src/main/java/com/attendance/util/SessionUtil.java) |
| **Application Lifecycle Listener** | [`src/main/java/com/attendance/listener/AppContextListener.java`](../src/main/java/com/attendance/listener/AppContextListener.java) |
| **HMAC-SHA256 & TOTP Crypto** | [`src/main/java/com/attendance/util/QrCodeUtil.java`](../src/main/java/com/attendance/util/QrCodeUtil.java) |
| **Constant-Time Verification** | [`src/main/java/com/attendance/util/QrCodeUtil.java`](../src/main/java/com/attendance/util/QrCodeUtil.java) |
| **Adaptive Password Hashing** | [`src/main/java/com/attendance/util/PasswordUtil.java`](../src/main/java/com/attendance/util/PasswordUtil.java) |
| **Modern Date/Time & Timezone Engine** | [`src/main/java/com/attendance/util/DateTimeUtil.java`](../src/main/java/com/attendance/util/DateTimeUtil.java) |
| **ACID Transaction Management** | [`src/main/java/com/attendance/servlet/AttendanceManualServlet.java`](../src/main/java/com/attendance/servlet/AttendanceManualServlet.java) |
| **DB Concurrency (Code 1062)** | [`src/main/java/com/attendance/dao/AttendanceDao.java`](../src/main/java/com/attendance/dao/AttendanceDao.java) |

---

## 9. Viva Defense / Interview Questions & Master Answers

### Q1: "Why did you use an abstract class for `User` instead of an interface?"
> **Master Answer:**  
> *"An abstract class was chosen because `Teacher` and `Student` share state (common fields like `id`, `name`, `email`, `password_hash`, `phone`, and `created_at`). An interface cannot hold mutable instance state or shared constructors. By using an abstract class `User`, we enforce code reuse for shared fields while keeping `getDashboardPath()` and `getRoleInfo()` abstract, requiring each role subclass to provide its own behavior."*

---

### Q2: "How does polymorphism simplify request handling in `LoginServlet`?"
> **Master Answer:**  
> *"When a user logs in, `userDao.findByEmail()` returns a `User` reference. We call `user.getDashboardPath()`, and the JVM dynamically invokes `Teacher.getDashboardPath()` (`/teacher/dashboard`) or `Student.getDashboardPath()` (`/student/dashboard`) via virtual method dispatch. This eliminates role-checking `if-else` blocks and allows new roles to be added without modifying the servlet."*

---

### Q3: "Why is the Singleton pattern in `DBConnection` declared with `synchronized`?"
> **Master Answer:**  
> *"Without synchronization, two concurrent HTTP worker threads could both find `instance == null` simultaneously and create two separate connection pool managers, wasting database connections and memory. Adding `synchronized` to `getInstance()` guarantees that only one thread can initialize the pool, preventing race conditions on startup."*

---

### Q4: "How does `SecurityFilter` protect against unauthorized URL access?"
> **Master Answer:**  
> *"The filter implements `jakarta.servlet.Filter` and intercepts every incoming HTTP request (`/*`). It verifies whether an active `HttpSession` exists and inspects the user's role. If a student attempts to access `/teacher/dashboard`, the filter intercepts the request and issues an HTTP 403 Forbidden error before it ever reaches the servlet."*

---

### Q5: "How does your code prevent SQL injection?"
> **Master Answer:**  
> *"Every database interaction uses `PreparedStatement` with parameterized placeholders (`?`). The database driver sends the query text and parameter values separately; inputs are never parsed as executable SQL commands, neutralizing SQL injection attempts entirely."*

---

### Q6: "Why use `MessageDigest.isEqual()` instead of `String.equals()` for token verification?"
> **Master Answer:**  
> *"`String.equals()` compares characters one by one and returns `false` as soon as it finds a mismatch, meaning comparison time varies depending on how many characters match. An attacker can measure these small timing differences to guess signature bytes incrementally. `MessageDigest.isEqual()` executes in constant time across the entire byte array, eliminating timing attack vulnerabilities."*

---

### Q7: "How does the system handle concurrent attendance scans from the same student?"
> **Master Answer:**  
> *"We handle concurrency using a database composite unique constraint: `UNIQUE(student_id, qr_session_id)`. If two identical requests hit the server at the same millisecond, the database engine accepts the first insert and rejects the second with MySQL Error Code 1062 (Duplicate Key). Our `AttendanceDao` catches this specific error code and returns an `ALREADY_MARKED` status code, preventing duplicate entries without race conditions."*
