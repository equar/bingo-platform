package com.equar.bingo.backend.feature.wallet.service;

import com.equar.bingo.backend.common.exception.ConflictException;
import java.net.URI;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class LocalFinancialSimulationGuard {
    private final DataSource dataSource;
    private final Environment environment;

    public LocalFinancialSimulationGuard(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    public void validateConfiguration(boolean enabled, String feature) {
        if (enabled && (environment.acceptsProfiles(Profiles.of("prod"))
                || !environment.acceptsProfiles(Profiles.of("qa", "test")))) {
            throw new IllegalStateException(feature + " simulation may only be configured in local QA/test profiles");
        }
    }

    public void requireEnabled(boolean enabled, String feature) {
        if (!enabled) {
            throw new ConflictException("FINANCIAL_SIMULATION_DISABLED",
                    feature + " simulation is disabled until the required financial policies are approved");
        }
        validateConfiguration(enabled, feature);
        try (var connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            URI uri = URI.create(url.substring("jdbc:".length()));
            String schema = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
            boolean localHost = "127.0.0.1".equals(uri.getHost()) || "localhost".equalsIgnoreCase(uri.getHost());
            if (!localHost || uri.getPort() != 3306
                    || !("bingo_qa".equals(schema) || "bingo_qa_test".equals(schema))) {
                throw new ConflictException("FINANCIAL_SIMULATION_DATABASE_NOT_ALLOWED",
                        "Financial simulation is restricted to local bingo_qa or bingo_qa_test");
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to verify the local financial simulation database", exception);
        }
    }
}
