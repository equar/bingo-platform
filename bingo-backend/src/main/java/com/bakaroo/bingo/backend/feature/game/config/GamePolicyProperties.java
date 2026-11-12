package com.bakaroo.bingo.backend.feature.game.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.game.policy")
public record GamePolicyProperties(
        @DefaultValue("1000") @Min(1) @Max(9999) int minimumHouseMarginBps,
        @DefaultValue("0.00") @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal sessionCostReserve,
        @DefaultValue("PT24H") @NotNull Duration claimWindow,
        @DefaultValue("false") boolean payoutsEnabled) {
    public GamePolicyProperties {
        if (claimWindow != null && (claimWindow.toSeconds() < 1)) {
            throw new IllegalArgumentException("Claim window must be positive");
        }
    }
}
