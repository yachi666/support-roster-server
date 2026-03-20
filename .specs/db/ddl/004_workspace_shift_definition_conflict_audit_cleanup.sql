-- Audit and cleanup for shift definitions after introducing
-- workspace_shift_definition_team_rel.
--
-- Purpose:
-- 1. Audit codes that are shared by multiple teams but no longer have a single definition.
-- 2. Safely merge duplicate definitions that are still identical after normalization.
-- 3. Leave genuinely different variants in place for manual review.

-- -----------------------------------------------------------------------------
-- Audit 1: overview of codes shared across teams with multiple distinct variants.
-- -----------------------------------------------------------------------------
WITH definition_base AS (
    SELECT
        sd.id,
        sd.code,
        COALESCE(NULLIF(BTRIM(REPLACE(sd.meaning, '　', ' ')), ''), '') AS meaning,
        COALESCE(sd.start_time::text, '') AS start_time,
        COALESCE(sd.end_time::text, '') AS end_time,
        COALESCE(NULLIF(BTRIM(REPLACE(sd.timezone, '　', ' ')), ''), '') AS timezone,
        COALESCE(sd.primary_shift, FALSE)::text AS primary_shift,
        COALESCE(sd.visible, FALSE)::text AS visible,
        COALESCE(NULLIF(BTRIM(REPLACE(sd.color_hex, '　', ' ')), ''), '') AS color_hex,
        COALESCE(NULLIF(BTRIM(REPLACE(sd.remark, '　', ' ')), ''), '') AS remark,
        sd.update_time,
        md5(concat_ws(
            '|',
            COALESCE(NULLIF(BTRIM(REPLACE(sd.meaning, '　', ' ')), ''), ''),
            COALESCE(sd.start_time::text, ''),
            COALESCE(sd.end_time::text, ''),
            COALESCE(NULLIF(BTRIM(REPLACE(sd.timezone, '　', ' ')), ''), ''),
            COALESCE(sd.primary_shift, FALSE)::text,
            COALESCE(sd.visible, FALSE)::text,
            COALESCE(NULLIF(BTRIM(REPLACE(sd.color_hex, '　', ' ')), ''), ''),
            COALESCE(NULLIF(BTRIM(REPLACE(sd.remark, '　', ' ')), ''), '')
        )) AS fingerprint
    FROM workspace_shift_definition sd
    WHERE sd.deleted = 0
),
definition_scope AS (
    SELECT
        db.id,
        db.code,
        db.fingerprint,
        rel.team_id,
        team.name AS team_name
    FROM definition_base db
    JOIN workspace_shift_definition_team_rel rel
      ON rel.shift_definition_id = db.id
    JOIN workspace_team team
      ON team.id = rel.team_id
),
conflicted_codes AS (
    SELECT code
    FROM definition_scope
    GROUP BY code
    HAVING COUNT(DISTINCT team_id) > 1
       AND COUNT(DISTINCT fingerprint) > 1
)
SELECT
    code,
    COUNT(DISTINCT fingerprint) AS variant_count,
    COUNT(DISTINCT team_id) AS team_count,
    string_agg(DISTINCT team_name, ', ' ORDER BY team_name) AS team_names
FROM definition_scope
WHERE code IN (SELECT code FROM conflicted_codes)
GROUP BY code
ORDER BY code;

-- -----------------------------------------------------------------------------
-- Audit 2: detailed rows for manual review.
-- -----------------------------------------------------------------------------
WITH definition_base AS (
    SELECT
        sd.id,
        sd.code,
        COALESCE(NULLIF(BTRIM(REPLACE(sd.meaning, '　', ' ')), ''), '') AS meaning,
        COALESCE(sd.start_time::text, '') AS start_time,
        COALESCE(sd.end_time::text, '') AS end_time,
        COALESCE(NULLIF(BTRIM(REPLACE(sd.timezone, '　', ' ')), ''), '') AS timezone,
        COALESCE(sd.primary_shift, FALSE)::text AS primary_shift,
        COALESCE(sd.visible, FALSE)::text AS visible,
        COALESCE(NULLIF(BTRIM(REPLACE(sd.color_hex, '　', ' ')), ''), '') AS color_hex,
        COALESCE(NULLIF(BTRIM(REPLACE(sd.remark, '　', ' ')), ''), '') AS remark,
        sd.update_time,
        md5(concat_ws(
            '|',
            COALESCE(NULLIF(BTRIM(REPLACE(sd.meaning, '　', ' ')), ''), ''),
            COALESCE(sd.start_time::text, ''),
            COALESCE(sd.end_time::text, ''),
            COALESCE(NULLIF(BTRIM(REPLACE(sd.timezone, '　', ' ')), ''), ''),
            COALESCE(sd.primary_shift, FALSE)::text,
            COALESCE(sd.visible, FALSE)::text,
            COALESCE(NULLIF(BTRIM(REPLACE(sd.color_hex, '　', ' ')), ''), ''),
            COALESCE(NULLIF(BTRIM(REPLACE(sd.remark, '　', ' ')), ''), '')
        )) AS fingerprint
    FROM workspace_shift_definition sd
    WHERE sd.deleted = 0
),
definition_scope AS (
    SELECT
        db.*,
        rel.team_id,
        team.name AS team_name
    FROM definition_base db
    JOIN workspace_shift_definition_team_rel rel
      ON rel.shift_definition_id = db.id
    JOIN workspace_team team
      ON team.id = rel.team_id
),
conflicted_codes AS (
    SELECT code
    FROM definition_scope
    GROUP BY code
    HAVING COUNT(DISTINCT team_id) > 1
       AND COUNT(DISTINCT fingerprint) > 1
)
SELECT
    code,
    team_id,
    team_name,
    id AS shift_definition_id,
    meaning,
    start_time,
    end_time,
    timezone,
    primary_shift,
    visible,
    color_hex,
    remark,
    fingerprint,
    update_time
FROM definition_scope
WHERE code IN (SELECT code FROM conflicted_codes)
ORDER BY code, fingerprint, team_name, shift_definition_id;

-- -----------------------------------------------------------------------------
-- Cleanup: merge only exact duplicate variants.
--
-- Rules:
-- - A canonical definition is chosen by latest update_time, then highest id.
-- - Only definitions with the same code and the same normalized fingerprint are merged.
-- - Team relations are moved onto the canonical definition.
-- - Roster assignments are repointed to the canonical definition.
-- - Non-canonical definitions are soft-deleted.
-- - Codes that still have multiple fingerprints remain untouched for manual review.
-- -----------------------------------------------------------------------------
BEGIN;

WITH definition_base AS (
    SELECT
        sd.id,
        sd.code,
        sd.update_time,
        md5(concat_ws(
            '|',
            COALESCE(NULLIF(BTRIM(REPLACE(sd.meaning, '　', ' ')), ''), ''),
            COALESCE(sd.start_time::text, ''),
            COALESCE(sd.end_time::text, ''),
            COALESCE(NULLIF(BTRIM(REPLACE(sd.timezone, '　', ' ')), ''), ''),
            COALESCE(sd.primary_shift, FALSE)::text,
            COALESCE(sd.visible, FALSE)::text,
            COALESCE(NULLIF(BTRIM(REPLACE(sd.color_hex, '　', ' ')), ''), ''),
            COALESCE(NULLIF(BTRIM(REPLACE(sd.remark, '　', ' ')), ''), '')
        )) AS fingerprint
    FROM workspace_shift_definition sd
    WHERE sd.deleted = 0
),
ranked_variants AS (
    SELECT
        id,
        code,
        fingerprint,
        ROW_NUMBER() OVER (
            PARTITION BY code, fingerprint
            ORDER BY update_time DESC NULLS LAST, id DESC
        ) AS row_num,
        FIRST_VALUE(id) OVER (
            PARTITION BY code, fingerprint
            ORDER BY update_time DESC NULLS LAST, id DESC
        ) AS canonical_id
    FROM definition_base
),
duplicate_rows AS (
    SELECT
        code,
        fingerprint,
        canonical_id,
        id AS duplicate_id
    FROM ranked_variants
    WHERE row_num > 1
),
insert_relations AS (
    INSERT INTO workspace_shift_definition_team_rel (shift_definition_id, team_id)
    SELECT DISTINCT
        dr.canonical_id,
        rel.team_id
    FROM duplicate_rows dr
    JOIN workspace_shift_definition_team_rel rel
      ON rel.shift_definition_id = dr.duplicate_id
    ON CONFLICT DO NOTHING
    RETURNING shift_definition_id, team_id
),
update_assignments AS (
    UPDATE workspace_roster_assignment assignment
    SET shift_definition_id = dr.canonical_id
    FROM duplicate_rows dr
    WHERE assignment.deleted = 0
      AND assignment.shift_definition_id = dr.duplicate_id
    RETURNING assignment.id
),
delete_relations AS (
    DELETE FROM workspace_shift_definition_team_rel rel
    USING duplicate_rows dr
    WHERE rel.shift_definition_id = dr.duplicate_id
    RETURNING rel.shift_definition_id, rel.team_id
)
UPDATE workspace_shift_definition definition
SET deleted = 1
FROM duplicate_rows dr
WHERE definition.id = dr.duplicate_id
  AND definition.deleted = 0;

COMMIT;

-- -----------------------------------------------------------------------------
-- Post-check: no duplicate code + fingerprint groups should remain.
-- -----------------------------------------------------------------------------
WITH definition_base AS (
    SELECT
        sd.id,
        sd.code,
        md5(concat_ws(
            '|',
            COALESCE(NULLIF(BTRIM(REPLACE(sd.meaning, '　', ' ')), ''), ''),
            COALESCE(sd.start_time::text, ''),
            COALESCE(sd.end_time::text, ''),
            COALESCE(NULLIF(BTRIM(REPLACE(sd.timezone, '　', ' ')), ''), ''),
            COALESCE(sd.primary_shift, FALSE)::text,
            COALESCE(sd.visible, FALSE)::text,
            COALESCE(NULLIF(BTRIM(REPLACE(sd.color_hex, '　', ' ')), ''), ''),
            COALESCE(NULLIF(BTRIM(REPLACE(sd.remark, '　', ' ')), ''), '')
        )) AS fingerprint
    FROM workspace_shift_definition sd
    WHERE sd.deleted = 0
)
SELECT
    code,
    fingerprint,
    COUNT(*) AS active_definition_count
FROM definition_base
GROUP BY code, fingerprint
HAVING COUNT(*) > 1
ORDER BY code, fingerprint;