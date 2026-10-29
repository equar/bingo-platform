package com.bakaroo.bingo.backend.feature.wallet.service;

import com.bakaroo.bingo.backend.common.exception.ConflictException;
import com.bakaroo.bingo.backend.config.MavenTestDatabasePolicy;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Fails closed unless synthetic financial tests are explicitly opted into on the disposable Maven schema. */
@Component
public class LocalFinancialSimulationGuard {
    public static final String OPT_IN_PROPERTY = "app.qa.synthetic-financial-tests-enabled";

    private final DataSource dataSource;
    private final Environment environment;

    public LocalFinancialSimulationGuard(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    /** Performs configuration-only checks and never opens a database connection. */
    public void validateConfiguration(boolean enabled, String feature) {
        if (!enabled) {
            return;
        }
        if (!environment.acceptsProfiles(Profiles.of("test"))
                || environment.acceptsProfiles(Profiles.of("qa", "prod", "android-test"))) {
            throw new IllegalStateException(feature + " synthetic posting requires only the isolated test profile");
        }
        if (!environment.getProperty(OPT_IN_PROPERTY, Boolean.class, false)) {
            throw new IllegalStateException(feature
                    + " synthetic posting requires explicit BINGO_MAVEN_SYNTHETIC_FINANCIAL_TESTS_ENABLED opt-in");
        }
        MavenTestDatabasePolicy.validate(
                environment.getProperty("spring.datasource.url"),
                environment.getProperty("spring.datasource.username"));
    }

    public void requireEnabled(boolean enabled, String feature) {
        if (!enabled) {
            throw new ConflictException("FINANCIAL_SIMULATION_DISABLED",
                    feature + " simulation is disabled until explicitly approved for isolated tests");
        }
        validateConfiguration(enabled, feature);
        verifyConnectedDisposableDatabase(feature);
    }

    /** Positive ticket purchase tests are also financial simulations and use the same explicit opt-in. */
    public void requireIsolatedTestDatabase(String feature) {
        if (!environment.acceptsProfiles(Profiles.of("test"))
                || environment.acceptsProfiles(Profiles.of("qa", "prod", "android-test"))) {
            throw new ConflictException("TICKET_PURCHASE_POSTING_DISABLED",
                    feature + " posting is disabled outside the isolated Maven test profile");
        }
        if (!environment.getProperty(OPT_IN_PROPERTY, Boolean.class, false)) {
            throw new ConflictException("TICKET_PURCHASE_POSTING_DISABLED",
                    feature + " posting requires explicit isolated-test opt-in");
        }
        validateConfiguration(true, feature);
        verifyConnectedDisposableDatabase(feature);
    }

    private void verifyConnectedDisposableDatabase(String feature) {
        try (var connection = dataSource.getConnection()) {
            String jdbcUrl = connection.getMetaData().getURL();
            MavenTestDatabasePolicy.validate(jdbcUrl, environment.getProperty("spring.datasource.username"));
            if (!MavenTestDatabasePolicy.DATABASE.equals(connection.getCatalog())) {
                throw new ConflictException("FINANCIAL_SIMULATION_DATABASE_NOT_ALLOWED",
                        feature + " is restricted to the dedicated disposable Maven schema");
            }
            String currentUser;
            try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT CURRENT_USER()")) {
                if (!result.next()) {
                    throw new IllegalStateException("Unable to verify the isolated financial-test database account");
                }
                currentUser = result.getString(1);
            }
            String accountPrefix = MavenTestDatabasePolicy.USERNAME + "@";
            boolean localAccount = currentUser != null && currentUser.startsWith(accountPrefix)
                    && ("localhost".equalsIgnoreCase(currentUser.substring(accountPrefix.length()))
                            || "127.0.0.1".equals(currentUser.substring(accountPrefix.length())));
            boolean disposableCiAccount = currentUser != null && currentUser.equals(accountPrefix + "%")
                    && "true".equalsIgnoreCase(System.getenv("BINGO_MAVEN_CI_DISPOSABLE"));
            if (!localAccount && !disposableCiAccount) {
                throw new ConflictException("FINANCIAL_SIMULATION_ACCOUNT_NOT_ALLOWED",
                        feature + " requires the dedicated local Maven test account");
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to verify the isolated financial-test database", exception);
        }
    }
}
