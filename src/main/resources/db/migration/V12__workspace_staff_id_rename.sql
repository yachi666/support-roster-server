DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'workspace_staff' AND column_name = 'staff_code'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'workspace_staff' AND column_name = 'staff_id'
    ) THEN
        ALTER TABLE workspace_staff RENAME COLUMN staff_code TO staff_id;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'workspace_account' AND column_name = 'staff_id'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'workspace_account' AND column_name = 'staff_record_id'
    ) THEN
        ALTER TABLE workspace_account RENAME COLUMN staff_id TO staff_record_id;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'workspace_account' AND column_name = 'staff_code'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'workspace_account' AND column_name = 'staff_id'
    ) THEN
        ALTER TABLE workspace_account RENAME COLUMN staff_code TO staff_id;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'support_team_contact_staff' AND column_name = 'staff_code'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'support_team_contact_staff' AND column_name = 'staff_id'
    ) THEN
        ALTER TABLE support_team_contact_staff RENAME COLUMN staff_code TO staff_id;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'workspace_linux_password_access_audit' AND column_name = 'staff_code'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'workspace_linux_password_access_audit' AND column_name = 'staff_id'
    ) THEN
        ALTER TABLE workspace_linux_password_access_audit RENAME COLUMN staff_code TO staff_id;
    END IF;
END $$;

ALTER INDEX IF EXISTS uk_workspace_staff_code RENAME TO uk_workspace_staff_id;
ALTER INDEX IF EXISTS idx_support_team_contact_staff_code RENAME TO idx_support_team_contact_staff_id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_class index_class
        JOIN pg_index idx ON idx.indexrelid = index_class.oid
        WHERE index_class.relname = 'uk_workspace_account_staff_id'
          AND pg_get_indexdef(idx.indexrelid) ILIKE '%(staff_record_id)%'
    ) THEN
        ALTER INDEX uk_workspace_account_staff_id RENAME TO uk_workspace_account_staff_record_id;
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_class WHERE relname = 'uk_workspace_account_staff_code'
    ) THEN
        ALTER INDEX uk_workspace_account_staff_code RENAME TO uk_workspace_account_staff_id;
    END IF;
END $$;
