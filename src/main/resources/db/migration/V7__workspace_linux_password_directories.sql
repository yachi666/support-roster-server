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
