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
