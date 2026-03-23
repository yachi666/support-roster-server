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

