CREATE OR REPLACE FUNCTION set_update_time()
RETURNS TRIGGER
LANGUAGE plpgsql
AS 'BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;';

CREATE TABLE IF NOT EXISTS workspace_role_group (
    id BIGINT PRIMARY KEY,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(64),
    region VARCHAR(64),
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_role_group_code
    ON workspace_role_group (code)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS workspace_team (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    color VARCHAR(64) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    deleted INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DROP INDEX IF EXISTS uk_workspace_team_code;

ALTER TABLE IF EXISTS workspace_team
    DROP COLUMN IF EXISTS team_code;

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_team_name_normalized
    ON workspace_team (LOWER(BTRIM(name)))
    WHERE deleted = 0;

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
    staff_id VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(64),
    slack VARCHAR(128),
    region VARCHAR(64),
    timezone VARCHAR(128),
    role_name VARCHAR(255),
    team_id BIGINT REFERENCES workspace_team (id),
    role_group_id BIGINT REFERENCES workspace_role_group (id),
    status VARCHAR(32) NOT NULL DEFAULT 'Active',
    avatar TEXT,
    notes TEXT,
    deleted INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_staff_id
    ON workspace_staff (staff_id)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS workspace_account (
    id BIGINT PRIMARY KEY,
    staff_record_id BIGINT NOT NULL REFERENCES workspace_staff (id),
    staff_id VARCHAR(128) NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    account_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_ACTIVATION',
    password_hash VARCHAR(255),
    password_set_at TIMESTAMP,
    auth_source VARCHAR(32) NOT NULL DEFAULT 'LOCAL_PASSWORD',
    external_subject VARCHAR(255),
    notes TEXT,
    last_login_at TIMESTAMP,
    token_version BIGINT NOT NULL DEFAULT 1,
    deleted INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_account_staff_id
    ON workspace_account (staff_id)
    WHERE deleted = 0;

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_account_staff_record_id
    ON workspace_account (staff_record_id)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS workspace_account_team_scope (
    id BIGINT PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES workspace_account (id),
    team_id BIGINT NOT NULL REFERENCES workspace_team (id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_account_team_scope
    ON workspace_account_team_scope (account_id, team_id);

CREATE TABLE IF NOT EXISTS workspace_shift_definition (
    id BIGINT PRIMARY KEY,
    team_id BIGINT REFERENCES workspace_team (id),
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
    deleted INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_shift_definition_role_group_code
    ON workspace_shift_definition (role_group_id, code)
    WHERE deleted = 0;

ALTER TABLE IF EXISTS workspace_staff
    ADD COLUMN IF NOT EXISTS team_id BIGINT REFERENCES workspace_team (id);

UPDATE workspace_staff staff
SET team_id = rel.team_id
FROM workspace_team_role_group_rel rel
WHERE staff.team_id IS NULL
  AND staff.role_group_id = rel.role_group_id;

ALTER TABLE IF EXISTS workspace_shift_definition
    ADD COLUMN IF NOT EXISTS team_id BIGINT REFERENCES workspace_team (id);

ALTER TABLE IF EXISTS workspace_shift_definition
    ADD COLUMN IF NOT EXISTS duration_minutes INTEGER;

UPDATE workspace_shift_definition
SET duration_minutes = CASE
    WHEN start_time IS NULL OR end_time IS NULL THEN 480
    ELSE MOD(
        CAST(EXTRACT(EPOCH FROM end_time) / 60 AS INTEGER)
        - CAST(EXTRACT(EPOCH FROM start_time) / 60 AS INTEGER)
        + 1440,
        1440
    )
END
WHERE duration_minutes IS NULL;

UPDATE workspace_shift_definition
SET duration_minutes = 1440
WHERE duration_minutes = 0;

ALTER TABLE IF EXISTS workspace_shift_definition
    ALTER COLUMN duration_minutes SET NOT NULL;

UPDATE workspace_shift_definition shift_definition
SET team_id = rel.team_id
FROM workspace_team_role_group_rel rel
WHERE shift_definition.team_id IS NULL
  AND shift_definition.role_group_id = rel.role_group_id;

ALTER TABLE IF EXISTS workspace_shift_definition
    ALTER COLUMN role_group_id DROP NOT NULL;

ALTER TABLE IF EXISTS workspace_roster_assignment
    ALTER COLUMN role_group_id DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_shift_definition_team_code
    ON workspace_shift_definition (team_id, code)
    WHERE deleted = 0 AND team_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS workspace_shift_definition_team_rel (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    shift_definition_id BIGINT NOT NULL REFERENCES workspace_shift_definition (id),
    team_id BIGINT NOT NULL REFERENCES workspace_team (id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_shift_definition_team_rel
    ON workspace_shift_definition_team_rel (shift_definition_id, team_id);

CREATE INDEX IF NOT EXISTS idx_workspace_shift_definition_team_rel_team
    ON workspace_shift_definition_team_rel (team_id, shift_definition_id);

INSERT INTO workspace_shift_definition_team_rel (shift_definition_id, team_id)
SELECT id, team_id
FROM workspace_shift_definition
WHERE team_id IS NOT NULL
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS workspace_roster_assignment (
    id BIGINT PRIMARY KEY,
    staff_id BIGINT NOT NULL REFERENCES workspace_staff (id),
    role_group_id BIGINT REFERENCES workspace_role_group (id),
    team_id BIGINT NOT NULL REFERENCES workspace_team (id),
    shift_definition_id BIGINT NOT NULL REFERENCES workspace_shift_definition (id),
    assignment_date DATE NOT NULL,
    shift_code VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    notes TEXT,
    deleted INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_roster_assignment_staff_date
    ON workspace_roster_assignment (staff_id, assignment_date)
    WHERE deleted = 0;

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

DROP TRIGGER IF EXISTS trg_workspace_account_update_time ON workspace_account;
CREATE TRIGGER trg_workspace_account_update_time
    BEFORE UPDATE ON workspace_account
    FOR EACH ROW
    EXECUTE FUNCTION set_update_time();

DROP TRIGGER IF EXISTS trg_workspace_account_team_scope_update_time ON workspace_account_team_scope;
CREATE TRIGGER trg_workspace_account_team_scope_update_time
    BEFORE UPDATE ON workspace_account_team_scope
    FOR EACH ROW
    EXECUTE FUNCTION set_update_time();

DROP TRIGGER IF EXISTS trg_workspace_shift_definition_update_time ON workspace_shift_definition;
CREATE TRIGGER trg_workspace_shift_definition_update_time
    BEFORE UPDATE ON workspace_shift_definition
    FOR EACH ROW
    EXECUTE FUNCTION set_update_time();

DROP TRIGGER IF EXISTS trg_workspace_shift_definition_team_rel_update_time ON workspace_shift_definition_team_rel;
CREATE TRIGGER trg_workspace_shift_definition_team_rel_update_time
    BEFORE UPDATE ON workspace_shift_definition_team_rel
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

-- Linux password tables (V6 + V7 + V11 + V13)

CREATE TABLE IF NOT EXISTS workspace_linux_password_server (
    id BIGINT PRIMARY KEY,
    hostname VARCHAR(255) NOT NULL,
    ip VARCHAR(128) NOT NULL,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'online',
    deleted INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_linux_password_server_hostname
    ON workspace_linux_password_server (LOWER(BTRIM(hostname)))
    WHERE deleted = 0;

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_linux_password_server_ip
    ON workspace_linux_password_server (LOWER(BTRIM(ip)))
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS workspace_linux_password_server_business_unit (
    id BIGINT PRIMARY KEY,
    server_id BIGINT NOT NULL REFERENCES workspace_linux_password_server (id),
    business_unit VARCHAR(255) NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_linux_password_server_business_unit
    ON workspace_linux_password_server_business_unit (server_id, LOWER(BTRIM(business_unit)))
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS workspace_linux_password_directory (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_linux_password_directory_name
    ON workspace_linux_password_directory (LOWER(BTRIM(name)))
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS workspace_linux_password_credential (
    id BIGINT PRIMARY KEY,
    server_id BIGINT NOT NULL REFERENCES workspace_linux_password_server (id),
    username VARCHAR(255) NOT NULL,
    password_ciphertext TEXT NOT NULL,
    password_iv VARCHAR(128) NOT NULL,
    key_version VARCHAR(32) NOT NULL DEFAULT 'v1',
    notes VARCHAR(500),
    deleted INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_linux_password_credential_username
    ON workspace_linux_password_credential (server_id, LOWER(BTRIM(username)))
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_workspace_linux_password_credential_server
    ON workspace_linux_password_credential (server_id)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS workspace_linux_password_access_audit (
    id BIGINT PRIMARY KEY,
    account_id BIGINT,
    staff_record_id BIGINT,
    staff_id VARCHAR(128),
    staff_name VARCHAR(255),
    server_id BIGINT,
    credential_id BIGINT,
    action VARCHAR(32) NOT NULL,
    result VARCHAR(32) NOT NULL,
    client_ip VARCHAR(128),
    user_agent VARCHAR(500),
    hostname_snapshot VARCHAR(255),
    ip_snapshot VARCHAR(128),
    username_snapshot VARCHAR(255),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workspace_linux_password_access_audit_staff
    ON workspace_linux_password_access_audit (staff_id, create_time DESC);

CREATE INDEX IF NOT EXISTS idx_workspace_linux_password_access_audit_credential
    ON workspace_linux_password_access_audit (credential_id, create_time DESC);

CREATE INDEX IF NOT EXISTS idx_workspace_linux_password_access_audit_hostname_snapshot
    ON workspace_linux_password_access_audit (LOWER(hostname_snapshot), create_time DESC)
    WHERE hostname_snapshot IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_workspace_linux_password_access_audit_username_snapshot
    ON workspace_linux_password_access_audit (LOWER(username_snapshot), create_time DESC)
    WHERE username_snapshot IS NOT NULL;

DROP TRIGGER IF EXISTS trg_workspace_linux_password_server_update_time ON workspace_linux_password_server;
CREATE TRIGGER trg_workspace_linux_password_server_update_time
    BEFORE UPDATE ON workspace_linux_password_server
    FOR EACH ROW
    EXECUTE FUNCTION set_update_time();

DROP TRIGGER IF EXISTS trg_workspace_linux_password_server_business_unit_update_time ON workspace_linux_password_server_business_unit;
CREATE TRIGGER trg_workspace_linux_password_server_business_unit_update_time
    BEFORE UPDATE ON workspace_linux_password_server_business_unit
    FOR EACH ROW
    EXECUTE FUNCTION set_update_time();

DROP TRIGGER IF EXISTS trg_workspace_linux_password_directory_update_time ON workspace_linux_password_directory;
CREATE TRIGGER trg_workspace_linux_password_directory_update_time
    BEFORE UPDATE ON workspace_linux_password_directory
    FOR EACH ROW
    EXECUTE FUNCTION set_update_time();

DROP TRIGGER IF EXISTS trg_workspace_linux_password_credential_update_time ON workspace_linux_password_credential;
CREATE TRIGGER trg_workspace_linux_password_credential_update_time
    BEFORE UPDATE ON workspace_linux_password_credential
    FOR EACH ROW
    EXECUTE FUNCTION set_update_time();

DROP TRIGGER IF EXISTS trg_workspace_linux_password_access_audit_update_time ON workspace_linux_password_access_audit;
CREATE TRIGGER trg_workspace_linux_password_access_audit_update_time
    BEFORE UPDATE ON workspace_linux_password_access_audit
    FOR EACH ROW
    EXECUTE FUNCTION set_update_time();
