ALTER TABLE workspace_account
    ADD COLUMN IF NOT EXISTS token_version BIGINT;

UPDATE workspace_account
SET token_version = 1
WHERE token_version IS NULL;

ALTER TABLE workspace_account
    ALTER COLUMN token_version SET DEFAULT 1;

ALTER TABLE workspace_account
    ALTER COLUMN token_version SET NOT NULL;
