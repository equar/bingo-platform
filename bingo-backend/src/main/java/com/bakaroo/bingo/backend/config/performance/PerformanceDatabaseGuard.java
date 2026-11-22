package com.bakaroo.bingo.backend.config.performance;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Verifies the live datasource before the performance-test profile serves traffic. */
@Component
@Profile("performance-test")
public class PerformanceDatabaseGuard implements SmartInitializingSingleton {

    private final DataSource dataSource;
    private final String expectedCatalog;
    private final int expectedPort;
    private final String expectedServerUuid;
    private final int approvedMaximumConnections;
    private volatile TargetIdentity identity;

    public PerformanceDatabaseGuard(
            DataSource dataSource,
            @Value("${app.performance.expected-catalog:}") String expectedCatalog,
            @Value("${app.performance.expected-mysql-port:3306}") int expectedPort,
            @Value("${app.performance.expected-mysql-server-uuid:}") String expectedServerUuid,
            @Value("${app.performance.approved-maximum-db-connections:0}") int approvedMaximumConnections) {
        this.dataSource = dataSource;
        this.expectedCatalog = expectedCatalog;
        this.expectedPort = expectedPort;
        this.expectedServerUuid = expectedServerUuid;
        this.approvedMaximumConnections = approvedMaximumConnections;
    }

    /** Runs before ContextRefreshedEvent starts scheduled work and before the web server accepts requests. */
    @Override
    public void afterSingletonsInstantiated() {
        validateExpectedCatalog(expectedCatalog);
        try (Connection connection = dataSource.getConnection()) {
            String catalog = connection.getCatalog();
            String user;
            int port;
            String serverUuid;
            try (Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("SELECT @@port, CURRENT_USER(), @@server_uuid")) {
                if (!result.next()) {
                    throw new IllegalStateException("Performance database identity query returned no row");
                }
                port = result.getInt(1);
                user = result.getString(2);
                serverUuid = result.getString(3);
            }
            List<String> grants = readGrants(connection);
            if (!(dataSource instanceof HikariDataSource hikariDataSource)) {
                throw new IllegalStateException("Performance-test profile requires measurable Hikari connection pooling");
            }
            int poolMaximum = hikariDataSource.getMaximumPoolSize();
            validateRuntimeIdentity(expectedCatalog, catalog, expectedPort, port,
                    expectedServerUuid, serverUuid, user, poolMaximum, approvedMaximumConnections, grants);
            identity = new TargetIdentity(catalog, user, port, serverUuid, poolMaximum, approvedMaximumConnections);
        } catch (SQLException exception) {
            throw new IllegalStateException("Refusing performance-test profile: unable to verify isolated MySQL identity", exception);
        }
    }

    public TargetIdentity requireVerifiedIdentity() {
        TargetIdentity verified = identity;
        if (verified == null) {
            throw new IllegalStateException("Performance database identity has not been verified");
        }
        return verified;
    }

    static void validateExpectedCatalog(String catalog) {
        if (catalog == null || !catalog.matches("bingo_perf_[a-z0-9_]{1,48}")) {
            throw new IllegalStateException("Performance schema must be a dedicated bingo_perf_<run> catalog");
        }
    }

    static void validateRuntimeIdentity(
            String expectedCatalog,
            String actualCatalog,
            int expectedPort,
            int actualPort,
            String expectedServerUuid,
            String actualServerUuid,
            String currentUser,
            int poolMaximum,
            int approvedMaximumConnections,
            List<String> grants) {
        validateExpectedCatalog(expectedCatalog);
        if (!expectedCatalog.equals(actualCatalog)) {
            throw new IllegalStateException("Connected MySQL catalog does not match the approved performance catalog");
        }
        if (expectedPort != actualPort) {
            throw new IllegalStateException("Connected MySQL port does not match the approved performance port");
        }
        if (expectedServerUuid == null || !expectedServerUuid.matches("[0-9a-fA-F-]{36}")
                || !expectedServerUuid.equalsIgnoreCase(actualServerUuid)) {
            throw new IllegalStateException("Connected MySQL server UUID does not match the approved disposable server");
        }
        if (currentUser == null || !currentUser.toLowerCase(Locale.ROOT).startsWith("bingo_perf_runner@")) {
            throw new IllegalStateException("Performance database account must be bingo_perf_runner");
        }
        if (approvedMaximumConnections < 1 || poolMaximum < 1 || poolMaximum > approvedMaximumConnections) {
            throw new IllegalStateException("Hikari pool maximum exceeds the approved database connection ceiling");
        }
        validateGrants(expectedCatalog, grants);
    }

    private static List<String> readGrants(Connection connection) throws SQLException {
        List<String> grants = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SHOW GRANTS FOR CURRENT_USER()")) {
            while (result.next()) {
                grants.add(result.getString(1));
            }
        }
        return grants;
    }

    private static void validateGrants(String expectedCatalog, List<String> grants) {
        if (grants == null || grants.isEmpty()) {
            throw new IllegalStateException("Performance database account has no verifiable grants");
        }
        String schemaGrant = " ON " + expectedCatalog.toUpperCase(Locale.ROOT) + ".* ";
        boolean hasSchemaGrant = false;
        for (String rawGrant : grants) {
            String grant = rawGrant == null ? "" : rawGrant.replace("`", "").toUpperCase(Locale.ROOT);
            if (grant.startsWith("GRANT USAGE ON *.* ")) {
                continue;
            }
            if (!grant.contains(schemaGrant) || grant.contains("WITH GRANT OPTION")) {
                throw new IllegalStateException("Performance database account has a grant outside its isolated catalog");
            }
            hasSchemaGrant = true;
        }
        if (!hasSchemaGrant) {
            throw new IllegalStateException("Performance database account has no explicit grant on its isolated catalog");
        }
    }

    public record TargetIdentity(
            String schemaName,
            String databaseAccount,
            int mysqlPort,
            String mysqlServerUuid,
            int hikariPoolMaximum,
            int approvedMaximumConnections) { }
}
