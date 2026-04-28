-- Add hostname/ip/username snapshot columns to audit table so audit history
-- is self-contained and does not depend on live joins after server/credential deletion.

ALTER TABLE workspace_linux_password_access_audit
    ADD COLUMN IF NOT EXISTS hostname_snapshot VARCHAR(255),
    ADD COLUMN IF NOT EXISTS ip_snapshot VARCHAR(128),
    ADD COLUMN IF NOT EXISTS username_snapshot VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_workspace_linux_password_access_audit_hostname_snapshot
    ON workspace_linux_password_access_audit (LOWER(hostname_snapshot), create_time DESC)
    WHERE hostname_snapshot IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_workspace_linux_password_access_audit_username_snapshot
    ON workspace_linux_password_access_audit (LOWER(username_snapshot), create_time DESC)
    WHERE username_snapshot IS NOT NULL;
