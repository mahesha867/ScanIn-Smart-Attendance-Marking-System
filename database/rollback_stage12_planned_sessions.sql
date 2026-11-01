-- ============================================================================
-- SCANIN Attendance Management System — Stage 12 Rollback
-- File: database/rollback_stage12_planned_sessions.sql
-- Invariants:
-- 1. All-or-nothing preflight verification before any destructive DDL.
-- 2. Strictly refuses rollback if migration not COMMITTED, any object unowned, or data present.
-- 3. Scopes all foreign-key checks to current schema and referenced table.
-- 4. Idempotently cleans up Stage 12-owned objects on retry without dropping EXTERNAL objects.
-- 5. Preserves tracking tables (schema_migrations, schema_migration_objects).
-- ============================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS sp_rollback_stage12_planned_sessions$$

CREATE PROCEDURE sp_rollback_stage12_planned_sessions()
proc_rollback: BEGIN
    DECLARE v_schema_name VARCHAR(64);
    DECLARE v_cnt INT DEFAULT 0;
    DECLARE v_owner VARCHAR(32);
    DECLARE v_status VARCHAR(32);

    SET v_schema_name = DATABASE();

    -- ------------------------------------------------------------------------
    -- PREFLIGHT 1: Verify Tracking Tables & V12 COMMITTED State
    -- ------------------------------------------------------------------------
    SELECT COUNT(*) INTO v_cnt FROM information_schema.tables
    WHERE table_schema = v_schema_name AND table_name = 'schema_migrations';
    IF v_cnt = 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ROLLBACK ABORTED: schema_migrations table does not exist.'; END IF;

    SELECT COUNT(*) INTO v_cnt FROM schema_migrations
    WHERE version = 'V12__planned_academic_sessions' AND status = 'COMMITTED';
    IF v_cnt = 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ROLLBACK ABORTED: V12 migration is not in COMMITTED state.'; END IF;

    -- ------------------------------------------------------------------------
    -- PREFLIGHT 2: Verify Every Removal Candidate is STAGE12_CREATED & COMMITTED
    -- ------------------------------------------------------------------------
    SELECT COUNT(*) INTO v_cnt FROM schema_migration_objects
    WHERE version = 'V12__planned_academic_sessions' AND status <> 'COMMITTED';
    IF v_cnt > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Ambiguous objects with status != COMMITTED exist.'; END IF;

    -- Check planned_sessions table ownership
    SET v_owner = NULL; SET v_status = NULL;
    SELECT ownership_type, status INTO v_owner, v_status FROM schema_migration_objects
    WHERE version = 'V12__planned_academic_sessions' AND object_type = 'TABLE' AND object_name = 'planned_sessions' AND parent_object = 'planned_sessions' LIMIT 1;
    IF v_owner <> 'STAGE12_CREATED' OR v_status <> 'COMMITTED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ROLLBACK ABORTED: planned_sessions is not positively STAGE12_CREATED.';
    END IF;

    -- Check qr_sessions.planned_session_id column ownership
    SET v_owner = NULL; SET v_status = NULL;
    SELECT ownership_type, status INTO v_owner, v_status FROM schema_migration_objects
    WHERE version = 'V12__planned_academic_sessions' AND object_type = 'COLUMN' AND object_name = 'planned_session_id' AND parent_object = 'qr_sessions' LIMIT 1;
    IF v_owner <> 'STAGE12_CREATED' OR v_status <> 'COMMITTED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ROLLBACK ABORTED: qr_sessions.planned_session_id is not positively STAGE12_CREATED.';
    END IF;

    -- Check qr_sessions.uq_qr_planned_session index ownership
    SET v_owner = NULL; SET v_status = NULL;
    SELECT ownership_type, status INTO v_owner, v_status FROM schema_migration_objects
    WHERE version = 'V12__planned_academic_sessions' AND object_type = 'INDEX' AND object_name = 'uq_qr_planned_session' AND parent_object = 'qr_sessions' LIMIT 1;
    IF v_owner <> 'STAGE12_CREATED' OR v_status <> 'COMMITTED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ROLLBACK ABORTED: uq_qr_planned_session is not positively STAGE12_CREATED.';
    END IF;

    -- Check qr_sessions.fk_qr_planned_session foreign key ownership
    SET v_owner = NULL; SET v_status = NULL;
    SELECT ownership_type, status INTO v_owner, v_status FROM schema_migration_objects
    WHERE version = 'V12__planned_academic_sessions' AND object_type = 'FOREIGN KEY' AND object_name = 'fk_qr_planned_session' AND parent_object = 'qr_sessions' LIMIT 1;
    IF v_owner <> 'STAGE12_CREATED' OR v_status <> 'COMMITTED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ROLLBACK ABORTED: fk_qr_planned_session is not positively STAGE12_CREATED.';
    END IF;

    -- ------------------------------------------------------------------------
    -- PREFLIGHT 3: Verify Physical Definitions of Objects to be Dropped
    -- ------------------------------------------------------------------------
    SELECT COUNT(*) INTO v_cnt FROM information_schema.tables
    WHERE table_schema = v_schema_name AND table_name = 'planned_sessions';
    IF v_cnt > 0 THEN
        SELECT COUNT(*) INTO v_cnt FROM information_schema.columns
        WHERE table_schema = v_schema_name AND table_name = 'planned_sessions';
        IF v_cnt <> 13 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ROLLBACK ABORTED: planned_sessions definition is corrupt or modified.'; END IF;
    END IF;

    -- ------------------------------------------------------------------------
    -- PREFLIGHT 4: Data Invariance (Zero Planned Sessions & Zero Linked QR Meetings)
    -- ------------------------------------------------------------------------
    SELECT COUNT(*) INTO v_cnt FROM information_schema.tables
    WHERE table_schema = v_schema_name AND table_name = 'planned_sessions';
    IF v_cnt > 0 THEN
        SELECT COUNT(*) INTO v_cnt FROM planned_sessions;
        IF v_cnt > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ROLLBACK ABORTED: planned_sessions contains records.'; END IF;
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM information_schema.columns
    WHERE table_schema = v_schema_name AND table_name = 'qr_sessions' AND column_name = 'planned_session_id';
    IF v_cnt > 0 THEN
        SELECT COUNT(*) INTO v_cnt FROM qr_sessions WHERE planned_session_id IS NOT NULL;
        IF v_cnt > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ROLLBACK ABORTED: qr_sessions contains records linked to planned sessions.'; END IF;
    END IF;

    -- ------------------------------------------------------------------------
    -- PREFLIGHT 5: Scope Foreign Keys and Indexes to Schema and Referenced Table
    -- ------------------------------------------------------------------------
    SELECT COUNT(*) INTO v_cnt FROM information_schema.referential_constraints
    WHERE constraint_schema = v_schema_name
      AND referenced_table_name = 'planned_sessions'
      AND constraint_name <> 'fk_qr_planned_session';
    IF v_cnt > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ROLLBACK ABORTED: External foreign keys reference planned_sessions.'; END IF;

    SELECT COUNT(DISTINCT index_name) INTO v_cnt FROM information_schema.statistics
    WHERE table_schema = v_schema_name AND table_name = 'qr_sessions' AND column_name = 'planned_session_id'
      AND index_name NOT IN ('uq_qr_planned_session', 'fk_qr_planned_session');
    IF v_cnt > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Unexpected indexes on qr_sessions.planned_session_id.'; END IF;

    -- ========================================================================
    -- EXECUTION PHASE: Safe Idempotent Drop Sequence
    -- ========================================================================

    -- 1. Drop Foreign Key fk_qr_planned_session
    SELECT COUNT(*) INTO v_cnt FROM information_schema.referential_constraints
    WHERE constraint_schema = v_schema_name AND table_name = 'qr_sessions' AND constraint_name = 'fk_qr_planned_session';
    IF v_cnt > 0 THEN
        ALTER TABLE qr_sessions DROP FOREIGN KEY fk_qr_planned_session;
    END IF;

    -- 2. Drop Unique Index uq_qr_planned_session
    SELECT COUNT(*) INTO v_cnt FROM information_schema.statistics
    WHERE table_schema = v_schema_name AND table_name = 'qr_sessions' AND index_name = 'uq_qr_planned_session';
    IF v_cnt > 0 THEN
        ALTER TABLE qr_sessions DROP INDEX uq_qr_planned_session;
    END IF;

    -- 3. Drop Column qr_sessions.planned_session_id
    SELECT COUNT(*) INTO v_cnt FROM information_schema.columns
    WHERE table_schema = v_schema_name AND table_name = 'qr_sessions' AND column_name = 'planned_session_id';
    IF v_cnt > 0 THEN
        ALTER TABLE qr_sessions DROP COLUMN planned_session_id;
    END IF;

    -- 4. Drop planned_sessions Table
    SELECT COUNT(*) INTO v_cnt FROM information_schema.tables
    WHERE table_schema = v_schema_name AND table_name = 'planned_sessions';
    IF v_cnt > 0 THEN
        DROP TABLE planned_sessions;
    END IF;

    -- 5. Drop courses.uq_course_teacher ONLY IF STAGE12_CREATED. If EXTERNAL, PRESERVE.
    SET v_owner = NULL;
    SELECT ownership_type INTO v_owner FROM schema_migration_objects
    WHERE version = 'V12__planned_academic_sessions' AND object_type = 'INDEX' AND object_name = 'uq_course_teacher' AND parent_object = 'courses' LIMIT 1;

    IF v_owner = 'STAGE12_CREATED' THEN
        SELECT COUNT(*) INTO v_cnt FROM information_schema.statistics
        WHERE table_schema = v_schema_name AND table_name = 'courses' AND index_name = 'uq_course_teacher';
        IF v_cnt > 0 THEN
            ALTER TABLE courses DROP INDEX uq_course_teacher;
        END IF;
    END IF;

    -- 6. Clean V12 records from tracking tables (preserve tracking tables)
    DELETE FROM schema_migration_objects WHERE version = 'V12__planned_academic_sessions';
    DELETE FROM schema_migrations WHERE version = 'V12__planned_academic_sessions';

    SELECT 'STAGE_12_ROLLBACK_COMPLETED' AS status,
           'All Stage 12 objects cleanly removed. Pre-existing objects and tracking tables preserved.' AS message;

END proc_rollback$$

DELIMITER ;

