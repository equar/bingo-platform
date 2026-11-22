package com.bakaroo.bingo.backend.common.money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyValuesTest {
    @Test
    void requestAmountPrecisionMustFitWithoutRounding() {
        assertEquals(new BigDecimal("1.23"), MoneyValues.requireMoneyPrecision(new BigDecimal("1.23")));
        assertThrows(IllegalArgumentException.class,
                () -> MoneyValues.requireMoneyPrecision(new BigDecimal("1.239")));
        assertThrows(IllegalArgumentException.class,
                () -> MoneyValues.requireMoneyPrecision(new BigDecimal("100000000000000000.00")));
    }

    @Test
    void derivedAmountsKeepExplicitHalfUpRoundingBehavior() {
        assertEquals(new BigDecimal("1.24"), MoneyValues.money(new BigDecimal("1.235")));
    }
}
