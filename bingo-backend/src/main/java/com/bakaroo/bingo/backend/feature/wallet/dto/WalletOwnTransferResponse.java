package com.bakaroo.bingo.backend.feature.wallet.dto;

import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransferStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record WalletOwnTransferResponse(
        Long id,
        String operationId,
        String recipientPhoneNumber,
        BigDecimal amount,
        String currency,
        WalletTransferStatus status,
        Instant createdAt,
        Instant completedAt) {
}
