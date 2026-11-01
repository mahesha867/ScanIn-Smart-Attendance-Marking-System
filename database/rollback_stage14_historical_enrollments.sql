-- ============================================================================
-- ScanIn — Stage 14 Rollback: Historical Enrollment Retention
-- Target Database: attendance_test_db ONLY
-- Reverts additive schema changes introduced in Stage 14.
-- ============================================================================

-- 1. Conditionally drop indexes
SET @idx_student_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollments' AND INDEX_NAME = 'idx_enrollment_student_status'
);
SET @sql_idx_student = IF(@idx_student_exists > 0, 
    'DROP INDEX idx_enrollment_student_status ON enrollments', 
    'SELECT ''idx_enrollment_student_status already dropped'''
);
PREPARE stmt_idx_student FROM @sql_idx_student;
EXECUTE stmt_idx_student;
DEALLOCATE PREPARE stmt_idx_student;

SET @idx_course_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollments' AND INDEX_NAME = 'idx_enrollment_course_status'
);
SET @sql_idx_course = IF(@idx_course_exists > 0, 
    'DROP INDEX idx_enrollment_course_status ON enrollments', 
    'SELECT ''idx_enrollment_course_status already dropped'''
);
PREPARE stmt_idx_course FROM @sql_idx_course;
EXECUTE stmt_idx_course;
DEALLOCATE PREPARE stmt_idx_course;

-- 2. Conditionally drop columns
SET @col_dropped_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollments' AND COLUMN_NAME = 'dropped_at'
);
SET @sql_dropped = IF(@col_dropped_exists > 0, 
    'ALTER TABLE enrollments DROP COLUMN dropped_at', 
    'SELECT ''dropped_at already dropped'''
);
PREPARE stmt_dropped FROM @sql_dropped;
EXECUTE stmt_dropped;
DEALLOCATE PREPARE stmt_dropped;

SET @col_status_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollments' AND COLUMN_NAME = 'status'
);
SET @sql_status = IF(@col_status_exists > 0, 
    'ALTER TABLE enrollments DROP COLUMN status', 
    'SELECT ''status already dropped'''
);
PREPARE stmt_status FROM @sql_status;
EXECUTE stmt_status;
DEALLOCATE PREPARE stmt_status;
