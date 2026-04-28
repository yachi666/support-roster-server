# Workspace Roster Upgrade Implementation

## Scope

This upgrade aligns the roster model with the confirmed business rules across backend, frontend, and data migration:

- Team identity uses `Team Name` only.
- Team uniqueness is `trim + case-insensitive`.
- `Shift Code` edits must not break historical staff assignments.
- Historical roster views should display the latest shift code.
- 24-hour shifts are represented by `startTime + durationMinutes`, where `1440` means full day.

This delivery spans two repositories:

- `support-roster-server`
- `support-roster-ui`

## Functional Changes

### 1. Team model simplification

- Removed `teamCode` from workspace team data structures and APIs.
- Team uniqueness now relies on normalized `name` only.
- Team management UI no longer displays or edits `Team Code`.

### 2. Shift assignment stability

- Shift assignments now rely on internal `shiftDefinitionId` as the authoritative linkage.
- Editing a shift definition code no longer breaks historical assignments.
- Historical roster rendering resolves the current shift definition code from the linked definition.
- Assignment cleanup on shift-definition deletion is restricted to `shiftDefinitionId` linkage.

### 3. 24-hour shift support

- Added `durationMinutes` to shift definitions.
- `endTime` is preserved as a derived/compatibility value where needed.
- `00:00 -> 00:00` is interpreted as a 24-hour shift during migration/backfill and represented internally as `durationMinutes = 1440`.
- Frontend editing now uses `startTime + durationMinutes` and provides a `+24h` preset.

## Backend Implementation Summary

Key server-side updates:

- `workspace_team.team_code` removed from the model.
- Added normalized unique index on `LOWER(BTRIM(name))`.
- Added `workspace_shift_definition.duration_minutes`.
- Added migration/backfill logic from `start_time` and `end_time` to `duration_minutes`.
- Introduced `WorkspaceShiftTimeSupport` to centralize shift duration logic.
- Updated roster, validation, import/export, and viewer flows to use ID-based shift linkage.
- Updated OpenAPI schema and server specs to reflect the new contract.

## Frontend Implementation Summary

Key UI updates:

- Team management no longer captures `Team Code`.
- Shift definition editing uses `durationMinutes` rather than direct `endTime` input.
- Shift timing helpers now support duration-first semantics while remaining compatible with legacy callers.
- Monthly roster and related workspace pages now rely on internal identifiers rather than team code semantics.

## Data Migration Plan

### Schema/data changes

1. Add `team_id` to `workspace_staff` and backfill from team-role-group relationships.
2. Add `team_id` to `workspace_shift_definition` and backfill from team-role-group relationships.
3. Add `duration_minutes` to `workspace_shift_definition`.
4. Backfill `duration_minutes` from `start_time` and `end_time`.
5. Convert zero-difference shifts to `1440` minutes.
6. Create/update relationship and uniqueness indexes needed by the new model.
7. Drop `workspace_team.team_code`.
8. Add normalized unique index for team names.

## Database Scripts and Execution Steps

This section is the operator runbook for the database upgrade.

### 1. Prerequisites

- Target database engine: PostgreSQL
- Recommended order: database migration -> backend deployment -> frontend deployment
- Before execution, make sure the application is stopped or the upgrade window is controlled to avoid concurrent writes

### 2. Pre-check SQL

Run these checks first. If any of them return unexpected rows, stop and resolve them before continuing.

#### 2.1 Check whether normalized duplicate team names exist

```sql
SELECT LOWER(BTRIM(name)) AS normalized_name,
       COUNT(*) AS duplicate_count,
       STRING_AGG(id::text, ', ' ORDER BY id) AS team_ids
FROM workspace_team
WHERE deleted = 0
GROUP BY LOWER(BTRIM(name))
HAVING COUNT(*) > 1;
```

Expected result: `0 rows`

#### 2.2 Check whether any staff rows cannot be backfilled to `team_id`

```sql
SELECT staff.id,
       staff.staff_id,
       staff.role_group_id
FROM workspace_staff staff
LEFT JOIN workspace_team_role_group_rel rel
       ON rel.role_group_id = staff.role_group_id
WHERE staff.deleted = 0
  AND staff.role_group_id IS NOT NULL
  AND rel.team_id IS NULL;
```

Expected result: `0 rows`

#### 2.3 Check whether any shift definitions cannot be backfilled to `team_id`

```sql
SELECT shift_definition.id,
       shift_definition.code,
       shift_definition.role_group_id
FROM workspace_shift_definition shift_definition
LEFT JOIN workspace_team_role_group_rel rel
       ON rel.role_group_id = shift_definition.role_group_id
WHERE shift_definition.deleted = 0
  AND rel.team_id IS NULL;
```

Expected result: `0 rows`

### 3. Database Backup

Create a backup before applying the migration.

Example:

```bash
export PGPASSWORD='<your-password>'
pg_dump -h <host> -U <user> -d <database> -Fc -f support-roster-pre-upgrade.dump
```

### 4. Upgrade SQL Script

Save the following as `roster_model_upgrade.sql` and run it against the target database.

```sql
BEGIN;

ALTER TABLE workspace_staff
    ADD COLUMN IF NOT EXISTS team_id BIGINT REFERENCES workspace_team (id);

UPDATE workspace_staff staff
SET team_id = rel.team_id
FROM workspace_team_role_group_rel rel
WHERE staff.team_id IS NULL
  AND staff.role_group_id = rel.role_group_id;

ALTER TABLE workspace_shift_definition
    ADD COLUMN IF NOT EXISTS team_id BIGINT REFERENCES workspace_team (id);

UPDATE workspace_shift_definition shift_definition
SET team_id = rel.team_id
FROM workspace_team_role_group_rel rel
WHERE shift_definition.team_id IS NULL
  AND shift_definition.role_group_id = rel.role_group_id;

ALTER TABLE workspace_shift_definition
    ADD COLUMN IF NOT EXISTS duration_minutes INTEGER;

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

ALTER TABLE workspace_shift_definition
    ALTER COLUMN duration_minutes SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_shift_definition_team_code
    ON workspace_shift_definition (team_id, code)
    WHERE deleted = 0 AND team_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS workspace_shift_definition_team_rel (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    shift_definition_id BIGINT NOT NULL REFERENCES workspace_shift_definition (id),
    team_id BIGINT NOT NULL REFERENCES workspace_team (id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_shift_definition_team_rel
    ON workspace_shift_definition_team_rel (shift_definition_id, team_id);

INSERT INTO workspace_shift_definition_team_rel (shift_definition_id, team_id)
SELECT id, team_id
FROM workspace_shift_definition
WHERE team_id IS NOT NULL
ON CONFLICT DO NOTHING;

DROP INDEX IF EXISTS uk_workspace_team_code;
ALTER TABLE workspace_team DROP COLUMN IF EXISTS team_code;

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_team_name_normalized
    ON workspace_team (LOWER(BTRIM(name)))
    WHERE deleted = 0;

COMMIT;
```

### 5. Execution Command

Example:

```bash
export PGPASSWORD='<your-password>'
psql -h <host> -U <user> -d <database> -v ON_ERROR_STOP=1 -f roster_model_upgrade.sql
```

### 6. Post-upgrade Verification SQL

#### 6.1 Confirm `team_code` is removed

```sql
SELECT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_name = 'workspace_team'
      AND column_name = 'team_code'
) AS team_code_still_exists;
```

Expected result: `false`

#### 6.2 Confirm `duration_minutes` exists

```sql
SELECT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_name = 'workspace_shift_definition'
      AND column_name = 'duration_minutes'
) AS duration_minutes_exists;
```

Expected result: `true`

#### 6.3 Check duration backfill samples

```sql
SELECT code,
       start_time,
       end_time,
       duration_minutes
FROM workspace_shift_definition
ORDER BY id
LIMIT 20;
```

What to confirm:

- normal `8h` shifts are backfilled correctly
- `00:00 -> 00:00` rows become `1440`
- overnight shifts keep the correct duration

#### 6.4 Confirm relationship backfill counts

```sql
SELECT
    (SELECT COUNT(*) FROM workspace_staff WHERE team_id IS NOT NULL) AS staff_with_team_id,
    (SELECT COUNT(*) FROM workspace_shift_definition WHERE team_id IS NOT NULL) AS shift_definitions_with_team_id,
    (SELECT COUNT(*) FROM workspace_shift_definition_team_rel) AS shift_definition_team_rel_rows;
```

#### 6.5 Confirm normalized uniqueness is enforced

Do not run this in production unless you are comfortable with a rollback of the test row. Prefer to verify using the duplicate pre-check query above.

Validation probe:

```sql
INSERT INTO workspace_team (id, name, color, display_order, visible, description, deleted)
VALUES (-1, '  l1 support team  ', '#000000', 9999, true, 'duplicate probe', 0);
```

Expected result: insert should fail with the unique index violation on `uk_workspace_team_name_normalized`.

### 7. Deployment Steps

1. Run the pre-check SQL.
2. Back up the database.
3. Execute `roster_model_upgrade.sql`.
4. Run the post-upgrade verification SQL.
5. Deploy `support-roster-server`.
6. Verify backend startup and core APIs.
7. Deploy `support-roster-ui`.
8. Run the smoke-test checklist below.

### Rollout order

1. Back up the target database.
2. Verify there are no duplicate normalized team names before applying the unique index.
3. Apply the database migration.
4. Deploy the backend.
5. Deploy the frontend.
6. Run smoke tests on team maintenance, shift definition editing, monthly roster display, and import/export.

## Validation Performed

The migration was validated on a disposable local PostgreSQL database by replaying current test data and then applying the migration logic.

Validated results:

- `workspace_team.team_code` removed successfully.
- `workspace_shift_definition.duration_minutes` added and backfilled successfully.
- Standard 8-hour shifts backfilled correctly to `480`.
- A 24-hour sample row `00:00 -> 00:00` backfilled correctly to `1440`.
- Shift-definition/team relation rows were backfilled successfully.
- Staff `team_id` values were backfilled successfully.
- The normalized unique index rejected a duplicate team name probe (`"  l1 support team  "`).

## Smoke Test Checklist

After deployment, verify:

1. Team maintenance can create and edit teams without `Team Code`.
2. Team names reject duplicates that differ only by case or surrounding spaces.
3. Editing a shift definition code does not remove or reassign existing roster entries.
4. Historical roster cells display the updated shift code after a rename.
5. A 24-hour shift can be created and displayed correctly.
6. Import/export still works with workbook-style start/end columns.
7. Validation and roster overview pages render expected shift/team data.

## Rollback Considerations

- Restore the pre-upgrade database backup if migration issues are found.
- Roll back backend and frontend deployments together if contract incompatibility appears.
- Do not partially revert only one repository after the schema change has been applied.

## Delivered Verification

- Backend: `mvn test`
- Frontend: `npm run build`
- Disposable DB migration validation: passed
