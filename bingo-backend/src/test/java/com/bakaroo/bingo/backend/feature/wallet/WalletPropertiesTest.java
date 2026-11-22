package com.bakaroo.bingo.backend.feature.wallet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bakaroo.bingo.backend.feature.wallet.config.WalletProperties;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.junit.jupiter.api.Test;

class WalletPropertiesTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void depositConversionRateMustRemainOneToOneUntilAccountingSupportsConversion() {
        assertTrue(validator.validate(properties("1.000000")).isEmpty());
        assertFalse(validator.validate(properties("1.25")).isEmpty());
    }

    @Test
    void overloadedRecordConstructorStillBindsAllWalletSettings() {
        var source = new MapConfigurationPropertySource(Map.of(
                "app.wallet.currency", "ETB",
                "app.wallet.deposit-conversion-rate", "1.000000",
                "app.wallet.payment-provider-name", "Telebirr",
                "app.wallet.payment-instructions", "Manual verification",
                "app.wallet.deposit-maximum-amount", "250000.00"));

        WalletProperties bound = new Binder(source)
                .bind("app.wallet", Bindable.of(WalletProperties.class))
                .get();

        assertEquals("ETB", bound.currency());
        assertEquals("Telebirr", bound.paymentProviderName());
        assertEquals(new BigDecimal("250000.00"), bound.depositMaximumAmount());
    }

    private WalletProperties properties(String rate) {
        return new WalletProperties("ETB", new BigDecimal(rate), "Telebirr", "account", "instructions");
    }
}
