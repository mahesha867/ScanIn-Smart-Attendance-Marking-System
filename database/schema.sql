-- ============================================================
-- Smart Attendance Marking System - Database Schema
-- MySQL 8.0
-- ============================================================

CREATE DATABASE IF NOT EXISTS attendance_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE attendance_db;

-- -------------------------------------------------------
-- Table: users (Base entity for polymorphism - Teacher & Student inherit from User)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,  -- BCrypt hashed
    role        ENUM('TEACHER','STUDENT') NOT NULL,
    phone       VARCHAR(20),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- -------------------------------------------------------
-- Table: students (extends users)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS students (
    student_id     INT AUTO_INCREMENT PRIMARY KEY,
    user_id        INT NOT NULL UNIQUE,
    roll_number    VARCHAR(20) NOT NULL UNIQUE,
    department     VARCHAR(100),
    semester       INT,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- -------------------------------------------------------
-- Table: teachers (extends users)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS teachers (
    teacher_id  INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT NOT NULL UNIQUE,
    employee_id VARCHAR(20) NOT NULL UNIQUE,
    department  VARCHAR(100),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- -------------------------------------------------------
-- Table: courses
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS courses (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    course_code VARCHAR(20) NOT NULL UNIQUE,
    course_name VARCHAR(150) NOT NULL,
    teacher_id  INT NOT NULL,
    semester    INT,
    department  VARCHAR(100),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (teacher_id) REFERENCES teachers(teacher_id) ON DELETE CASCADE
);

-- -------------------------------------------------------
-- Table: enrollments (Students enrolled in courses)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS enrollments (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    student_id  INT NOT NULL,
    course_id   INT NOT NULL,
    enrolled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_enrollment (student_id, course_id),
    FOREIGN KEY (student_id) REFERENCES students(student_id) ON DELETE CASCADE,
    FOREIGN KEY (course_id)  REFERENCES courses(id) ON DELETE CASCADE
);

-- -------------------------------------------------------
-- Table: course_schedules (Course semester timetable / planned lecture sessions)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS course_schedules (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    course_id   INT NOT NULL,
    day_of_week VARCHAR(15) NOT NULL,
    start_time  TIME NOT NULL,
    end_time    TIME NOT NULL,
    room        VARCHAR(50),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_schedule_course (course_id),
    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
);

-- -------------------------------------------------------
-- Table: qr_sessions (Dynamic QR codes generated per class)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS qr_sessions (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    session_token VARCHAR(255) NOT NULL UNIQUE,
    course_id   INT NOT NULL,
    teacher_id  INT NOT NULL,
    schedule_id INT NULL DEFAULT NULL,
    expires_at  TIMESTAMP NOT NULL,
    closed_at   TIMESTAMP NULL DEFAULT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active   BOOLEAN DEFAULT TRUE,
    INDEX idx_qr_schedule (schedule_id),
    FOREIGN KEY (course_id)    REFERENCES courses(id) ON DELETE CASCADE,
    FOREIGN KEY (teacher_id)   REFERENCES teachers(teacher_id) ON DELETE CASCADE,
    FOREIGN KEY (schedule_id)  REFERENCES course_schedules(id) ON DELETE SET NULL
);

-- -------------------------------------------------------
-- Table: attendance
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS attendance (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    student_id      INT NOT NULL,
    course_id       INT NOT NULL,
    qr_session_id   INT NOT NULL,
    marked_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status          ENUM('PRESENT','ABSENT','LATE') DEFAULT 'PRESENT',
    UNIQUE KEY unique_attendance (student_id, qr_session_id),
    FOREIGN KEY (student_id)    REFERENCES students(student_id) ON DELETE CASCADE,
    FOREIGN KEY (course_id)     REFERENCES courses(id) ON DELETE CASCADE,
    FOREIGN KEY (qr_session_id) REFERENCES qr_sessions(id) ON DELETE CASCADE
);

-- -------------------------------------------------------
-- Table: scan_attempts (Auditing QR scans and fraud prevention)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS scan_attempts (
    id                 INT AUTO_INCREMENT PRIMARY KEY,
    session_id         INT NULL,
    student_id         INT NULL,
    token_hash         VARCHAR(64) NOT NULL,
    outcome            VARCHAR(50) NOT NULL,
    attempted_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_scan_token_hash (token_hash),
    INDEX idx_scan_student_session (student_id, session_id)
);

-- -------------------------------------------------------
-- Table: attendance_audit_log (Immutable audit trail)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS attendance_audit_log (
    id                 INT AUTO_INCREMENT PRIMARY KEY,
    attendance_id      INT NULL,
    student_id         INT NOT NULL,
    session_id         INT NULL,
    changed_by_user_id INT NOT NULL,
    old_status         VARCHAR(20),
    new_status         VARCHAR(20) NOT NULL,
    reason             TEXT NOT NULL,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_student (student_id),
    INDEX idx_audit_session (session_id)
);

-- -------------------------------------------------------
-- Sample Data
-- -------------------------------------------------------

-- Default teacher (password: teacher123)
INSERT INTO users (name, email, password, role, phone) VALUES
('Prof. Ramesh Kumar',  'teacher@attendance.com', '$2a$10$4N8R1PtcJq..fGDPqmk4Gee8JbVoTrDMxSc1iZs74WU0BemBIU2MO', 'TEACHER', '9876543210'),
('Prof. Sunita Sharma', 'sunita@attendance.com',  '$2a$10$4N8R1PtcJq..fGDPqmk4Gee8JbVoTrDMxSc1iZs74WU0BemBIU2MO', 'TEACHER', '9876543211');

-- Default students (password: student123)
INSERT INTO users (name, email, password, role, phone) VALUES
('Aarav Singh',    'aarav@student.com',   '$2a$10$wALPiFbd0xdBlEilGrJxYuk7s0pWTI7WZAhg7uJI8XSCl7T9iltYC', 'STUDENT', '9000000001'),
('Priya Sharma',   'priya@student.com',   '$2a$10$wALPiFbd0xdBlEilGrJxYuk7s0pWTI7WZAhg7uJI8XSCl7T9iltYC', 'STUDENT', '9000000002'),
('Rahul Verma',    'rahul@student.com',   '$2a$10$wALPiFbd0xdBlEilGrJxYuk7s0pWTI7WZAhg7uJI8XSCl7T9iltYC', 'STUDENT', '9000000003'),
('Sneha Patel',    'sneha@student.com',   '$2a$10$wALPiFbd0xdBlEilGrJxYuk7s0pWTI7WZAhg7uJI8XSCl7T9iltYC', 'STUDENT', '9000000004'),
('Kiran Mehta',    'kiran@student.com',   '$2a$10$wALPiFbd0xdBlEilGrJxYuk7s0pWTI7WZAhg7uJI8XSCl7T9iltYC', 'STUDENT', '9000000005');

-- Teachers
INSERT INTO teachers (user_id, employee_id, department) VALUES
(1, 'EMP001', 'Computer Science'),
(2, 'EMP002', 'Information Technology');

-- Students
INSERT INTO students (user_id, roll_number, department, semester) VALUES
(3, 'CS2021001', 'Computer Science', 5),
(4, 'CS2021002', 'Computer Science', 5),
(5, 'CS2021003', 'Computer Science', 5),
(6, 'IT2021001', 'Information Technology', 5),
(7, 'IT2021002', 'Information Technology', 5);

-- Courses
INSERT INTO courses (course_code, course_name, teacher_id, semester, department) VALUES
('CS501', 'Database Management Systems',    1, 5, 'Computer Science'),
('CS502', 'Operating Systems',              1, 5, 'Computer Science'),
('IT501', 'Web Technologies',               2, 5, 'Information Technology');

-- Enrollments
INSERT INTO enrollments (student_id, course_id) VALUES
(1, 1), (1, 2),
(2, 1), (2, 2),
(3, 1), (3, 2),
(4, 3),
(5, 3);
