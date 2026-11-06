package com.bakaroo.bingo.backend.feature.auth.security;

import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class DeploymentAdminBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DeploymentAdminBootstrapRunner.class);
    private final DeploymentAdminBootstrapService service;
    private final boolean enabled;
    private final String emails;

    public DeploymentAdminBootstrapRunner(DeploymentAdminBootstrapService service,
            @Value("${ADMIN_EMAIL_BOOTSTRAP_ENABLED:false}") boolean enabled,
            @Value("${ADMIN_BOOTSTRAP_EMAILS:}") String emails) {
        this.service = service;
        this.enabled = enabled;
        this.emails = emails;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        if (emails.isBlank()) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_EMAILS is required when ADMIN_EMAIL_BOOTSTRAP_ENABLED=true.");
        }
        // Validate the complete list before creating any accounts.
        List<String> identities = Arrays.stream(emails.split(",", -1))
                .map(DeploymentAdminBootstrapService::normalizeEmail).distinct().toList();
        int created = 0;
        for (String email : identities) {
            if (service.bootstrap(email)) created++;
        }
        log.info("Deployment admin bootstrap completed: {} created, {} existing identities skipped.",
                created, identities.size() - created);
    }
}
