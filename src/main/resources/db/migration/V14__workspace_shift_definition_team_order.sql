ALTER TABLE workspace_shift_definition_team_rel
ADD COLUMN display_order integer NOT NULL DEFAULT 0;

WITH ordered AS (
    SELECT rel.id,
           row_number() OVER (PARTITION BY rel.team_id ORDER BY def.code, rel.shift_definition_id) - 1 AS seq
    FROM workspace_shift_definition_team_rel rel
    JOIN workspace_shift_definition def ON def.id = rel.shift_definition_id
)
UPDATE workspace_shift_definition_team_rel rel
SET display_order = ordered.seq
FROM ordered
WHERE ordered.id = rel.id;
