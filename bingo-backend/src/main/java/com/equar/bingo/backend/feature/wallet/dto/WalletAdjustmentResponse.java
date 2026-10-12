package com.equar.bingo.backend.feature.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletAdjustmentResponse(
        String operationId,
        Long walletId,
        Long playerId,
        BigDecimal amount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        String reason,
        Instant createdAt) {
}