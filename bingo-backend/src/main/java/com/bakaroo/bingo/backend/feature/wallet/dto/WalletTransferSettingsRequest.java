package com.bakaroo.bingo.backend.feature.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransferLimitPeriod;

public record WalletTransferSettingsRequest(
        boolean enabled,
        @NotNull BigDecimal maxSingleTransferAmount,
        @NotNull BigDecimal periodOutgoingLimitAmount,
        @NotNull WalletTransferLimitPeriod limitPeriod,
        @NotBlank @Size(max = 3) String currency,
        @NotNull Instant effectiveFrom,
        Instant effectiveUntil,
        @Size(max = 500) String reason) {
}
