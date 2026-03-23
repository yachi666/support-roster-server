-- Workspace migration for the team-only model.
-- This script captures the table-structure changes applied during the
-- team-based refactor, plus the required backfill and duplicate cleanup
-- needed before the new unique index can be created safely.

BEGIN;

DROP INDEX IF EXISTS uk_workspace_team_code;

ALTER TABLE IF EXISTS workspace_team
    DROP COLUMN IF EXISTS team_code;

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_team_name_normalized
    ON workspace_team (LOWER(BTRIM(name)))
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

UPDATE workspace_shift_definition shift_definition
SET team_id = rel.team_id
FROM workspace_team_role_group_rel rel
WHERE shift_definition.team_id IS NULL
  AND shift_definition.role_group_id = rel.role_group_id;

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
    ALTER COLUMN role_group_id DROP NOT NULL;

ALTER TABLE IF EXISTS workspace_roster_assignment
    ALTER COLUMN role_group_id DROP NOT NULL;

WITH ranked AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY team_id, code
            ORDER BY update_time DESC, id DESC
        ) AS row_num
    FROM workspace_shift_definition
    WHERE deleted = 0
      AND team_id IS NOT NULL
)
UPDATE workspace_shift_definition target
SET deleted = 1
WHERE target.id IN (
    SELECT id
    FROM ranked
    WHERE row_num > 1
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_shift_definition_team_code
    ON workspace_shift_definition (team_id, code)
    WHERE deleted = 0 AND team_id IS NOT NULL;

COMMIT;
