package com.equar.bingo.backend.feature.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletTransactionResponse(
        Long id,
        String operationId,
        String transactionType,
        BigDecimal amount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        String currency,
        String referenceType,
        Long referenceId,
        String note,
        Instant createdAt) {
}