package com.equar.bingo.backend.feature.wallet.service;

import com.equar.bingo.backend.common.exception.ConflictException;
import java.net.URI;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Restricts positive signup reward posting to isolated MySQL integration tests. */
@Component
public class SignupRewardPostingGuard {
    private final DataSource dataSource;
    private final Environment environment;

    public SignupRewardPostingGuard(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    public void validateConfiguration(boolean enabled) {
        if (enabled && !environment.acceptsProfiles(Profiles.of("test"))) {
            throw new IllegalStateException("Signup reward test posting is restricted to the test profile");
        }
    }

    public void requireIsolatedTestPosting(boolean enabled) {
        if (!enabled) {
            throw new ConflictException("SIGNUP_REWARD_POSTING_NOT_APPROVED",
                    "Positive signup rewards are disabled until Product and Finance approve their posting policy");
        }
        if (!environment.acceptsProfiles(Profiles.of("test"))) {
            throw new ConflictException("SIGNUP_REWARD_TEST_PROFILE_REQUIRED",
                    "Signup reward test posting is restricted to isolated integration tests");
        }

        try (var connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            URI uri = URI.create(url.substring("jdbc:".length()));
            String schema = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
            boolean localHost = "127.0.0.1".equals(uri.getHost()) || "localhost".equalsIgnoreCase(uri.getHost());
            if (!localHost || uri.getPort() != 3306 || !"bingo_qa_test".equals(schema)) {
                throw new ConflictException("SIGNUP_REWARD_TEST_DATABASE_NOT_ALLOWED",
                        "Signup reward test posting requires local MySQL schema bingo_qa_test");
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to verify isolated signup reward test database", exception);
        }
    }
}
