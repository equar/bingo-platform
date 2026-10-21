package com.equar.bingo.backend.common.idempotency;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

class FlywayUpgradeRecoveryIntegrationTest {

    @Test
    void upgradesFromV6AndRepairsDetectedChecksumDrift() {
        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")) {
            mysql.start();
            DataSource dataSource = new DriverManagerDataSource(
                    mysql.getJdbcUrl(),
                    mysql.getUsername(),
                    mysql.getPassword());
            Flyway throughV6 = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("6"))
                    .load();

            throughV6.migrate();
            assertEquals("6", throughV6.info().current().getVersion().getVersion());

            Flyway current = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load();
            current.migrate();
            assertEquals("12", current.info().current().getVersion().getVersion());
            assertDoesNotThrow(current::validate);

            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            Integer originalChecksum = jdbcTemplate.queryForObject(
                    "select checksum from flyway_schema_history where version = '7'",
                    Integer.class);
            assertNotNull(originalChecksum);

            jdbcTemplate.update(
                    "update flyway_schema_history set checksum = checksum + 1 where version = '7'");
            assertThrows(FlywayException.class, current::validate);

            current.repair();
            assertEquals(originalChecksum, jdbcTemplate.queryForObject(
                    "select checksum from flyway_schema_history where version = '7'",
                    Integer.class));
            assertDoesNotThrow(current::validate);
        }
    }
}
