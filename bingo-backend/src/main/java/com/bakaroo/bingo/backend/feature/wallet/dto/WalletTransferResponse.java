package com.bakaroo.bingo.backend.feature.wallet.dto;

import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransferStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record WalletTransferResponse(
        Long id,
        String operationId,
        Long senderId,
        String senderPhoneNumber,
        Long recipientId,
        String recipientPhoneNumber,
        BigDecimal amount,
        String currency,
        String note,
        WalletTransferStatus status,
        BigDecimal senderBalanceBefore,
        BigDecimal senderBalanceAfter,
        BigDecimal recipientBalanceBefore,
        BigDecimal recipientBalanceAfter,
        Instant createdAt,
        Instant completedAt,
        String failureReason) {
}