package com.bakaroo.bingo.backend.feature.wallet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bakaroo.bingo.backend.feature.wallet.config.WalletProperties;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class WalletPropertiesTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void depositConversionRateMustRemainOneToOneUntilAccountingSupportsConversion() {
        assertTrue(validator.validate(properties("1.000000")).isEmpty());
        assertFalse(validator.validate(properties("1.25")).isEmpty());
    }

    private WalletProperties properties(String rate) {
        return new WalletProperties("ETB", new BigDecimal(rate), "Telebirr", "account", "instructions");
    }
}
