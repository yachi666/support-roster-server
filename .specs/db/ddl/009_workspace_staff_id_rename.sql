-- Runtime migration counterpart: src/main/resources/db/migration/V12__workspace_staff_id_rename.sql
-- Purpose: normalize the employee login identifier to staff_id.

-- Main semantic changes:
-- - workspace_staff uses staff_id for the employee login identifier.
-- - workspace_account uses staff_record_id for the workspace_staff primary-key reference.
-- - workspace_account uses staff_id for the employee login identifier.
-- - support_team_contact_staff uses staff_id for contact staff bindings.
-- - workspace_linux_password_access_audit uses staff_id for password access audit identity.

-- See the Flyway migration for idempotent guards that support both existing
-- databases and fresh databases initialized from the updated V1 baseline.
