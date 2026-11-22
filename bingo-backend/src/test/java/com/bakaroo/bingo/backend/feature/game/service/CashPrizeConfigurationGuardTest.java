package com.bakaroo.bingo.backend.feature.game.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import com.bakaroo.bingo.backend.feature.game.entity.PrizeSettlementPath;
import org.junit.jupiter.api.Test;

class CashPrizeConfigurationGuardTest {
    @Test
    void rejectsAgentCashWithStableClientError() {
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> CashPrizeConfigurationGuard.requireAvailable(PrizeSettlementPath.AGENT_CASH));

        assertEquals("CASH_PRIZE_PAYOUT_UNAVAILABLE", error.getCode());
    }

    @Test
    void permitsWalletSettlement() {
        CashPrizeConfigurationGuard.requireAvailable(PrizeSettlementPath.PLAYER_WALLET);
    }
}
