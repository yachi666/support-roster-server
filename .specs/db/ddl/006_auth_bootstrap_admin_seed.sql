-- Auth rollout bootstrap SQL
--
-- Purpose:
-- 1. Ensure the first administrator's staff profile exists in workspace_staff.
-- 2. Provide a safe pre-deployment template before enabling
--    SUPPORT_BOOTSTRAP_ADMIN_STAFF_CODE on the server.
--
-- Recommended usage:
-- - Preferred: create/update only the staff row here, then let the server
--   bootstrap service create or promote the workspace_account on startup.
-- - Fallback: if you cannot use the bootstrap environment variable, adapt the
--   optional manual account section at the bottom and execute it explicitly.
--
-- Replace these placeholders before execution:
-- - 900000000000001001 : unique staff primary key
-- - BOOTSTRAP_ADMIN_001: target staffid / workspace_staff.staff_code
-- - Initial Admin      : display name
-- - admin@example.com  : email
-- - UTC                : timezone

BEGIN;

-- Step 1: ensure the bootstrap admin exists in workspace_staff
INSERT INTO workspace_staff (
    id,
    staff_code,
    name,
    email,
    timezone,
    status,
    deleted
) VALUES (
    900000000000001001,
    'BOOTSTRAP_ADMIN_001',
    'Initial Admin',
    'admin@example.com',
    'UTC',
    'Active',
    0
)
ON CONFLICT (staff_code) WHERE deleted = 0
DO UPDATE SET
    name = EXCLUDED.name,
    email = EXCLUDED.email,
    timezone = EXCLUDED.timezone,
    status = EXCLUDED.status,
    deleted = 0;

-- Step 2: verify the staff row that will be used by SUPPORT_BOOTSTRAP_ADMIN_STAFF_CODE
SELECT
    id,
    staff_code,
    name,
    email,
    status,
    team_id,
    deleted
FROM workspace_staff
WHERE staff_code = 'BOOTSTRAP_ADMIN_001'
  AND deleted = 0;

COMMIT;

-- Preferred rollout after this SQL:
-- 1. Set SUPPORT_BOOTSTRAP_ADMIN_STAFF_CODE=BOOTSTRAP_ADMIN_001
-- 2. Start the server
-- 3. Let WorkspaceAdminBootstrapService create/promote the admin account
-- 4. Login at /login with staffid BOOTSTRAP_ADMIN_001 and set the password
-- 5. Remove SUPPORT_BOOTSTRAP_ADMIN_STAFF_CODE after the first admin takes over
--
-- Optional manual fallback (execute only if you intentionally do NOT use bootstrap):
--
-- INSERT INTO workspace_account (
--     id,
--     staff_id,
--     staff_code,
--     role_code,
--     account_status,
--     password_hash,
--     auth_source,
--     notes,
--     deleted
-- )
-- SELECT
--     900000000000001101,
--     staff.id,
--     staff.staff_code,
--     'admin',
--     'PENDING_ACTIVATION',
--     NULL,
--     'LOCAL_PASSWORD',
--     'Manual bootstrap admin account',
--     0
-- FROM workspace_staff staff
-- WHERE staff.staff_code = 'BOOTSTRAP_ADMIN_001'
--   AND staff.deleted = 0
-- ON CONFLICT (staff_code) WHERE deleted = 0
-- DO UPDATE SET
--     role_code = EXCLUDED.role_code,
--     account_status = EXCLUDED.account_status,
--     password_hash = NULL,
--     auth_source = EXCLUDED.auth_source,
--     notes = EXCLUDED.notes,
--     deleted = 0;
