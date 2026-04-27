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
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workspace_linux_password_access_audit_staff
    ON workspace_linux_password_access_audit (staff_id, create_time DESC);

CREATE INDEX IF NOT EXISTS idx_workspace_linux_password_access_audit_credential
    ON workspace_linux_password_access_audit (credential_id, create_time DESC);
