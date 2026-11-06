package com.bakaroo.bingo.backend.feature.auth.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

class DeploymentAdminBootstrapRunnerTest {
    private final DeploymentAdminBootstrapService service = mock(DeploymentAdminBootstrapService.class);

    @Test
    void disabledDoesNothing() {
        new DeploymentAdminBootstrapRunner(service, false, "").run(null);
        verifyNoInteractions(service);
    }

    @Test
    void createsBothConfiguredAccountsAndDeduplicatesEmails() {
        new DeploymentAdminBootstrapRunner(service, true,
                "apps@bakaroo.com,equarhishe@gmail.com, APPS@bakaroo.com ").run(null);
        verify(service).bootstrap("apps@bakaroo.com");
        verify(service).bootstrap("equarhishe@gmail.com");
        verifyNoMoreInteractions(service);
    }

    @Test
    void validatesEntireListBeforeWriting() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeploymentAdminBootstrapRunner(service, true, "apps@bakaroo.com,broken").run(null));
        verifyNoInteractions(service);
    }

    @Test
    void enabledRequiresEmails() {
        assertThrows(IllegalStateException.class,
                () -> new DeploymentAdminBootstrapRunner(service, true, "").run(null));
    }
}
