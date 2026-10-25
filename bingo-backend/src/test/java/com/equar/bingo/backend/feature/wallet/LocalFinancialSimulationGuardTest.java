package com.equar.bingo.backend.feature.wallet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
    void disabledPostingFailsBeforeInspectingDatabase() {
        var environment = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        var guard = new LocalFinancialSimulationGuard(dataSource, environment);

        ConflictException failure = assertThrows(ConflictException.class,
                () -> guard.requireEnabled(false, "Wallet adjustment"));

        assertEquals("FINANCIAL_SIMULATION_DISABLED", failure.getCode());
        verifyNoInteractions(dataSource);
    }

    @Test
    void refusesEnabledSimulationInProductionWithoutDatabaseAccess() {
        var environment = testEnvironment("jdbc:mysql://127.0.0.1:3306/bingo_qa_maven_test", true);
        environment.setActiveProfiles("prod");
        var guard = new LocalFinancialSimulationGuard(dataSource, environment);

        assertThrows(IllegalStateException.class, () -> guard.validateConfiguration(true, "Player deposit"));

        verifyNoInteractions(dataSource);
    }

    @Test
    void refusesFinancialOptInWhenExplicitMasterSwitchIsOff() {
        var environment = testEnvironment("jdbc:mysql://127.0.0.1:3306/bingo_qa_maven_test", false);
        var guard = new LocalFinancialSimulationGuard(dataSource, environment);

        assertThrows(IllegalStateException.class, () -> guard.validateConfiguration(true, "Player deposit"));

        verifyNoInteractions(dataSource);
    }

    @Test
    void refusesFinancialOptInAgainstPersistentQaBeforeOpeningConnection() {
        var environment = testEnvironment("jdbc:mysql://127.0.0.1:3306/bingo_qa", true);
        var guard = new LocalFinancialSimulationGuard(dataSource, environment);

        assertThrows(IllegalStateException.class, () -> guard.validateConfiguration(true, "Wallet adjustment"));

        verifyNoInteractions(dataSource);
    }

    @Test
    void refusesFinancialOptInAgainstAndroidQaBeforeOpeningConnection() {
        var environment = testEnvironment("jdbc:mysql://127.0.0.1:3306/bingo_qa_test", true);
        var guard = new LocalFinancialSimulationGuard(dataSource, environment);

        assertThrows(IllegalStateException.class, () -> guard.validateConfiguration(true, "Agent float"));

        verifyNoInteractions(dataSource);
    }

    @Test
    void acceptsOnlyExplicitTestOptInOnDedicatedSchemaAsConfigurationOnlyCheck() {
        var environment = testEnvironment("jdbc:mysql://localhost:3306/bingo_qa_maven_test", true);
        var guard = new LocalFinancialSimulationGuard(dataSource, environment);

        assertDoesNotThrow(() -> guard.validateConfiguration(true, "Synthetic test posting"));

        verifyNoInteractions(dataSource);
    }

    @Test
    void refusesTicketPurchasePostingWithoutOptInBeforeOpeningConnection() {
        var environment = testEnvironment("jdbc:mysql://127.0.0.1:3306/bingo_qa_maven_test", false);
        var guard = new LocalFinancialSimulationGuard(dataSource, environment);

        ConflictException failure = assertThrows(ConflictException.class,
                () -> guard.requireIsolatedTestDatabase("Ticket purchase"));

        assertEquals("TICKET_PURCHASE_POSTING_DISABLED", failure.getCode());
        verifyNoInteractions(dataSource);
    }

    private MockEnvironment testEnvironment(String jdbcUrl, boolean optIn) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        environment.setProperty("app.qa.synthetic-financial-tests-enabled", Boolean.toString(optIn));
        environment.setProperty("spring.datasource.url", jdbcUrl);
        environment.setProperty("spring.datasource.username", "bingo_qa_maven_runner");
        return environment;
    }
}
