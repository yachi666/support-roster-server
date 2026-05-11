package com.support.server.supportrosterserver.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FlywayStartupConfigTest {

    @Test
    void shouldEnableOutOfOrderMigrations() {
        FlywayStartupConfig config = new FlywayStartupConfig();
        DataSource dataSource = mock(DataSource.class);

        Flyway flyway = config.flyway(dataSource);

        assertTrue(flyway.getConfiguration().isOutOfOrder(),
            "Expected Flyway startup configuration to allow out-of-order migrations.");
    }
}
