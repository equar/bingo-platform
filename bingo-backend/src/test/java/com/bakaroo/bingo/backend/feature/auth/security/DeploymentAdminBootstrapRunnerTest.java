package com.bakaroo.bingo.backend.feature.auth.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import java.time.Instant;

class DeploymentAdminBootstrapRunnerTest {
    private final DeploymentAdminBootstrapService service = mock(DeploymentAdminBootstrapService.class);

    @Test
    void disabledDoesNothing() {
        new DeploymentAdminBootstrapRunner(service, false, "", false, "", "").run(null);
        verifyNoInteractions(service);
    }

    @Test
    void createsBothConfiguredAccountsAndDeduplicatesEmails() {
        new DeploymentAdminBootstrapRunner(service, true,
                "apps@bakaroo.com,equarhishe@gmail.com, APPS@bakaroo.com ", true,
                Instant.now().plusSeconds(600).toString(), "12345").run(null);
        verify(service).bootstrap("apps@bakaroo.com");
        verify(service).bootstrap("equarhishe@gmail.com");
        verifyNoMoreInteractions(service);
    }

    @Test
    void validatesEntireListBeforeWriting() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeploymentAdminBootstrapRunner(service, true, "apps@bakaroo.com,broken", true,
                        Instant.now().plusSeconds(600).toString(), "12345").run(null));
        verifyNoInteractions(service);
    }

    @Test
    void enabledRequiresEmails() {
        assertThrows(IllegalStateException.class,
                () -> new DeploymentAdminBootstrapRunner(service, true, "", true,
                        Instant.now().plusSeconds(600).toString(), "12345").run(null));
    }

    @Test
    void expiredOrMissingApprovalPreventsEmailBootstrap() {
        new DeploymentAdminBootstrapRunner(service, true, "admin@example.com", false,
                Instant.now().plusSeconds(600).toString(), "12345").run(null);
        new DeploymentAdminBootstrapRunner(service, true, "admin@example.com", true,
                Instant.now().minusSeconds(1).toString(), "12345").run(null);
        new DeploymentAdminBootstrapRunner(service, true, "admin@example.com", true,
                Instant.now().plusSeconds(600).toString(), "").run(null);
        verifyNoInteractions(service);
    }
}
