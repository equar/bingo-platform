package com.equar.bingo.backend.config;

import java.net.URI;
import java.sql.Connection;
import java.util.Arrays;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Emits a credential-free startup identity and fails closed outside the local test schema. */
@Component
@Profile("android-test & !prod & !qa")
@ConditionalOnProperty(prefix = "app.qa", name = "test-runtime-identity-reporting-enabled", havingValue = "true")
public class TestRuntimeIdentityReporter implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TestRuntimeIdentityReporter.class);
    private static final String EXPECTED_DATABASE = "bingo_qa_test";

    private final DataSource dataSource;
    private final Environment environment;
    private final Flyway flyway;

    public TestRuntimeIdentityReporter(DataSource dataSource, Environment environment, Flyway flyway) {
        this.dataSource = dataSource;
        this.environment = environment;
        this.flyway = flyway;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (Arrays.stream(environment.getActiveProfiles()).noneMatch("android-test"::equals)) {
            throw new IllegalStateException("Test runtime identity reporter requires the android-test profile");
        }

        String commit = requiredEnvironment("BINGO_QA_BUILD_COMMIT");
        String checksum = requiredEnvironment("BINGO_QA_BUILD_SHA256");
        requireDisabled("app.game.draw-scheduler-enabled");
        requireDisabled("app.wallet.signup-reward-test-posting-enabled");
        requireDisabled("app.wallet.deposit-simulation-enabled");
        requireDisabled("app.wallet.adjustment-simulation-enabled");
        requireDisabled("app.agent.float-simulation-enabled");
        requireDisabled("app.qa.synthetic-financial-tests-enabled");

        try (Connection connection = dataSource.getConnection()) {
            String jdbcUrl = connection.getMetaData().getURL();
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            String database = connection.getCatalog();
            if (!"127.0.0.1".equals(uri.getHost()) || uri.getPort() != 3306
                    || !EXPECTED_DATABASE.equals(uri.getPath().replaceFirst("^/", ""))
                    || !EXPECTED_DATABASE.equals(database)) {
                throw new IllegalStateException("Test backend datasource must be local bingo_qa_test on port 3306");
            }

            var currentMigration = flyway.info().current();
            String flywayVersion = currentMigration == null ? "none" : currentMigration.getVersion().toString();
            log.info("QA_TEST_RUNTIME_IDENTITY pid={} profiles={} database={} jdbcHost={} jdbcPort={} flyway={} commit={} artifactSha256={}",
                    ProcessHandle.current().pid(), String.join(",", environment.getActiveProfiles()), database,
                    uri.getHost(), uri.getPort(), flywayVersion, commit, checksum);
            log.info("QA_TEST_RUNTIME_GUARDS drawScheduler=disabled signupRewardPosting=disabled depositSimulation=disabled adjustmentSimulation=disabled agentFloatSimulation=disabled");
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
