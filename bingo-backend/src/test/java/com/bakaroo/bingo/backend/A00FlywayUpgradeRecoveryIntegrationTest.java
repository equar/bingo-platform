package com.bakaroo.bingo.backend;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Objects;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Runs first and resets only bingo_qa_test before testing upgrades and recovery. */
class A00FlywayUpgradeRecoveryIntegrationTest extends MySqlIntegrationTest {

    @Test
    void initializesFreshSchemaUpgradesFromV6AndRepairsDetectedChecksumDrift() {
        TestDatabase testDatabase = requireIsolatedTestDatabase();
        DataSource dataSource = new DriverManagerDataSource(
                testDatabase.url(), testDatabase.username(), testDatabase.password());
        Flyway current = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .defaultSchema(DATABASE)
                .schemas(DATABASE)
                .cleanDisabled(false)
                .load();

        current.clean();
        MigrationVersion latestVersion = Arrays.stream(current.info().all())
                .map(MigrationInfo::getVersion)
                .filter(Objects::nonNull)
                .max(MigrationVersion::compareTo)
                .orElseThrow();
        assertEquals(current.info().pending().length, current.migrate().migrationsExecuted);
        assertEquals(latestVersion, current.info().current().getVersion());
        assertDoesNotThrow(current::validate);

        current.clean();
        Flyway throughV6 = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .defaultSchema(DATABASE)
                .schemas(DATABASE)
                .target(MigrationVersion.fromVersion("6"))
                .load();
        throughV6.migrate();
        assertEquals("6", throughV6.info().current().getVersion().getVersion());

        current.migrate();
        assertEquals(latestVersion, current.info().current().getVersion());
        assertDoesNotThrow(current::validate);

        // A later deployment of the same migrations must leave the schema history unchanged.
        assertEquals(0, current.migrate().migrationsExecuted);
        assertEquals(latestVersion, current.info().current().getVersion());

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Integer originalChecksum = jdbcTemplate.queryForObject(
                "select checksum from flyway_schema_history where version = '7'", Integer.class);
        assertNotNull(originalChecksum);
        jdbcTemplate.update("update flyway_schema_history set checksum = checksum + 1 where version = '7'");
        assertThrows(FlywayException.class, current::validate);
        current.repair();
        assertEquals(originalChecksum, jdbcTemplate.queryForObject(
                "select checksum from flyway_schema_history where version = '7'", Integer.class));
        assertDoesNotThrow(current::validate);
    }
}
