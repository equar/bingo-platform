package com.bakaroo.bingo.backend.feature.wallet.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.AssertTrue;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.wallet")
public record WalletProperties(
        @NotBlank String currency,
        @NotNull @DecimalMin("0.000001") @DecimalMax("9999999999999.999999")
        @Digits(integer = 13, fraction = 6) BigDecimal depositConversionRate,
        @NotBlank String paymentProviderName,
        String businessAccount,
        @NotBlank String paymentInstructions,
        @NotNull @DecimalMin("0.01") @DecimalMax("99999999999999999.99") @Digits(integer = 17, fraction = 2)
        BigDecimal depositMaximumAmount) {

    public WalletProperties(String currency, BigDecimal depositConversionRate, String paymentProviderName,
            String businessAccount, String paymentInstructions) {
        this(currency, depositConversionRate, paymentProviderName, businessAccount, paymentInstructions,
                new BigDecimal("99999999999999999.99"));
    }

    @AssertTrue(message = "Deposit conversion is supported only at a 1:1 cash-to-credit rate")
    public boolean isDepositConversionRateSupported() {
        return depositConversionRate != null && BigDecimal.ONE.compareTo(depositConversionRate) == 0;
    }
}
