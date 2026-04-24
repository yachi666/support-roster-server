INSERT INTO workspace_linux_password_directory (id, name, deleted, create_time, update_time)
SELECT
    seed.max_id + ROW_NUMBER() OVER (ORDER BY source.business_unit),
    source.business_unit,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM (
    SELECT DISTINCT ON (LOWER(BTRIM(business_unit)))
        BTRIM(business_unit) AS business_unit
    FROM workspace_linux_password_server_business_unit
    WHERE deleted = 0 AND BTRIM(business_unit) <> ''
    ORDER BY LOWER(BTRIM(business_unit)), BTRIM(business_unit)
) source
CROSS JOIN (
    SELECT COALESCE(MAX(id), 0) AS max_id
    FROM workspace_linux_password_directory
) seed
WHERE NOT EXISTS (
    SELECT 1
    FROM workspace_linux_password_directory target
    WHERE target.deleted = 0
      AND LOWER(BTRIM(target.name)) = LOWER(source.business_unit)
);
