package com.equar.bingo.backend.feature.wallet.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.wallet")
public record WalletProperties(
        @NotBlank String currency,
        @NotNull BigDecimal depositConversionRate,
        @NotBlank String paymentProviderName,
        String businessAccount,
        @NotBlank String paymentInstructions) {
}