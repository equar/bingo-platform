package com.equar.bingo.backend.feature.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.equar.bingo.backend.common.exception.ConflictException;
import com.equar.bingo.backend.feature.wallet.service.LocalFinancialSimulationGuard;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

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
}
