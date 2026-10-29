package com.bakaroo.bingo.backend.feature.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletAdjustmentRequestResponse(
        Long id,
        Long playerId,
        Long makerUserId,
        BigDecimal amount,
        String currency,
        String reason,
        String supportingReference,
        String status,
        String operationId,
        Long checkerUserId,
        String reviewNote,
        Instant submittedAt,
        Instant reviewedAt,
        Long journalId,
        String postingReference,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        boolean simulated) {
}
