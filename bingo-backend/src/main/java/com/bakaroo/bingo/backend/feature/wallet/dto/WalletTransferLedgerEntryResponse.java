package com.bakaroo.bingo.backend.feature.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletTransferLedgerEntryResponse(
        Long id,
        Long walletId,
        Long walletOwnerId,
        String operationId,
        String transactionType,
        BigDecimal amount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        String currency,
        String referenceType,
        Long referenceId,
        Instant createdAt) {
}
