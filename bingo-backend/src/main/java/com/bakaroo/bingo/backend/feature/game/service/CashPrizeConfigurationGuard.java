package com.bakaroo.bingo.backend.feature.game.service;

import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import com.bakaroo.bingo.backend.feature.game.entity.PrizeSettlementPath;

/** Blocks configurations that cannot be settled through an in-scope payout path. */
final class CashPrizeConfigurationGuard {
    private CashPrizeConfigurationGuard() {}

    static void requireAvailable(PrizeSettlementPath settlementPath) {
        if (settlementPath == PrizeSettlementPath.AGENT_CASH) {
            throw new BadRequestException("CASH_PRIZE_PAYOUT_UNAVAILABLE",
                    "Cash prizes cannot be configured for play until an approved payout mechanism is available");
        }
    }
}
