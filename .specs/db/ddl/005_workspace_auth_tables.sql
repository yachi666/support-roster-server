-- Workspace auth and authorization tables

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
