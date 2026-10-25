package com.equar.bingo.backend;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import com.equar.bingo.backend.config.MavenTestDatabasePolicy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Shared database guard for integration tests that create, mutate, or delete data. */
public abstract class MySqlIntegrationTest {
    public static final String DATABASE = MavenTestDatabasePolicy.DATABASE;
    private static final TestDatabase TEST_DATABASE = requireIsolatedTestDatabase();

    @DynamicPropertySource
    public static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_DATABASE.url());
        registry.add("spring.datasource.username", () -> TEST_DATABASE.username());
        registry.add("spring.datasource.password", () -> TEST_DATABASE.password());
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.url", () -> TEST_DATABASE.url());
        registry.add("spring.flyway.user", () -> TEST_DATABASE.username());
        registry.add("spring.flyway.password", () -> TEST_DATABASE.password());
    }

    public static TestDatabase requireIsolatedTestDatabase() {
        String url = required("BINGO_MAVEN_MYSQL_URL");
        String username = required("BINGO_MAVEN_MYSQL_USERNAME");
        String password = required("BINGO_MAVEN_MYSQL_PASSWORD");
        MavenTestDatabasePolicy.validate(url, username);

        TestDatabase database = new TestDatabase(url, username, password);
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            if (!DATABASE.equals(connection.getCatalog())) {
                throw new IllegalStateException("Refusing integration tests: connected MySQL catalog is not " + DATABASE);
            }
            assertCurrentAccountHost(
                    connection,
                    username,
                    "true".equalsIgnoreCase(System.getenv("BINGO_MAVEN_CI_DISPOSABLE")));
            assertNoPersistentSchemaAccess(connection, "bingo");
            assertNoPersistentSchemaAccess(connection, "bingo_qa");
            assertNoPersistentSchemaAccess(connection, "bingo_qa_test");
            assertOnlyDisposableSchemaGrants(connection);
            try (var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT @@hostname, @@port")) {
                if (!result.next() || result.getInt(2) != 3306) {
                    throw new IllegalStateException("Refusing integration tests: MySQL server identity check failed");
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Cannot verify isolated MySQL test database and restricted credentials", exception);
        }
        return database;
    }

    private static void assertCurrentAccountHost(
            Connection connection, String expectedUsername, boolean allowDisposableCiWildcard) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT CURRENT_USER()")) {
            if (!result.next()) {
                throw new IllegalStateException("Refusing integration tests: MySQL did not report CURRENT_USER()");
            }
            String currentAccount = result.getString(1);
            String prefix = expectedUsername + "@";
            if (currentAccount == null || !currentAccount.startsWith(prefix)) {
                throw new IllegalStateException(
                        "Refusing integration tests: authenticated MySQL account does not match the Maven runner");
            }
            String host = currentAccount.substring(prefix.length());
            boolean localHost = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
            if (!localHost && !(allowDisposableCiWildcard && "%".equals(host))) {
                throw new IllegalStateException(
                        "Refusing integration tests: Maven account host must be local; wildcard is CI-only");
            }
        }
    }

    private static void assertOnlyDisposableSchemaGrants(Connection connection) throws SQLException {
        boolean hasDisposableSchemaGrant = false;
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SHOW GRANTS FOR CURRENT_USER()")) {
            while (result.next()) {
                String grant = result.getString(1).replace("`", "").toUpperCase(Locale.ROOT);
                if (!grant.startsWith("GRANT USAGE ON *.*")
                        && !grant.contains(" ON " + DATABASE.toUpperCase(Locale.ROOT) + ".* ")) {
                    throw new IllegalStateException(
                            "Refusing integration tests: Maven account has a grant outside " + DATABASE);
                }
                hasDisposableSchemaGrant |= grant.contains(" ON " + DATABASE.toUpperCase(Locale.ROOT) + ".* ");
            }
        }
        if (!hasDisposableSchemaGrant) {
            throw new IllegalStateException(
                    "Refusing integration tests: Maven account has no explicit grant on " + DATABASE);
        }
    }

    private static void assertNoPersistentSchemaAccess(Connection connection, String schema) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeQuery("SELECT 1 FROM `" + schema + "`.flyway_schema_history LIMIT 0").close();
            throw new IllegalStateException("Refusing integration tests: test credentials can access " + schema);
        } catch (SQLException exception) {
            // An authorization denial is required; missing table or other failures are not proof of isolation.
            if (exception.getErrorCode() != 1044 && exception.getErrorCode() != 1142) {
                throw exception;
            }
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required; integration tests never use Testcontainers implicitly");
        }
        return value;
    }

    public record TestDatabase(String url, String username, String password) { }
}
