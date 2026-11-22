package com.bakaroo.bingo.backend.feature.auth.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.time.Instant;

/** Runs after Spring has initialized the datasource and completed Flyway migrations. */
@Component
@Profile("prod")
public class ProductionAdminBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ProductionAdminBootstrapRunner.class);

    private final ProductionAdminBootstrapService bootstrapService;
    private final boolean enabled;
    private final String phone;
    private final String name;
    private final String email;
    private final String password;
    private final boolean approved;
    private final String approvalExpiresAt;
    private final String approvalRunId;

    public ProductionAdminBootstrapRunner(ProductionAdminBootstrapService bootstrapService,
            @Value("${app.admin.bootstrap.enabled:false}") boolean enabled,
            @Value("${ADMIN_BOOTSTRAP_PHONE:}") String phone,
            @Value("${ADMIN_BOOTSTRAP_NAME:}") String name,
            @Value("${ADMIN_BOOTSTRAP_EMAIL:}") String email,
            @Value("${ADMIN_BOOTSTRAP_PASSWORD:}") String password,
            @Value("${ADMIN_BOOTSTRAP_APPROVED:false}") boolean approved,
            @Value("${ADMIN_BOOTSTRAP_APPROVAL_EXPIRES_AT:}") String approvalExpiresAt,
            @Value("${ADMIN_BOOTSTRAP_APPROVAL_RUN_ID:}") String approvalRunId) {
        this.bootstrapService = bootstrapService;
        this.enabled = enabled;
        this.phone = phone;
        this.name = name;
        this.email = email;
        this.password = password;
        this.approved = approved;
        this.approvalExpiresAt = approvalExpiresAt;
        this.approvalRunId = approvalRunId;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        if (!approved || approvalRunId.isBlank()
                || !AdminBootstrapApproval.isActive(approvalExpiresAt, Instant.now())) {
            log.warn("Production Admin bootstrap skipped: a current, auditable production bootstrap approval is required.");
            return;
        }
        log.warn("Production initial Admin bootstrap authorized by GitHub Actions run {} until {}.", approvalRunId,
                approvalExpiresAt);

        ProductionAdminBootstrapService.Result result = bootstrapService.bootstrap(phone, name, email, password);
        if (result == ProductionAdminBootstrapService.Result.CREATED) {
            log.warn("Production initial admin account created. Log in, change its password, then disable bootstrap and remove ADMIN_BOOTSTRAP_PASSWORD.");
        } else {
            log.warn("Production admin bootstrap skipped because the requested phone or email already exists; no account fields or roles were changed. Verify the existing account or use the supported account recovery process.");
        }
    }
}
