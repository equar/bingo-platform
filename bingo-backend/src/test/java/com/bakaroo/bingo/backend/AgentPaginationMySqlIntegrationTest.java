package com.bakaroo.bingo.backend;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.net.URI;
import java.util.Locale;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Strict database boundary for the rollback-only Agent roster integration tests. */
@ResourceLock(value = "bingo-agent-pagination-mysql", mode = ResourceAccessMode.READ_WRITE)
public abstract class AgentPaginationMySqlIntegrationTest {
    public static final String DATABASE = "bingo_qa_agent_pagination_test";
    public static final String USERNAME = "bingo_qa_agent_pagination_runner";
    private static final TestDatabase TEST_DATABASE = requireIsolatedTestDatabase();

    @DynamicPropertySource
    public static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TEST_DATABASE::url);
        registry.add("spring.datasource.username", TEST_DATABASE::username);
        registry.add("spring.datasource.password", TEST_DATABASE::password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.url", TEST_DATABASE::url);
        registry.add("spring.flyway.user", TEST_DATABASE::username);
        registry.add("spring.flyway.password", TEST_DATABASE::password);
    }

    private static TestDatabase requireIsolatedTestDatabase() {
        if (!"true".equalsIgnoreCase(required("BINGO_MAVEN_AGENT_PAGINATION_TESTS_ENABLED"))) {
            throw new IllegalStateException(
                    "Set BINGO_MAVEN_AGENT_PAGINATION_TESTS_ENABLED=true only for the approved disposable Agent pagination schema");
        }
        if ("true".equalsIgnoreCase(System.getenv("BINGO_MAVEN_SYNTHETIC_FINANCIAL_TESTS_ENABLED"))) {
            throw new IllegalStateException("Agent pagination tests must not enable synthetic financial tests");
        }

        String url = required("BINGO_MAVEN_AGENT_PAGINATION_MYSQL_URL");
        String username = required("BINGO_MAVEN_AGENT_PAGINATION_MYSQL_USERNAME");
        String password = required("BINGO_MAVEN_AGENT_PAGINATION_MYSQL_PASSWORD");
        if (!USERNAME.equals(username) || !isExactLocalDisposableUrl(url)) {
            throw new IllegalStateException(
                    "Agent pagination tests require the exact local disposable schema, dedicated account, and database creation disabled");
        }

        TestDatabase database = new TestDatabase(url, username, password);
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            if (!DATABASE.equals(connection.getCatalog())) {
                throw new IllegalStateException("Refusing tests: connected MySQL catalog is not " + DATABASE);
            }
            try (var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT CURRENT_USER(), @@hostname, @@port")) {
                if (!result.next() || !result.getString(1).startsWith(USERNAME + "@")
                        || !isLocalAccountHost(result.getString(1).substring(USERNAME.length() + 1))
                        || result.getInt(3) != 3306) {
                    throw new IllegalStateException("Refusing tests: authenticated local MySQL identity check failed");
                }
            }
            assertOnlySchemaGrant(connection);
            assertNoPersistentSchemaAccess(connection, "bingo");
            assertNoPersistentSchemaAccess(connection, "bingo_qa");
            assertNoPersistentSchemaAccess(connection, "bingo_qa_test");
            assertNoPersistentSchemaAccess(connection, "bingo_qa_maven_test");
            assertEmptyOrCurrentFlywaySchema(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot verify the isolated Agent pagination MySQL target", exception);
        }
        return database;
    }

    private static boolean isExactLocalDisposableUrl(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql://")) {
            return false;
        }
        try {
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            if (!("127.0.0.1".equals(uri.getHost()) || "localhost".equalsIgnoreCase(uri.getHost()))
                    || uri.getPort() != 3306 || !("/" + DATABASE).equals(uri.getPath())) {
                return false;
            }
            int createDatabaseParameters = 0;
            for (String parameter : uri.getRawQuery().split("&")) {
                String[] pair = parameter.split("=", 2);
                if ("createdatabaseifnotexist".equalsIgnoreCase(pair[0])) {
                    createDatabaseParameters++;
                    if (pair.length != 2 || Boolean.parseBoolean(pair[1])) {
                        return false;
                    }
                }
            }
            return createDatabaseParameters == 1;
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }
    }

    private static void assertEmptyOrCurrentFlywaySchema(Connection connection) throws SQLException {
        long tableCount;
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = '" + DATABASE + "'")) {
            result.next();
            tableCount = result.getLong(1);
        }
        if (tableCount == 0) {
            return;
        }
        if (tableCount < 1) {
            throw new IllegalStateException("Refusing tests: unable to verify schema state");
        }
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT version, success FROM " + DATABASE
                        + ".flyway_schema_history WHERE version = '28' ORDER BY installed_rank DESC LIMIT 1")) {
            if (!result.next() || !"28".equals(result.getString(1)) || !result.getBoolean(2)) {
                throw new IllegalStateException(
                        "Refusing tests: populated Agent pagination schema must have successful Flyway V28");
            }
        }
    }

    private static boolean isLocalAccountHost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
    }

    private static void assertOnlySchemaGrant(Connection connection) throws SQLException {
        boolean schemaGrantFound = false;
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SHOW GRANTS FOR CURRENT_USER()")) {
            while (result.next()) {
                String grant = result.getString(1).replace("`", "").toUpperCase(Locale.ROOT);
                if (!grant.startsWith("GRANT USAGE ON *.*")
                        && !grant.contains(" ON " + DATABASE.toUpperCase(Locale.ROOT) + ".* ")) {
                    throw new IllegalStateException("Refusing tests: runner has a grant outside " + DATABASE);
                }
                schemaGrantFound |= grant.contains(" ON " + DATABASE.toUpperCase(Locale.ROOT) + ".* ");
            }
        }
        if (!schemaGrantFound) {
            throw new IllegalStateException("Refusing tests: runner lacks an explicit grant on " + DATABASE);
        }
    }

    private static void assertNoPersistentSchemaAccess(Connection connection, String schema) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeQuery("SELECT 1 FROM `" + schema + "`.flyway_schema_history LIMIT 0").close();
            throw new IllegalStateException("Refusing tests: runner can access " + schema);
        } catch (SQLException exception) {
            if (exception.getErrorCode() != 1044 && exception.getErrorCode() != 1142) {
                throw exception;
            }
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required; there is no fallback database for these tests");
        }
        return value;
    }

    private record TestDatabase(String url, String username, String password) { }
}
