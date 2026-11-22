package com.bakaroo.bingo.backend.feature.auth.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import java.time.Instant;

class ProductionAdminBootstrapRunnerTest {
    @Test
    void disabledBootstrapDoesNotRequireCredentialsOrCreateAccounts() throws Exception {
        ProductionAdminBootstrapService service = mock(ProductionAdminBootstrapService.class);
        ProductionAdminBootstrapRunner runner = new ProductionAdminBootstrapRunner(
                service, false, "", "", "", "", false, "", "");

        runner.run(null);

        verifyNoInteractions(service);
    }

    @Test
    void enabledBootstrapPassesConfiguredIdentityToService() throws Exception {
        ProductionAdminBootstrapService service = mock(ProductionAdminBootstrapService.class);
        org.mockito.Mockito.when(service.bootstrap("+251911123456", "Admin", "admin@example.com",
                "long-test-bootstrap-password"))
                .thenReturn(ProductionAdminBootstrapService.Result.CREATED);
        ProductionAdminBootstrapRunner runner = new ProductionAdminBootstrapRunner(service, true,
                "+251911123456", "Admin", "admin@example.com", "long-test-bootstrap-password", true,
                Instant.now().plusSeconds(600).toString(), "12345");

        runner.run(mock(ApplicationArguments.class));

        verify(service).bootstrap("+251911123456", "Admin", "admin@example.com", "long-test-bootstrap-password");
    }

    @Test
    void enabledBootstrapSkipsWhenApprovalIsExpired() throws Exception {
        ProductionAdminBootstrapService service = mock(ProductionAdminBootstrapService.class);
        var runner = new ProductionAdminBootstrapRunner(service, true,
                "+251911123456", "Admin", "admin@example.com", "long-test-bootstrap-password", true,
                Instant.now().minusSeconds(1).toString(), "12345");
        runner.run(mock(ApplicationArguments.class));
        verifyNoInteractions(service);
    }
}
