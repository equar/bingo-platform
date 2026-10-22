package com.equar.bingo.backend.common.idempotency;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
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
    void upgradesFromV6AndRepairsDetectedChecksumDrift() throws SQLException {
        String externalUrl = System.getenv("BINGO_TEST_MYSQL_URL");
        if (externalUrl == null || externalUrl.isBlank()) {
            try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")) {
                mysql.start();
                verifyUpgradeAndRepair(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            }
            return;
        }

        String username = requiredExternalProperty("BINGO_TEST_MYSQL_USERNAME");
        String password = requiredExternalProperty("BINGO_TEST_MYSQL_PASSWORD");
        String isolatedDatabase = "bingo_flyway_" + UUID.randomUUID().toString().replace("-", "");
        executeDatabaseStatement(
                externalUrl,
                username,
                password,
                "create database `" + isolatedDatabase + "`");
        try {
            verifyUpgradeAndRepair(databaseUrl(externalUrl, isolatedDatabase), username, password);
        } finally {
            executeDatabaseStatement(
                    externalUrl,
                    username,
                    password,
                    "drop database `" + isolatedDatabase + "`");
        }
    }

    private static void verifyUpgradeAndRepair(String url, String username, String password) {
        DataSource dataSource = new DriverManagerDataSource(url, username, password);
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
        assertEquals("14", current.info().current().getVersion().getVersion());
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

    private static void executeDatabaseStatement(
            String jdbcUrl,
            String username,
            String password,
            String sql) throws SQLException {
        int queryIndex = jdbcUrl.indexOf('?');
        String serverUrl = queryIndex < 0 ? jdbcUrl : jdbcUrl.substring(0, queryIndex);
        int databaseSeparator = serverUrl.lastIndexOf('/');
        serverUrl = serverUrl.substring(0, databaseSeparator + 1);
        if (queryIndex >= 0) {
            serverUrl += jdbcUrl.substring(queryIndex);
        }
        try (Connection connection = DriverManager.getConnection(serverUrl, username, password);
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String databaseUrl(String jdbcUrl, String databaseName) {
        int queryIndex = jdbcUrl.indexOf('?');
        String serverUrl = queryIndex < 0 ? jdbcUrl : jdbcUrl.substring(0, queryIndex);
        int databaseSeparator = serverUrl.lastIndexOf('/');
        String result = serverUrl.substring(0, databaseSeparator + 1) + databaseName;
        if (queryIndex >= 0) {
            result += jdbcUrl.substring(queryIndex);
        }
        return result;
    }

    private static String requiredExternalProperty(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when BINGO_TEST_MYSQL_URL is set");
        }
        return value;
    }
}
