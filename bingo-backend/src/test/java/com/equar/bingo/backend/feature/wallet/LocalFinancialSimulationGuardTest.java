package com.equar.bingo.backend.feature.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.equar.bingo.backend.common.exception.ConflictException;
import com.equar.bingo.backend.feature.wallet.service.LocalFinancialSimulationGuard;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

class LocalFinancialSimulationGuardTest {
    private final DataSource dataSource = mock(DataSource.class);

    @Test
    void refusesEnabledSimulationInProductionProfile() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        var guard = new LocalFinancialSimulationGuard(dataSource, environment);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> guard.validateConfiguration(true, "Player deposit"));

        org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains("local QA/test profiles"));
        verifyNoInteractions(dataSource);
    }

    @Test
    void disabledPostingFailsBeforeInspectingDatabase() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        var guard = new LocalFinancialSimulationGuard(dataSource, environment);

        ConflictException failure = assertThrows(ConflictException.class,
                () -> guard.requireEnabled(false, "Wallet adjustment"));

        assertEquals("FINANCIAL_SIMULATION_DISABLED", failure.getCode());
        verifyNoInteractions(dataSource);
    }

    @Test
    void playerTicketPostingIsDisabledOutsideTheTestProfile() {
        for (String profile : new String[] { "qa", "prod" }) {
            var environment = new MockEnvironment();
            environment.setActiveProfiles(profile);
            var guard = new LocalFinancialSimulationGuard(dataSource, environment);

            ConflictException failure = assertThrows(ConflictException.class,
                    () -> guard.requireIsolatedTestDatabase("Player ticket purchase"));

            assertEquals("TICKET_PURCHASE_POSTING_DISABLED", failure.getCode());
        }
        verifyNoInteractions(dataSource);
    }

    @Test
    void isolatedTestPostingRequiresTheLocalTestSchema() throws Exception {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:mysql://127.0.0.1:3306/bingo_qa_test");
        var guard = new LocalFinancialSimulationGuard(dataSource, environment);

        assertDoesNotThrow(() -> guard.requireIsolatedTestDatabase("Player ticket purchase"));
    }

    @Test
    void isolatedTestPostingRejectsPersistentQaSchema() throws Exception {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:mysql://127.0.0.1:3306/bingo_qa");
        var guard = new LocalFinancialSimulationGuard(dataSource, environment);

        ConflictException failure = assertThrows(ConflictException.class,
                () -> guard.requireIsolatedTestDatabase("Player ticket purchase"));

        assertEquals("TICKET_PURCHASE_TEST_DATABASE_REQUIRED", failure.getCode());
    }
}
