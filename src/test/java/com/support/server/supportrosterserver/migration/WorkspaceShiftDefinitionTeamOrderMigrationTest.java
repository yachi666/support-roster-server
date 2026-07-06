package com.support.server.supportrosterserver.migration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class WorkspaceShiftDefinitionTeamOrderMigrationTest {

    @Test
    void shouldMakeV15DisplayOrderMigrationIdempotentWhileKeepingBackfill() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader()
            .getResourceAsStream("db/migration/V15__workspace_shift_definition_team_display_order.sql")) {
            assertNotNull(inputStream, "Expected V15 team-order migration to exist.");

            String migration = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(migration.contains("ADD COLUMN IF NOT EXISTS display_order"),
                "Expected V15 migration to add display_order idempotently.");
            assertTrue(migration.contains("UPDATE workspace_shift_definition_team_rel"),
                "Expected V15 migration to keep the display_order backfill update.");
            assertTrue(migration.contains("row_number() OVER") || migration.contains("ROW_NUMBER() OVER"),
                "Expected V15 migration to preserve ordered backfill logic.");
        }
    }
}
