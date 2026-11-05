# Java Concepts & Implementation — ScanIn

This document explains every core Java concept used in the ScanIn project — what the concept is, why it was used, and exactly where it appears in the source code. Use this as your presentation reference.

---

## 1. Object-Oriented Programming (OOP)

### 1.1 Abstraction — `User.java`

**What it is:** Abstraction means hiding implementation details and exposing only the essential interface. In Java, this is done with `abstract` classes and interfaces.

**How ScanIn uses it:**
```java
// User.java
public abstract class User {
    private int id;
    private String name;
    private String email;
    private String password;   // always BCrypt-hashed, never plain text

    // Abstract methods — WHAT to do is defined here, HOW is left to subclasses
    public abstract String getDashboardPath();  // Teacher → /teacher/dashboard
    public abstract String getRoleInfo();       // Student → "Student | CS | Sem 5"
}
```
- `User` cannot be instantiated directly — the system never creates a "generic user".
- Callers only need to call `user.getDashboardPath()` without knowing whether the object is a Teacher or Student.

**File:** [`src/main/java/com/attendance/model/User.java`](../src/main/java/com/attendance/model/User.java)

---

### 1.2 Inheritance — `Teacher.java` and `Student.java`

**What it is:** Inheritance lets one class acquire the fields and behaviour of another using `extends`. The child class adds its own fields on top.

**How ScanIn uses it:**
```java
// Teacher.java
public class Teacher extends User {
    private int teacherId;
    private String employeeId;   // e.g. "EMP001"
    private String department;

    @Override
    public String getDashboardPath() { return "/teacher/dashboard"; }

    @Override
    public String getRoleInfo() { return "Faculty | " + department; }
}

// Student.java
public class Student extends User {
    private int studentId;
    private String rollNumber;   // e.g. "CS2021001"
    private String department;
    private int semester;

    @Override
    public String getDashboardPath() { return "/student/dashboard"; }
}
```
- Both `Teacher` and `Student` automatically inherit `id`, `name`, `email`, `password`, `phone`, `createdAt` from `User`.
- They add their own domain-specific fields without duplicating the common fields.

**Files:**
- [`src/main/java/com/attendance/model/Teacher.java`](../src/main/java/com/attendance/model/Teacher.java)
- [`src/main/java/com/attendance/model/Student.java`](../src/main/java/com/attendance/model/Student.java)

---

### 1.3 Polymorphism — `LoginServlet.java`

**What it is:** Polymorphism means "many forms" — the same method call behaves differently depending on the actual type of the object at runtime (dynamic dispatch).

**How ScanIn uses it:**
```java
// LoginServlet.java — after successful login
User user = userDao.findByEmail(email);   // could be Teacher or Student

// Same call, different behaviour at runtime
String dashboardPath = user.getDashboardPath();
// If user is a Teacher  → "/teacher/dashboard"
// If user is a Student  → "/student/dashboard"

response.sendRedirect(request.getContextPath() + dashboardPath);
```
- The servlet never checks `if (user instanceof Teacher)` — it relies on the polymorphic contract.
- Adding a new role (e.g. Admin) only requires a new subclass; the servlet needs no change.

**File:** [`src/main/java/com/attendance/servlet/LoginServlet.java`](../src/main/java/com/attendance/servlet/LoginServlet.java)

---

### 1.4 Encapsulation — All Model Classes

**What it is:** Encapsulation means keeping fields `private` and exposing them only through controlled `public` getters and setters. This protects the object's internal state.

**How ScanIn uses it:**
```java
// All model classes follow this pattern
public class QrSession {
    private int id;
    private String sessionToken;   // never accessible directly
    private boolean isActive;

    public int getId()                  { return id; }
    public String getSessionToken()     { return sessionToken; }
    public boolean isActive()           { return isActive; }
    // No public setter for sessionToken — only the DAO layer writes it
}
```
- Password fields are always stored as BCrypt hashes. There is no method that returns the original plain-text password anywhere in the codebase.
- The HMAC secret key is a `private static final` constant in `QrCodeUtil` — no external class can access it.

---

### 1.5 Interface — `Dao<T>` and `Attendable`

**What it is:** An `interface` defines a pure contract (what methods must exist) without providing any implementation. Any class that `implements` the interface must provide all the method bodies.

**`Dao<T>` — Generic Data Access Contract:**
```java
// Dao.java
public interface Dao<T> {
    Optional<T> findById(int id);
    List<T>     findAll();
    boolean     save(T entity);
    boolean     update(T entity);
    boolean     delete(int id);
}
```
Every DAO class implements this:
```
UserDao         implements Dao<User>
StudentDao      implements Dao<Student>
QrSessionDao    implements Dao<QrSession>
AttendanceDao   implements Dao<Attendance>
```
This guarantees a uniform API across all data access — if you know how to use `UserDao`, you already know the shape of every other DAO.

**`Attendable` — Scanner Capability Contract:**
```java
public interface Attendable {
    boolean canMarkAttendance(String token);
}
```
`Student` implements `Attendable`, which lets the scan servlet confirm the user has the scanning capability at compile time, not just at runtime.

**Files:**
- [`src/main/java/com/attendance/dao/Dao.java`](../src/main/java/com/attendance/dao/Dao.java)
- [`src/main/java/com/attendance/model/Attendable.java`](../src/main/java/com/attendance/model/Attendable.java)

---

## 2. Design Patterns

### 2.1 Singleton Pattern — `DBConnection.java`

**What it is:** The Singleton pattern ensures only one instance of a class exists in the entire JVM, providing a single global access point.

**How ScanIn uses it:**
```java
// DBConnection.java
public class DBConnection {
    private static DBConnection instance;   // the one and only instance
    private final DataSource dataSource;    // Apache Tomcat JDBC pool

    private DBConnection() {
        // Connection pool setup: maxActive=20, testOnBorrow=true
        PoolProperties p = new PoolProperties();
        p.setMaxActive(20);
        p.setInitialSize(5);
        p.setTestOnBorrow(true);          // validates connection before use
        p.setValidationQuery("SELECT 1");
        // ...
    }

    public static synchronized DBConnection getInstance() {
        if (instance == null) {
            instance = new DBConnection();  // created once
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return this.dataSource.getConnection();  // borrows from pool
    }
}
```
- **Why Singleton?** Creating a new database connection pool on every request would be catastrophically slow (each TCP + TLS handshake takes ~100ms). The pool keeps 5–20 connections open and lends them out instantly.
- `synchronized` prevents two threads from both seeing `instance == null` and creating two pools (race condition protection).

**File:** [`src/main/java/com/attendance/util/DBConnection.java`](../src/main/java/com/attendance/util/DBConnection.java)

---

### 2.2 DAO Pattern — All DAO Classes

**What it is:** The Data Access Object (DAO) pattern separates the business logic from the data persistence logic. Servlets talk to DAOs; DAOs talk to the database.

**Flow:**
```
HTTP Request
    → Servlet
        → DAO (SQL query)
            → DBConnection (pool)
                → MySQL
            ← ResultSet
        ← Model object (e.g. QrSession)
    ← HTTP Response (JSP rendered)
```

**Example — `QrSessionDao`:**
```java
public Optional<QrSession> findById(int id) {
    String sql = "SELECT * FROM qr_sessions WHERE id = ?";
    try (Connection conn = DBConnection.getInstance().getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, id);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            QrSession session = mapRow(rs);  // convert ResultSet → object
            return Optional.of(session);
        }
    } catch (SQLException e) { ... }
    return Optional.empty();
}
```
- Uses `PreparedStatement` (never `Statement`) to prevent SQL injection.
- Returns `Optional<T>` instead of `null` — callers must explicitly handle the "not found" case.

---

## 3. Jakarta Servlet API

### 3.1 What is a Servlet?

A **Servlet** is a Java class that handles HTTP requests. It extends `HttpServlet` and overrides `doGet()` / `doPost()`. Tomcat (the Servlet container) manages the servlet lifecycle.

**Lifecycle:**
```
1. Tomcat loads the WAR → calls servlet.init()   (once per JVM lifetime)
2. HTTP request arrives → Tomcat calls doGet() or doPost()   (per request, on a thread)
3. Tomcat shutdown → calls servlet.destroy()
```

### 3.2 Key Servlets in ScanIn

| Servlet | URL Mapping | Purpose |
|---|---|---|
| `LoginServlet` | `/login` | Authenticates user, creates session |
| `QrGenerateServlet` | `/qr/generate` | Teacher launches live QR session |
| `QrScanServlet` | `/qr/scan` | Student scans/types QR token |
| `TeacherDashboardServlet` | `/teacher/dashboard` | Faculty course overview |
| `StudentDashboardServlet` | `/student/dashboard` | Student attendance % view |
| `ReportServlet` | `/reports` | Faculty attendance reports & manual overrides |
| `AttendanceManualServlet` | `/attendance/manual` | Faculty edits a student status |

**Registration (annotation-based):**
```java
@WebServlet(name = "QrScanServlet", urlPatterns = {"/qr/scan"})
public class QrScanServlet extends HttpServlet {

    @Override
    public void init() throws ServletException {
        // DAO objects created once — thread-safe reads only
        this.qrSessionDao = new QrSessionDao();
        this.attendanceDao = new AttendanceDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Handle QR scan from camera (GET with ?token=...)
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Handle typed numeric fallback code (POST form)
    }
}
```

### 3.3 `SecurityFilter` — Jakarta Filter API

**What it is:** A `Filter` intercepts every HTTP request before it reaches a servlet. It can inspect, modify, or reject the request.

**How `SecurityFilter` works:**
```java
@WebFilter(filterName = "SecurityFilter", urlPatterns = {"/*"})
public class SecurityFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
        String path = request.getRequestURI();

        // 1. Static assets → pass through immediately
        if (isStaticAsset(path)) { chain.doFilter(req, res); return; }

        // 2. Public routes → pass through
        if (path.equals("/login")) { chain.doFilter(req, res); return; }

        // 3. No session → redirect to login
        User user = SessionUtil.getLoggedInUser(request);
        if (user == null) { response.sendRedirect("/login"); return; }

        // 4. RBAC check
        if (path.startsWith("/teacher") && !"TEACHER".equals(user.getRole())) {
            response.sendError(403, "Access Denied");   // HTTP 403 Forbidden
            return;
        }

        chain.doFilter(req, res);  // authorized — continue to servlet
    }
}
```
- **Why a Filter and not inside each servlet?** Putting access checks in the filter means they cannot be bypassed. Even if a new servlet is added, the filter still runs for it.

**File:** [`src/main/java/com/attendance/filter/SecurityFilter.java`](../src/main/java/com/attendance/filter/SecurityFilter.java)

---

## 4. Session Management

**What it is:** HTTP is stateless — each request is independent. Sessions allow the server to remember who is logged in across multiple requests.

**How ScanIn uses it:**
```java
// SessionUtil.java
public class SessionUtil {
    private static final String USER_KEY = "loggedInUser";

    public static void setLoggedInUser(HttpServletRequest req, User user) {
        req.getSession(true).setAttribute(USER_KEY, user);
    }

    public static User getLoggedInUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);  // false = don't create new
        return (session != null) ? (User) session.getAttribute(USER_KEY) : null;
    }
}
```
- Tomcat stores session data server-side. Only a session ID cookie (`JSESSIONID`) is sent to the browser.
- `getSession(false)` returns `null` if no session exists — this is how the filter detects unauthenticated requests.

---

## 5. JDBC & Prepared Statements

**What is JDBC?** Java Database Connectivity — the standard Java API for communicating with relational databases.

**Key classes:**
| Class | Role |
|---|---|
| `Connection` | Represents one database connection |
| `PreparedStatement` | Pre-compiled SQL with `?` placeholders (SQL injection safe) |
| `ResultSet` | Rows returned by a `SELECT` query |
| `DataSource` | Pool of `Connection` objects |

**Pattern used in every DAO:**
```java
// Safe: PreparedStatement with parameterized placeholders
String sql = "INSERT INTO attendance (student_id, course_id, qr_session_id, status) VALUES (?, ?, ?, ?)";

try (Connection conn = DBConnection.getInstance().getConnection();
     PreparedStatement ps = conn.prepareStatement(sql)) {

    ps.setInt(1, studentId);    // parameter 1
    ps.setInt(2, courseId);     // parameter 2
    ps.setInt(3, sessionId);    // parameter 3
    ps.setString(4, "PRESENT"); // parameter 4

    ps.executeUpdate();         // execute INSERT
} catch (SQLException e) {
    // catch duplicate key (error code 1062) for ACID rollback
}
```
- **Try-with-resources** (`try (Connection conn = ...)`) ensures the connection is returned to the pool even if an exception occurs — no connection leaks.

---

## 6. Cryptography — `QrCodeUtil.java`

### 6.1 HMAC-SHA256 Token Generation

**What is HMAC?** Hash-based Message Authentication Code — a cryptographic signature that proves a message was created by someone who knows the secret key, and has not been tampered with.

**Token format:**
```
sessionId.courseId.timestampMs.nonce.HMAC-SHA256(payload)
Example:
360003.1.1793801716000.274274b4.02522b34f6e9178ddb943a2751619f5fd693f01779c8623970234a6f5543b5cf
```

**Generation code:**
```java
// QrCodeUtil.java
public static String generateSignedTokenForStep(int sessionId, int courseId, long step) {
    long stepStartMs = step * ROTATION_INTERVAL_MS;        // 7000ms per step
    String nonce     = computeHmac(sessionId + ":nonce:" + step).substring(0, 8);
    String payload   = sessionId + ":" + courseId + ":" + stepStartMs + ":" + nonce;
    String signature = computeHmac(payload);               // HMAC-SHA256
    return sessionId + "." + courseId + "." + stepStartMs + "." + nonce + "." + signature;
}

private static String computeHmac(String data) {
    Mac mac = Mac.getInstance("HmacSHA256");
    SecretKeySpec key = new SecretKeySpec(SECRET_KEY.getBytes(UTF_8), "HmacSHA256");
    mac.init(key);
    byte[] raw = mac.doFinal(data.getBytes(UTF_8));
    // Convert bytes to hex string
    StringBuilder sb = new StringBuilder();
    for (byte b : raw) sb.append(String.format("%02x", b));
    return sb.toString();
}
```

**Verification (constant-time comparison):**
```java
// MessageDigest.isEqual() does NOT short-circuit — prevents timing attacks
if (!MessageDigest.isEqual(receivedSig.getBytes(), expectedSig.getBytes())) {
    return TokenStatus.INVALID;
}
```
- A **timing attack** works by measuring how fast a comparison returns false — if `String.equals()` returns at the first mismatch, an attacker can guess the signature one character at a time. `MessageDigest.isEqual()` always takes the same time regardless of where the mismatch is.

### 6.2 6-Digit Numeric Fallback (TOTP-style)

```java
public static String computeNumericCode(int sessionId, int courseId, long timestamp) {
    long step = timestamp / ROTATION_INTERVAL_MS;      // which 7-second slot
    String data = sessionId + ":" + courseId + ":" + step;
    String hmac = computeHmac(data);
    // Dynamic truncation: take 4 bytes at a dynamic offset
    int offset  = Integer.parseInt(hmac.substring(hmac.length() - 1), 16);
    String part = hmac.substring(offset * 2, offset * 2 + 8);
    long binary = Long.parseLong(part, 16) & 0x7fffffffL;
    int otp     = (int) (binary % 1_000_000L);
    return String.format("%06d", otp);               // always 6 digits
}
```
This is exactly how **TOTP (Time-based One-Time Passwords)** work (RFC 6238) — the same algorithm behind Google Authenticator, adapted here to be course-session-bound.

### 6.3 BCrypt Password Hashing — `PasswordUtil.java`

```java
// PasswordUtil.java
public class PasswordUtil {
    public static String hash(String plain) {
        return BCrypt.hashpw(plain, BCrypt.gensalt(10));  // cost factor 10
    }

    public static boolean verify(String plain, String hashed) {
        return BCrypt.checkpw(plain, hashed);
    }
}
```
- BCrypt is **adaptive** — the cost factor (10) can be increased as hardware gets faster.
- BCrypt is **salted** — two users with the same password get different hashes.
- Stored hashes start with `$2a$10$` — this prefix identifies the algorithm and cost.

---

## 7. Generics — `Dao<T>`

**What they are:** Generics allow writing type-safe code that works with different types without casting. `Dao<T>` is a generic interface where `T` is a placeholder for any model class.

```java
// Without generics — unsafe, requires casting
List list = userDao.findAll();
User u = (User) list.get(0);   // ClassCastException risk

// With generics — type-safe
List<User> users = userDao.findAll();   // compiler enforces the type
User u = users.get(0);                  // no cast needed, compiler guaranteed
```
The compiler rejects an attempt to call `studentDao.save(teacher)` because `StudentDao` implements `Dao<Student>`, not `Dao<Teacher>`.

---

## 8. `AppContextListener` — Application Lifecycle

**What is a `ServletContextListener`?** An event listener that is notified when the entire web application starts and stops. Runs before any servlet processes a request.

**How ScanIn uses it:**
```java
@WebListener
public class AppContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));   // set JVM timezone
        runStartupMigrations();   // create/alter tables if missing
    }
}
```
`runStartupMigrations()` executes `CREATE TABLE IF NOT EXISTS` and `ALTER TABLE` statements so the database schema is always consistent on cold start — even when deploying to a fresh TiDB Cloud instance. This is what makes deployment to Render "zero-config".

**File:** [`src/main/java/com/attendance/listener/AppContextListener.java`](../src/main/java/com/attendance/listener/AppContextListener.java)

---

## 9. Exception Handling

**Key patterns in the project:**

| Scenario | Handling |
|---|---|
| `SQLException` on duplicate key (code 1062) | Caught by `AttendanceDao`, returns `DUPLICATE` outcome instead of crashing |
| Invalid token format | `QrCodeUtil.validateToken()` returns `TokenStatus.INVALID` — no exception propagated |
| `NumberFormatException` during token parsing | Caught inside `validateToken()`, returns `INVALID` safely |
| Connection failure | `DBConnection` pool retries; if exhausted, `getConnection()` throws `SQLException` caught by servlet |

```java
// Example: AttendanceDao.java — ACID duplicate handling
try {
    ps.executeUpdate();   // INSERT into attendance
    return "SUCCESS";
} catch (SQLException e) {
    if (e.getErrorCode() == 1062) {   // MySQL duplicate key
        return "DUPLICATE";           // already marked — not an error, just rejected
    }
    throw e;   // unexpected error — rethrow
}
```

---

## 10. `Optional<T>` — Null Safety

All DAO `findById()` and `findByX()` methods return `Optional<T>` instead of `null`. This forces callers to handle the "not found" case explicitly:

```java
Optional<Student> opt = studentDao.findByUserId(userId);

// Caller must acknowledge that the student might not exist
if (opt.isPresent()) {
    Student s = opt.get();
    // use s
} else {
    response.sendError(404, "Student not found");
}
```
Returning `null` from a method and forgetting to check is the #1 source of `NullPointerException` in Java. `Optional` makes the possibility visible in the type system.

---

## 11. Summary Map: Concept → File

| Concept | Primary File(s) |
|---|---|
| Abstract class (Abstraction) | [`User.java`](../src/main/java/com/attendance/model/User.java) |
| Inheritance | [`Teacher.java`](../src/main/java/com/attendance/model/Teacher.java), [`Student.java`](../src/main/java/com/attendance/model/Student.java) |
| Polymorphism | [`LoginServlet.java`](../src/main/java/com/attendance/servlet/LoginServlet.java) |
| Encapsulation | All model classes |
| Interface | [`Dao.java`](../src/main/java/com/attendance/dao/Dao.java), [`Attendable.java`](../src/main/java/com/attendance/model/Attendable.java) |
| Singleton pattern | [`DBConnection.java`](../src/main/java/com/attendance/util/DBConnection.java) |
| DAO pattern | [`AttendanceDao.java`](../src/main/java/com/attendance/dao/AttendanceDao.java), [`QrSessionDao.java`](../src/main/java/com/attendance/dao/QrSessionDao.java) |
| Servlet lifecycle | [`QrGenerateServlet.java`](../src/main/java/com/attendance/servlet/QrGenerateServlet.java), [`QrScanServlet.java`](../src/main/java/com/attendance/servlet/QrScanServlet.java) |
| Filter / RBAC | [`SecurityFilter.java`](../src/main/java/com/attendance/filter/SecurityFilter.java) |
| Session management | [`SessionUtil.java`](../src/main/java/com/attendance/util/SessionUtil.java) |
| JDBC & PreparedStatement | All DAO classes |
| HMAC-SHA256 cryptography | [`QrCodeUtil.java`](../src/main/java/com/attendance/util/QrCodeUtil.java) |
| BCrypt hashing | [`PasswordUtil.java`](../src/main/java/com/attendance/util/PasswordUtil.java) |
| Generics | [`Dao.java`](../src/main/java/com/attendance/dao/Dao.java) |
| App lifecycle listener | [`AppContextListener.java`](../src/main/java/com/attendance/listener/AppContextListener.java) |
| Optional / null safety | All DAO findById methods |
