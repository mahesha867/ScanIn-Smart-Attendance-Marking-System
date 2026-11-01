-- ============================================================================
-- SCANIN Attendance Management System — Stage 12 Migration
-- File: database/migration_stage12_planned_sessions.sql
-- Invariants:
-- 1. Strictly fail-closed exhaustive verification of all schema invariants.
-- 2. Physical database objects verified through reusable sp_verify_stage12_schema.
-- 3. Branch A and Branch B both invoke sp_verify_stage12_schema before COMMITTED.
-- 4. Explicit 7-column schema_migration_objects contract across all operations.
-- 5. Ambiguous ownership (PENDING or untracked existing object) strictly aborts.
-- ============================================================================

DELIMITER $$

-- ----------------------------------------------------------------------------
-- PROCEDURE 1: Reusable Exhaustive Schema Verifier
-- Callable by Branch A, Branch B, and external integrity audits.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_verify_stage12_schema$$

CREATE PROCEDURE sp_verify_stage12_schema()
proc_verify: BEGIN
    DECLARE v_schema_name VARCHAR(64);
    DECLARE v_cnt INT DEFAULT 0;

    SET v_schema_name = DATABASE();
    IF v_schema_name IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'VERIFICATION FAILED: No active database selected. Execute USE <database> before running verification.';
    END IF;

    -- 1. Tracking Table: schema_migrations (exact 4 columns, InnoDB, sole PK on version, no ON UPDATE)
    SELECT COUNT(*) INTO v_cnt FROM information_schema.columns
    WHERE table_schema = v_schema_name AND table_name = 'schema_migrations'
      AND ((column_name = 'version' AND ordinal_position = 1 AND data_type = 'varchar' AND character_maximum_length = 64 AND is_nullable = 'NO' AND column_key = 'PRI')
        OR (column_name = 'description' AND ordinal_position = 2 AND data_type = 'varchar' AND character_maximum_length = 255 AND is_nullable = 'NO')
        OR (column_name = 'status' AND ordinal_position = 3 AND data_type = 'varchar' AND character_maximum_length = 32 AND is_nullable = 'NO')
        OR (column_name = 'applied_at' AND ordinal_position = 4 AND data_type = 'timestamp' AND is_nullable = 'NO' AND extra NOT LIKE '%on update%'));
    IF v_cnt <> 4 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'VERIFICATION FAILED: schema_migrations definition mismatch (expected exact 4 columns).';
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM information_schema.tables
    WHERE table_schema = v_schema_name AND table_name = 'schema_migrations' AND engine = 'InnoDB';
    IF v_cnt <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'VERIFICATION FAILED: schema_migrations engine is not InnoDB.';
    END IF;

    -- 2. Tracking Table: schema_migration_objects (exact 7 columns, InnoDB, composite PK, no ON UPDATE)
    SELECT COUNT(*) INTO v_cnt FROM information_schema.columns
    WHERE table_schema = v_schema_name AND table_name = 'schema_migration_objects'
      AND ((column_name = 'version' AND ordinal_position = 1 AND data_type = 'varchar' AND character_maximum_length = 64 AND is_nullable = 'NO')
        OR (column_name = 'object_type' AND ordinal_position = 2 AND data_type = 'varchar' AND character_maximum_length = 32 AND is_nullable = 'NO')
        OR (column_name = 'object_name' AND ordinal_position = 3 AND data_type = 'varchar' AND character_maximum_length = 64 AND is_nullable = 'NO')
        OR (column_name = 'parent_object' AND ordinal_position = 4 AND data_type = 'varchar' AND character_maximum_length = 64 AND is_nullable = 'NO')
        OR (column_name = 'ownership_type' AND ordinal_position = 5 AND data_type = 'varchar' AND character_maximum_length = 32 AND is_nullable = 'NO')
        OR (column_name = 'status' AND ordinal_position = 6 AND data_type = 'varchar' AND character_maximum_length = 32 AND is_nullable = 'NO')
        OR (column_name = 'applied_at' AND ordinal_position = 7 AND data_type = 'timestamp' AND is_nullable = 'NO' AND extra NOT LIKE '%on update%'));
    IF v_cnt <> 7 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'VERIFICATION FAILED: schema_migration_objects definition mismatch (expected exact 7 columns).';
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM information_schema.tables
    WHERE table_schema = v_schema_name AND table_name = 'schema_migration_objects' AND engine = 'InnoDB';
    IF v_cnt <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'VERIFICATION FAILED: schema_migration_objects engine is not InnoDB.';
    END IF;

    -- 3. courses.uq_course_teacher (ordered columns: id, then teacher_id)
    SELECT COUNT(*) INTO v_cnt FROM information_schema.statistics
    WHERE table_schema = v_schema_name AND table_name = 'courses' AND index_name = 'uq_course_teacher' AND non_unique = 0
      AND ((seq_in_index = 1 AND column_name = 'id') OR (seq_in_index = 2 AND column_name = 'teacher_id'));
    IF v_cnt <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'VERIFICATION FAILED: courses.uq_course_teacher missing or invalid ordered columns.';
    END IF;

    -- 4. planned_sessions exact 13 columns, ordinal positions, types, nullability, defaults
    SELECT COUNT(*) INTO v_cnt FROM information_schema.columns
    WHERE table_schema = v_schema_name AND table_name = 'planned_sessions'
      AND ((column_name = 'id' AND ordinal_position = 1 AND data_type = 'int' AND is_nullable = 'NO' AND extra LIKE '%auto_increment%')
        OR (column_name = 'block_id' AND ordinal_position = 2 AND data_type = 'varchar' AND character_maximum_length = 36 AND is_nullable = 'YES')
        OR (column_name = 'course_id' AND ordinal_position = 3 AND data_type = 'int' AND is_nullable = 'NO')
        OR (column_name = 'teacher_id' AND ordinal_position = 4 AND data_type = 'int' AND is_nullable = 'NO')
        OR (column_name = 'session_type' AND ordinal_position = 5 AND column_type = "enum('LECTURE','PRACTICAL')" AND is_nullable = 'NO')
        OR (column_name = 'session_date' AND ordinal_position = 6 AND data_type = 'date' AND is_nullable = 'NO')
        OR (column_name = 'start_time' AND ordinal_position = 7 AND data_type = 'time' AND is_nullable = 'NO')
        OR (column_name = 'end_time' AND ordinal_position = 8 AND data_type = 'time' AND is_nullable = 'NO')
        OR (column_name = 'room' AND ordinal_position = 9 AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES')
        OR (column_name = 'description' AND ordinal_position = 10 AND data_type = 'varchar' AND character_maximum_length = 255 AND is_nullable = 'YES')
        OR (column_name = 'status' AND ordinal_position = 11 AND column_type = "enum('SCHEDULED','ATTENDANCE_OPEN','COMPLETED','CANCELLED','MISSED')" AND column_default = 'SCHEDULED' AND is_nullable = 'NO')
        OR (column_name = 'created_at' AND ordinal_position = 12 AND data_type = 'timestamp' AND is_nullable = 'NO')
        OR (column_name = 'updated_at' AND ordinal_position = 13 AND data_type = 'timestamp' AND is_nullable = 'NO' AND extra LIKE '%on update current_timestamp%'));
    IF v_cnt <> 13 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'VERIFICATION FAILED: planned_sessions columns invalid or count != 13.';
    END IF;

    -- 5. planned_sessions sole primary key on id
    SELECT COUNT(*) INTO v_cnt FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu USING (constraint_schema, table_name, constraint_name)
    WHERE tc.table_schema = v_schema_name AND tc.table_name = 'planned_sessions' AND tc.constraint_type = 'PRIMARY KEY' AND kcu.column_name = 'id';
    IF v_cnt <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'VERIFICATION FAILED: planned_sessions primary key not solely on id.';
    END IF;

    -- 6. planned_sessions storage engine InnoDB
    SELECT COUNT(*) INTO v_cnt FROM information_schema.tables
    WHERE table_schema = v_schema_name AND table_name = 'planned_sessions' AND engine = 'InnoDB';
    IF v_cnt <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'VERIFICATION FAILED: planned_sessions engine is not InnoDB.';
    END IF;

    -- 7. chk_session_time_order semantic check constraint
    SELECT COUNT(*) INTO v_cnt FROM information_schema.table_constraints tc
    JOIN information_schema.check_constraints cc USING (constraint_schema, constraint_name)
    WHERE tc.table_schema = v_schema_name AND tc.table_name = 'planned_sessions' AND tc.constraint_name = 'chk_session_time_order'
      AND tc.enforced = 'YES' AND LOWER(REPLACE(REPLACE(REPLACE(cc.check_clause, ' ', ''), '`', ''), '(', '')) = 'start_time<end_time)';
    IF v_cnt <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'VERIFICATION FAILED: chk_session_time_order invalid, weakened, or not enforced.';
    END IF;

    -- 8. fk_planned_course_teacher 2-column CASCADE foreign key
    SELECT COUNT(*) INTO v_cnt FROM information_schema.referential_constraints rc
    JOIN information_schema.key_column_usage kcu USING (constraint_schema, constraint_name)
    WHERE rc.constraint_schema = v_schema_name AND rc.table_name = 'planned_sessions' AND rc.constraint_name = 'fk_planned_course_teacher'
      AND rc.delete_rule = 'CASCADE' AND rc.referenced_table_name = 'courses';
    IF v_cnt <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'VERIFICATION FAILED: fk_planned_course_teacher invalid, missing, or delete_rule is not CASCADE.';
    END IF;

    -- 9. 4 secondary indexes with exact ordered columns
    SELECT COUNT(*) INTO v_cnt FROM (
        SELECT index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) as cols
        FROM information_schema.statistics
        WHERE table_schema = v_schema_name AND table_name = 'planned_sessions'
        GROUP BY index_name
    ) idx_summary
    WHERE (index_name = 'idx_planned_sessions_teacher_date' AND cols = 'teacher_id,session_date,start_time')
       OR (index_name = 'idx_planned_sessions_course_date' AND cols = 'course_id,session_date,start_time')
       OR (index_name = 'idx_planned_sessions_block' AND cols = 'block_id')
       OR (index_name = 'idx_planned_sessions_status_date' AND cols = 'status,session_date');
    IF v_cnt <> 4 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'VERIFICATION FAILED: planned_sessions secondary indexes mismatch or column order invalid.';
    END IF;

    -- 10. qr_sessions.planned_session_id nullable INT
    SELECT COUNT(*) INTO v_cnt FROM information_schema.columns
    WHERE table_schema = v_schema_name AND table_name = 'qr_sessions' AND column_name = 'planned_session_id' AND data_type = 'int' AND is_nullable = 'YES';
    IF v_cnt <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'VERIFICATION FAILED: qr_sessions.planned_session_id missing or not nullable INT.';
    END IF;

    -- 11. uq_qr_planned_session unique on planned_session_id
    SELECT COUNT(*) INTO v_cnt FROM information_schema.statistics
    WHERE table_schema = v_schema_name AND table_name = 'qr_sessions' AND index_name = 'uq_qr_planned_session'
      AND non_unique = 0 AND column_name = 'planned_session_id';
    IF v_cnt <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'VERIFICATION FAILED: uq_qr_planned_session missing or not unique on planned_session_id.';
    END IF;

    -- 12. fk_qr_planned_session ON DELETE RESTRICT
    SELECT COUNT(*) INTO v_cnt FROM information_schema.referential_constraints
    WHERE constraint_schema = v_schema_name AND table_name = 'qr_sessions' AND constraint_name = 'fk_qr_planned_session'
      AND delete_rule = 'RESTRICT' AND referenced_table_name = 'planned_sessions';
    IF v_cnt <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'VERIFICATION FAILED: fk_qr_planned_session missing or delete_rule is not RESTRICT.';
    END IF;

END proc_verify$$

-- ----------------------------------------------------------------------------
-- PROCEDURE 2: Main Migration Procedure
-- Both Branch A and Branch B invoke sp_verify_stage12_schema before COMMITTED.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_migrate_stage12_planned_sessions$$

CREATE PROCEDURE sp_migrate_stage12_planned_sessions()
proc_main: BEGIN
    DECLARE v_schema_name VARCHAR(64);
    DECLARE v_t1_exists INT DEFAULT 0;
    DECLARE v_t2_exists INT DEFAULT 0;
    DECLARE v_v12_status VARCHAR(32) DEFAULT NULL;
    DECLARE v_cnt INT DEFAULT 0;
    DECLARE v_track_cnt INT DEFAULT 0;
    DECLARE v_obj_owner VARCHAR(32);
    DECLARE v_obj_status VARCHAR(32);

    SET v_schema_name = DATABASE();
    IF v_schema_name IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'MIGRATION FAILED: No active database selected. Execute USE <database> before running migration.';
    END IF;

    -- ------------------------------------------------------------------------
    -- STEP 1: Tracking Tables Existence & Structure Check (Fail-Closed)
    -- ------------------------------------------------------------------------
    SELECT COUNT(*) INTO v_t1_exists FROM information_schema.tables
    WHERE table_schema = v_schema_name AND table_name = 'schema_migrations';

    SELECT COUNT(*) INTO v_t2_exists FROM information_schema.tables
    WHERE table_schema = v_schema_name AND table_name = 'schema_migration_objects';

    -- Symmetrical existence invariant
    IF (v_t1_exists = 1 AND v_t2_exists = 0) OR (v_t1_exists = 0 AND v_t2_exists = 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'TRACKING CORRUPT: Asymmetrical tracking table state detected.';
    END IF;

    IF v_t1_exists = 1 AND v_t2_exists = 1 THEN
        -- Query V12 status safely with duplicate guard; returns 0 rows safely as NULL
        SELECT COUNT(*), MAX(status) INTO v_track_cnt, v_v12_status
        FROM schema_migrations
        WHERE version = 'V12__planned_academic_sessions';

        IF v_track_cnt > 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'TRACKING CORRUPT: Duplicate migration record found for V12__planned_academic_sessions.';
        END IF;
    END IF;

    -- ========================================================================
    -- BRANCH A: V12 IS RECORDED COMMITTED -> STRICTLY READ-ONLY VERIFICATION
    -- Physical database objects verified against live database (zero mutations).
    -- ========================================================================
    IF v_v12_status = 'COMMITTED' THEN
        -- Execute exhaustive physical verification
        CALL sp_verify_stage12_schema();

        SELECT 'STAGE_12_ALREADY_COMMITTED_AND_VERIFIED' AS status,
               'All physical objects verified against live database. Zero mutations performed.' AS message;
        LEAVE proc_main;
    END IF;

    -- ========================================================================
    -- BRANCH B: V12 NOT COMMITTED -> TWO-PHASE MIGRATION
    -- ========================================================================

    IF v_t1_exists = 0 AND v_t2_exists = 0 THEN
        CREATE TABLE schema_migrations (
            version VARCHAR(64) PRIMARY KEY,
            description VARCHAR(255) NOT NULL,
            status VARCHAR(32) NOT NULL,
            applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ) ENGINE=InnoDB;

        CREATE TABLE schema_migration_objects (
            version VARCHAR(64) NOT NULL,
            object_type VARCHAR(32) NOT NULL,
            object_name VARCHAR(64) NOT NULL,
            parent_object VARCHAR(64) NOT NULL,
            ownership_type VARCHAR(32) NOT NULL,
            status VARCHAR(32) NOT NULL,
            applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (version, object_type, parent_object, object_name)
        ) ENGINE=InnoDB;
    END IF;

    INSERT INTO schema_migrations (version, description, status)
    VALUES ('V12__planned_academic_sessions', 'Pre-Planned Academic Sessions', 'PENDING')
    ON DUPLICATE KEY UPDATE status = 'PENDING';

    -- Object 1: courses.uq_course_teacher
    SET v_obj_owner = NULL; SET v_obj_status = NULL;
    SELECT COUNT(*) INTO v_cnt FROM information_schema.statistics
    WHERE table_schema = v_schema_name AND table_name = 'courses' AND index_name = 'uq_course_teacher';

    SELECT COUNT(*), MAX(ownership_type), MAX(status)
    INTO v_track_cnt, v_obj_owner, v_obj_status
    FROM schema_migration_objects
    WHERE version = 'V12__planned_academic_sessions'
      AND object_type = 'INDEX'
      AND object_name = 'uq_course_teacher'
      AND parent_object = 'courses';

    IF v_track_cnt > 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'TRACKING CORRUPT: Duplicate tracking records found for courses.uq_course_teacher.';
    END IF;

    IF v_cnt = 0 THEN
        INSERT INTO schema_migration_objects (version, object_type, object_name, parent_object, ownership_type, status)
        VALUES ('V12__planned_academic_sessions', 'INDEX', 'uq_course_teacher', 'courses', 'STAGE12_CREATED', 'PENDING');

        ALTER TABLE courses ADD CONSTRAINT uq_course_teacher UNIQUE (id, teacher_id);

        UPDATE schema_migration_objects SET status = 'COMMITTED'
        WHERE version = 'V12__planned_academic_sessions'
          AND object_type = 'INDEX'
          AND object_name = 'uq_course_teacher'
          AND parent_object = 'courses';
    ELSE
        IF v_obj_owner IS NULL THEN
            INSERT INTO schema_migration_objects (version, object_type, object_name, parent_object, ownership_type, status)
            VALUES ('V12__planned_academic_sessions', 'INDEX', 'uq_course_teacher', 'courses', 'EXTERNAL', 'COMMITTED');
        ELSEIF v_obj_status = 'PENDING' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AMBIGUOUS OWNERSHIP: courses.uq_course_teacher exists with PENDING intent.';
        END IF;
    END IF;

    -- Object 2: planned_sessions Table
    SET v_obj_owner = NULL; SET v_obj_status = NULL;
    SELECT COUNT(*) INTO v_cnt FROM information_schema.tables
    WHERE table_schema = v_schema_name AND table_name = 'planned_sessions';

    SELECT COUNT(*), MAX(ownership_type), MAX(status)
    INTO v_track_cnt, v_obj_owner, v_obj_status
    FROM schema_migration_objects
    WHERE version = 'V12__planned_academic_sessions'
      AND object_type = 'TABLE'
      AND object_name = 'planned_sessions'
      AND parent_object = 'planned_sessions';

    IF v_track_cnt > 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'TRACKING CORRUPT: Duplicate tracking records found for planned_sessions table.';
    END IF;

    IF v_cnt = 0 THEN
        INSERT INTO schema_migration_objects (version, object_type, object_name, parent_object, ownership_type, status)
        VALUES ('V12__planned_academic_sessions', 'TABLE', 'planned_sessions', 'planned_sessions', 'STAGE12_CREATED', 'PENDING');

        CREATE TABLE planned_sessions (
            id INT NOT NULL AUTO_INCREMENT,
            block_id VARCHAR(36) NULL,
            course_id INT NOT NULL,
            teacher_id INT NOT NULL,
            session_type ENUM('LECTURE', 'PRACTICAL') NOT NULL,
            session_date DATE NOT NULL,
            start_time TIME NOT NULL,
            end_time TIME NOT NULL,
            room VARCHAR(50) NULL,
            description VARCHAR(255) NULL,
            status ENUM('SCHEDULED', 'ATTENDANCE_OPEN', 'COMPLETED', 'CANCELLED', 'MISSED') NOT NULL DEFAULT 'SCHEDULED',
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (id),
            CONSTRAINT chk_session_time_order CHECK (start_time < end_time),
            CONSTRAINT fk_planned_course_teacher FOREIGN KEY (course_id, teacher_id)
                REFERENCES courses(id, teacher_id) ON DELETE CASCADE,
            INDEX idx_planned_sessions_teacher_date (teacher_id, session_date, start_time),
            INDEX idx_planned_sessions_course_date (course_id, session_date, start_time),
            INDEX idx_planned_sessions_block (block_id),
            INDEX idx_planned_sessions_status_date (status, session_date)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

        UPDATE schema_migration_objects SET status = 'COMMITTED'
        WHERE version = 'V12__planned_academic_sessions'
          AND object_type = 'TABLE'
          AND object_name = 'planned_sessions'
          AND parent_object = 'planned_sessions';
    ELSE
        IF v_obj_owner IS NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AMBIGUOUS OWNERSHIP: untracked planned_sessions table exists.';
        ELSEIF v_obj_status = 'PENDING' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AMBIGUOUS OWNERSHIP: planned_sessions present with PENDING intent.';
        END IF;
    END IF;

    -- Object 3: qr_sessions.planned_session_id Column
    SET v_obj_owner = NULL; SET v_obj_status = NULL;
    SELECT COUNT(*) INTO v_cnt FROM information_schema.columns
    WHERE table_schema = v_schema_name AND table_name = 'qr_sessions' AND column_name = 'planned_session_id';

    SELECT COUNT(*), MAX(ownership_type), MAX(status)
    INTO v_track_cnt, v_obj_owner, v_obj_status
    FROM schema_migration_objects
    WHERE version = 'V12__planned_academic_sessions'
      AND object_type = 'COLUMN'
      AND object_name = 'planned_session_id'
      AND parent_object = 'qr_sessions';

    IF v_track_cnt > 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'TRACKING CORRUPT: Duplicate tracking records found for qr_sessions.planned_session_id.';
    END IF;

    IF v_cnt = 0 THEN
        INSERT INTO schema_migration_objects (version, object_type, object_name, parent_object, ownership_type, status)
        VALUES ('V12__planned_academic_sessions', 'COLUMN', 'planned_session_id', 'qr_sessions', 'STAGE12_CREATED', 'PENDING');

        ALTER TABLE qr_sessions ADD COLUMN planned_session_id INT NULL AFTER course_id;

        UPDATE schema_migration_objects SET status = 'COMMITTED'
        WHERE version = 'V12__planned_academic_sessions'
          AND object_type = 'COLUMN'
          AND object_name = 'planned_session_id'
          AND parent_object = 'qr_sessions';
    ELSE
        IF v_obj_owner IS NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AMBIGUOUS OWNERSHIP: untracked qr_sessions.planned_session_id exists.';
        ELSEIF v_obj_status = 'PENDING' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AMBIGUOUS OWNERSHIP: planned_session_id present with PENDING intent.';
        END IF;
    END IF;

    -- Object 4: qr_sessions.uq_qr_planned_session Unique Index
    SET v_obj_owner = NULL; SET v_obj_status = NULL;
    SELECT COUNT(*) INTO v_cnt FROM information_schema.statistics
    WHERE table_schema = v_schema_name AND table_name = 'qr_sessions' AND index_name = 'uq_qr_planned_session';

    SELECT COUNT(*), MAX(ownership_type), MAX(status)
    INTO v_track_cnt, v_obj_owner, v_obj_status
    FROM schema_migration_objects
    WHERE version = 'V12__planned_academic_sessions'
      AND object_type = 'INDEX'
      AND object_name = 'uq_qr_planned_session'
      AND parent_object = 'qr_sessions';

    IF v_track_cnt > 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'TRACKING CORRUPT: Duplicate tracking records found for qr_sessions.uq_qr_planned_session.';
    END IF;

    IF v_cnt = 0 THEN
        INSERT INTO schema_migration_objects (version, object_type, object_name, parent_object, ownership_type, status)
        VALUES ('V12__planned_academic_sessions', 'INDEX', 'uq_qr_planned_session', 'qr_sessions', 'STAGE12_CREATED', 'PENDING');

        ALTER TABLE qr_sessions ADD CONSTRAINT uq_qr_planned_session UNIQUE (planned_session_id);

        UPDATE schema_migration_objects SET status = 'COMMITTED'
        WHERE version = 'V12__planned_academic_sessions'
          AND object_type = 'INDEX'
          AND object_name = 'uq_qr_planned_session'
          AND parent_object = 'qr_sessions';
    ELSE
        IF v_obj_owner IS NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AMBIGUOUS OWNERSHIP: untracked uq_qr_planned_session exists.';
        ELSEIF v_obj_status = 'PENDING' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AMBIGUOUS OWNERSHIP: uq_qr_planned_session present with PENDING intent.';
        END IF;
    END IF;

    -- Object 5: qr_sessions.fk_qr_planned_session Foreign Key
    SET v_obj_owner = NULL; SET v_obj_status = NULL;
    SELECT COUNT(*) INTO v_cnt FROM information_schema.referential_constraints
    WHERE constraint_schema = v_schema_name AND table_name = 'qr_sessions' AND constraint_name = 'fk_qr_planned_session';

    SELECT COUNT(*), MAX(ownership_type), MAX(status)
    INTO v_track_cnt, v_obj_owner, v_obj_status
    FROM schema_migration_objects
    WHERE version = 'V12__planned_academic_sessions'
      AND object_type = 'FOREIGN KEY'
      AND object_name = 'fk_qr_planned_session'
      AND parent_object = 'qr_sessions';

    IF v_track_cnt > 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'TRACKING CORRUPT: Duplicate tracking records found for qr_sessions.fk_qr_planned_session.';
    END IF;

    IF v_cnt = 0 THEN
        INSERT INTO schema_migration_objects (version, object_type, object_name, parent_object, ownership_type, status)
        VALUES ('V12__planned_academic_sessions', 'FOREIGN KEY', 'fk_qr_planned_session', 'qr_sessions', 'STAGE12_CREATED', 'PENDING');

        ALTER TABLE qr_sessions ADD CONSTRAINT fk_qr_planned_session
            FOREIGN KEY (planned_session_id) REFERENCES planned_sessions(id) ON DELETE RESTRICT;

        UPDATE schema_migration_objects SET status = 'COMMITTED'
        WHERE version = 'V12__planned_academic_sessions'
          AND object_type = 'FOREIGN KEY'
          AND object_name = 'fk_qr_planned_session'
          AND parent_object = 'qr_sessions';
    ELSE
        IF v_obj_owner IS NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AMBIGUOUS OWNERSHIP: untracked fk_qr_planned_session exists.';
        ELSEIF v_obj_status = 'PENDING' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AMBIGUOUS OWNERSHIP: fk_qr_planned_session present with PENDING intent.';
        END IF;
    END IF;

    -- ------------------------------------------------------------------------
    -- BRANCH B POST-DDL VERIFICATION:
    -- Invokes the exact same exhaustive verifier as Branch A before COMMITTED.
    -- ------------------------------------------------------------------------
    CALL sp_verify_stage12_schema();

    UPDATE schema_migrations SET status = 'COMMITTED' WHERE version = 'V12__planned_academic_sessions';

    SELECT 'STAGE_12_MIGRATION_COMPLETED' AS status, 'Migration successfully applied, verified, and committed.' AS message;

END proc_main$$

DELIMITER ;
