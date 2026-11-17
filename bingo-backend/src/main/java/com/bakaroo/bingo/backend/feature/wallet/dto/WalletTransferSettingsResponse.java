package com.bakaroo.bingo.backend.feature.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransferLimitPeriod;

public record WalletTransferSettingsResponse(
        Long id,
        boolean enabled,
        BigDecimal maxSingleTransferAmount,
        BigDecimal periodOutgoingLimitAmount,
        WalletTransferLimitPeriod limitPeriod,
        String currency,
        Instant effectiveFrom,
        Instant effectiveUntil,
        Long changedByUserId,
        String reason,
        Instant createdAt,
        Instant updatedAt,
        boolean activeNow) {
}
