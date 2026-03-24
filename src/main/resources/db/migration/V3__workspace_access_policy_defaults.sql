INSERT INTO workspace_access_policy (page_code, auth_required)
SELECT seed.page_code, seed.auth_required
FROM (
    VALUES
        ('teams', FALSE)
) AS seed(page_code, auth_required)
WHERE NOT EXISTS (
    SELECT 1
    FROM workspace_access_policy existing
    WHERE existing.page_code = seed.page_code
      AND existing.deleted = 0
);

UPDATE workspace_access_policy
SET auth_required = FALSE
WHERE deleted = 0
  AND page_code IN ('overview', 'roster', 'staff', 'shifts', 'teams', 'validation', 'import-export');

UPDATE workspace_access_policy
SET auth_required = TRUE
WHERE deleted = 0
  AND page_code = 'accounts';
