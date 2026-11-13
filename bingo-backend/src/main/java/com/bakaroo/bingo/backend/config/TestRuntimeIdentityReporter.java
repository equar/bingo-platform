package com.bakaroo.bingo.backend.config;

import java.net.URI;
import java.sql.Connection;
import java.util.Arrays;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Emits a credential-free startup identity and fails closed outside the local test schema. */
@Component
@Profile("android-test & !prod & !qa")
@ConditionalOnProperty(prefix = "app.qa", name = "test-runtime-identity-reporting-enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TestRuntimeIdentityReporter implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TestRuntimeIdentityReporter.class);
    private final DataSource dataSource;
    private final Environment environment;
    private final Flyway flyway;
    private final String expectedDatabase;

    public TestRuntimeIdentityReporter(DataSource dataSource, Environment environment, Flyway flyway,
            @Value("${app.qa.test-runtime-expected-database:bingo_qa_test}") String expectedDatabase) {
        this.dataSource = dataSource;
        this.environment = environment;
        this.flyway = flyway;
        this.expectedDatabase = expectedDatabase;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (Arrays.stream(environment.getActiveProfiles()).noneMatch("android-test"::equals)) {
            throw new IllegalStateException("Test runtime identity reporter requires the android-test profile");
        }

        String commit = requiredEnvironment("BINGO_QA_BUILD_COMMIT");
        String checksum = requiredEnvironment("BINGO_QA_BUILD_SHA256");
        String sourceChecksum = requiredEnvironment("BINGO_QA_BUILD_SOURCE_SHA256");
        if (!"bingo_qa_test".equals(expectedDatabase)
                && !expectedDatabase.matches("bingo_qa_v22_v40_[0-9a-f]{32}")) {
            throw new IllegalStateException("Test runtime expected database is not an approved isolated schema name");
        }
        String adminOrigin = environment.getProperty("CORS_ALLOWED_ORIGINS", "");
        if (!sourceChecksum.matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalStateException("Test runtime source fingerprint is invalid");
        }
        if (!"http://127.0.0.1:5174".equals(adminOrigin)) {
            throw new IllegalStateException("Test backend CORS must allow only the isolated local Admin origin");
        }
        requireDisabled("app.game.draw-scheduler-enabled");
        requireDisabled("app.wallet.deposit-simulation-enabled");
        requireDisabled("app.wallet.adjustment-simulation-enabled");
        requireDisabled("app.agent.float-simulation-enabled");
        requireDisabled("app.qa.synthetic-financial-tests-enabled");

        try (Connection connection = dataSource.getConnection()) {
            String jdbcUrl = connection.getMetaData().getURL();
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            String database = connection.getCatalog();
            if (!"127.0.0.1".equals(uri.getHost()) || uri.getPort() != 3306
                    || !expectedDatabase.equals(uri.getPath().replaceFirst("^/", ""))
                    || !expectedDatabase.equals(database)) {
                throw new IllegalStateException("Test backend datasource does not match the approved local schema on port 3306");
            }

            var currentMigration = flyway.info().current();
            String flywayVersion = currentMigration == null ? "none" : currentMigration.getVersion().toString();
            log.info("QA_TEST_RUNTIME_IDENTITY pid={} profiles={} database={} jdbcHost={} jdbcPort={} flyway={} commit={} artifactSha256={} sourceSha256={}",
                    ProcessHandle.current().pid(), String.join(",", environment.getActiveProfiles()), database,
                    uri.getHost(), uri.getPort(), flywayVersion, commit, checksum, sourceChecksum);
            log.info("QA_TEST_RUNTIME_GUARDS drawScheduler=disabled signupRewardPosting=disabled depositSimulation=disabled adjustmentSimulation=disabled agentFloatSimulation=disabled adminCorsOrigin={}",
                    adminOrigin);
        }
    }

    private void requireDisabled(String property) {
        if (environment.getProperty(property, Boolean.class, true)) {
            throw new IllegalStateException("Test runtime safety property must be disabled: " + property);
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing runtime identity value: " + name);
        }
        return value;
    }
}
