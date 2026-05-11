ALTER TABLE workspace_shift_definition_team_rel
    ADD COLUMN IF NOT EXISTS display_order INTEGER NOT NULL DEFAULT 0;

WITH ordered_relations AS (
    SELECT rel.id,
           ROW_NUMBER() OVER (
               PARTITION BY rel.team_id
               ORDER BY shift_definition.code NULLS LAST, rel.shift_definition_id
           ) - 1 AS next_display_order
    FROM workspace_shift_definition_team_rel rel
    JOIN workspace_shift_definition shift_definition
      ON shift_definition.id = rel.shift_definition_id
)
UPDATE workspace_shift_definition_team_rel rel
SET display_order = ordered_relations.next_display_order
FROM ordered_relations
WHERE rel.id = ordered_relations.id;

DROP INDEX IF EXISTS idx_workspace_shift_definition_team_rel_team;

CREATE INDEX IF NOT EXISTS idx_workspace_shift_definition_team_rel_team
    ON workspace_shift_definition_team_rel (team_id, display_order, shift_definition_id);
