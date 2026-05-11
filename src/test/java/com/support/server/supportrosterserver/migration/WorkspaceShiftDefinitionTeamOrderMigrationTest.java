package com.support.server.supportrosterserver.migration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class WorkspaceShiftDefinitionTeamOrderMigrationTest {

    @Test
    void shouldMakeV14DisplayOrderMigrationIdempotentWhileKeepingBackfill() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader()
            .getResourceAsStream("db/migration/V14__workspace_shift_definition_team_order.sql")) {
            assertNotNull(inputStream, "Expected V14 team-order migration to exist.");

            String migration = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(migration.contains("ADD COLUMN IF NOT EXISTS display_order"),
                "Expected V14 migration to add display_order idempotently.");
            assertTrue(migration.contains("UPDATE workspace_shift_definition_team_rel"),
                "Expected V14 migration to keep the display_order backfill update.");
            assertTrue(migration.contains("row_number() OVER") || migration.contains("ROW_NUMBER() OVER"),
                "Expected V14 migration to preserve ordered backfill logic.");
        }
    }
}
