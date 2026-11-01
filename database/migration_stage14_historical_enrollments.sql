-- ============================================================================
-- ScanIn — Stage 14 Additive Migration: Historical Enrollment Retention
-- Target Database: attendance_test_db ONLY
-- Preserves student enrollment history without physical record deletion.
-- ============================================================================

-- 1. Conditionally add status column
SET @col_status_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollments' AND COLUMN_NAME = 'status'
);
SET @sql_status = IF(@col_status_exists = 0, 
    'ALTER TABLE enrollments ADD COLUMN status ENUM(''ACTIVE'', ''DROPPED'') NOT NULL DEFAULT ''ACTIVE''', 
    'SELECT ''status column already exists'''
);
PREPARE stmt_status FROM @sql_status;
EXECUTE stmt_status;
DEALLOCATE PREPARE stmt_status;

-- 2. Conditionally add dropped_at column
SET @col_dropped_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollments' AND COLUMN_NAME = 'dropped_at'
);
SET @sql_dropped = IF(@col_dropped_exists = 0, 
    'ALTER TABLE enrollments ADD COLUMN dropped_at TIMESTAMP NULL DEFAULT NULL', 
    'SELECT ''dropped_at column already exists'''
);
PREPARE stmt_dropped FROM @sql_dropped;
EXECUTE stmt_dropped;
DEALLOCATE PREPARE stmt_dropped;

-- 3. Conditionally add performance index on (student_id, status)
SET @idx_student_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollments' AND INDEX_NAME = 'idx_enrollment_student_status'
);
SET @sql_idx_student = IF(@idx_student_exists = 0, 
    'CREATE INDEX idx_enrollment_student_status ON enrollments (student_id, status)', 
    'SELECT ''idx_enrollment_student_status already exists'''
);
PREPARE stmt_idx_student FROM @sql_idx_student;
EXECUTE stmt_idx_student;
DEALLOCATE PREPARE stmt_idx_student;

-- 4. Conditionally add performance index on (course_id, status)
SET @idx_course_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollments' AND INDEX_NAME = 'idx_enrollment_course_status'
);
SET @sql_idx_course = IF(@idx_course_exists = 0, 
    'CREATE INDEX idx_enrollment_course_status ON enrollments (course_id, status)', 
    'SELECT ''idx_enrollment_course_status already exists'''
);
PREPARE stmt_idx_course FROM @sql_idx_course;
EXECUTE stmt_idx_course;
DEALLOCATE PREPARE stmt_idx_course;
