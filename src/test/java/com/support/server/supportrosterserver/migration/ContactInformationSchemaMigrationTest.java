package com.support.server.supportrosterserver.migration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ContactInformationSchemaMigrationTest {

    @Test
    void shouldDropNotNullConstraintForTeamEmail() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader()
            .getResourceAsStream("db/migration/V10__support_team_contact_team_email_nullable.sql")) {
            assertNotNull(inputStream, "Expected contact information schema migration to exist.");

            String migration = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(migration.contains("ALTER TABLE support_team_contact"),
                "Expected migration to target support_team_contact.");
            assertTrue(migration.contains("ALTER COLUMN team_email DROP NOT NULL"),
                "Expected migration to make support_team_contact.team_email nullable.");
        }
    }
}
