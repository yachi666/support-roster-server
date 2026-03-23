CREATE OR REPLACE FUNCTION set_update_time()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE IF NOT EXISTS workspace_role_group (
    id BIGINT PRIMARY KEY,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(64),
    region VARCHAR(64),
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_role_group_code
    ON workspace_role_group (code)
    WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS workspace_team (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    color VARCHAR(64) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_team_name_normalized
    ON workspace_team (LOWER(BTRIM(name)))
    WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS workspace_team_role_group_rel (
    id BIGINT PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES workspace_team (id),
    role_group_id BIGINT NOT NULL REFERENCES workspace_role_group (id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_team_role_group_rel
    ON workspace_team_role_group_rel (team_id, role_group_id);

CREATE TABLE IF NOT EXISTS workspace_staff (
    id BIGINT PRIMARY KEY,
    staff_code VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(64),
    slack VARCHAR(128),
    region VARCHAR(64),
    timezone VARCHAR(128),
    role_name VARCHAR(255),
    role_group_id BIGINT REFERENCES workspace_role_group (id),
    status VARCHAR(32) NOT NULL DEFAULT 'Active',
    avatar TEXT,
    notes TEXT,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_staff_code
    ON workspace_staff (staff_code)
    WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS workspace_shift_definition (
    id BIGINT PRIMARY KEY,
    role_group_id BIGINT NOT NULL REFERENCES workspace_role_group (id),
    code VARCHAR(64) NOT NULL,
    meaning VARCHAR(255) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME,
    duration_minutes INTEGER NOT NULL DEFAULT 480,
    timezone VARCHAR(128) NOT NULL,
    primary_shift BOOLEAN NOT NULL DEFAULT FALSE,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    color_hex VARCHAR(16),
    remark TEXT,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_shift_definition_role_group_code
    ON workspace_shift_definition (role_group_id, code)
    WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS workspace_roster_assignment (
    id BIGINT PRIMARY KEY,
    staff_id BIGINT NOT NULL REFERENCES workspace_staff (id),
    role_group_id BIGINT NOT NULL REFERENCES workspace_role_group (id),
    team_id BIGINT NOT NULL REFERENCES workspace_team (id),
    shift_definition_id BIGINT NOT NULL REFERENCES workspace_shift_definition (id),
    assignment_date DATE NOT NULL,
    shift_code VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    notes TEXT,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_roster_assignment_staff_date
    ON workspace_roster_assignment (staff_id, assignment_date)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_workspace_roster_assignment_team_date
    ON workspace_roster_assignment (team_id, assignment_date);

CREATE TABLE IF NOT EXISTS workspace_import_batch (
    id BIGINT PRIMARY KEY,
    roster_year INTEGER NOT NULL,
    roster_month INTEGER NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_records INTEGER NOT NULL DEFAULT 0,
    valid_records INTEGER NOT NULL DEFAULT 0,
    invalid_records INTEGER NOT NULL DEFAULT 0,
    operator_name VARCHAR(128),
    applied_time TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS workspace_import_record (
    id BIGINT PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES workspace_import_batch (id),
    sheet_name VARCHAR(128) NOT NULL,
    row_number INTEGER NOT NULL,
    record_type VARCHAR(64) NOT NULL,
    payload_json JSONB NOT NULL,
    valid BOOLEAN NOT NULL DEFAULT TRUE,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS workspace_import_issue (
    id BIGINT PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES workspace_import_batch (id),
    import_record_id BIGINT REFERENCES workspace_import_record (id),
    severity VARCHAR(16) NOT NULL,
    issue_type VARCHAR(128) NOT NULL,
    description TEXT NOT NULL,
    team_name VARCHAR(255),
    role_group_code VARCHAR(128),
    staff_name VARCHAR(255),
    issue_date DATE,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workspace_import_issue_batch
    ON workspace_import_issue (batch_id, resolved);

CREATE TABLE IF NOT EXISTS workspace_operation_log (
    id BIGINT PRIMARY KEY,
    actor VARCHAR(128) NOT NULL,
    action VARCHAR(255) NOT NULL,
    target_type VARCHAR(64),
    target_id VARCHAR(128),
    details TEXT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DROP TRIGGER IF EXISTS trg_workspace_role_group_update_time ON workspace_role_group;
CREATE TRIGGER trg_workspace_role_group_update_time
    BEFORE UPDATE ON workspace_role_group
    FOR EACH ROW
    EXECUTE FUNCTION set_update_time();

DROP TRIGGER IF EXISTS trg_workspace_team_update_time ON workspace_team;
CREATE TRIGGER trg_workspace_team_update_time
    BEFORE UPDATE ON workspace_team
    FOR EACH ROW
    EXECUTE FUNCTION set_update_time();

DROP TRIGGER IF EXISTS trg_workspace_team_role_group_rel_update_time ON workspace_team_role_group_rel;
CREATE TRIGGER trg_workspace_team_role_group_rel_update_time
    BEFORE UPDATE ON workspace_team_role_group_rel
    FOR EACH ROW
    EXECUTE FUNCTION set_update_time();

DROP TRIGGER IF EXISTS trg_workspace_staff_update_time ON workspace_staff;
CREATE TRIGGER trg_workspace_staff_update_time
    BEFORE UPDATE ON workspace_staff
    FOR EACH ROW
    EXECUTE FUNCTION set_update_time();

DROP TRIGGER IF EXISTS trg_workspace_shift_definition_update_time ON workspace_shift_definition;
CREATE TRIGGER trg_workspace_shift_definition_update_time
    BEFORE UPDATE ON workspace_shift_definition
    FOR EACH ROW
    EXECUTE FUNCTION set_update_time();

DROP TRIGGER IF EXISTS trg_workspace_roster_assignment_update_time ON workspace_roster_assignment;
CREATE TRIGGER trg_workspace_roster_assignment_update_time
    BEFORE UPDATE ON workspace_roster_assignment
    FOR EACH ROW
    EXECUTE FUNCTION set_update_time();

DROP TRIGGER IF EXISTS trg_workspace_import_batch_update_time ON workspace_import_batch;
CREATE TRIGGER trg_workspace_import_batch_update_time
    BEFORE UPDATE ON workspace_import_batch
    FOR EACH ROW
    EXECUTE FUNCTION set_update_time();

DROP TRIGGER IF EXISTS trg_workspace_import_record_update_time ON workspace_import_record;
CREATE TRIGGER trg_workspace_import_record_update_time
    BEFORE UPDATE ON workspace_import_record
    FOR EACH ROW
    EXECUTE FUNCTION set_update_time();

DROP TRIGGER IF EXISTS trg_workspace_import_issue_update_time ON workspace_import_issue;
CREATE TRIGGER trg_workspace_import_issue_update_time
    BEFORE UPDATE ON workspace_import_issue
    FOR EACH ROW
    EXECUTE FUNCTION set_update_time();

DROP TRIGGER IF EXISTS trg_workspace_operation_log_update_time ON workspace_operation_log;
CREATE TRIGGER trg_workspace_operation_log_update_time
    BEFORE UPDATE ON workspace_operation_log
    FOR EACH ROW
    EXECUTE FUNCTION set_update_time();
