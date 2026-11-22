package com.bakaroo.bingo.backend.feature.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransferLimitPeriod;

public record WalletTransferSettingsRequest(
        boolean enabled,
        @NotNull @DecimalMin("0.01") @DecimalMax("99999999999999999.99") @Digits(integer = 17, fraction = 2) BigDecimal maxSingleTransferAmount,
        @NotNull @DecimalMin("0.01") @DecimalMax("99999999999999999.99") @Digits(integer = 17, fraction = 2) BigDecimal periodOutgoingLimitAmount,
        @NotNull WalletTransferLimitPeriod limitPeriod,
        @NotBlank @Size(max = 3) String currency,
        @NotNull Instant effectiveFrom,
        Instant effectiveUntil,
        @Size(max = 500) String reason) {
}
