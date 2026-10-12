package com.equar.bingo.backend.common.money;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyValuesTest {

    @Test
    void roundsMoneyValuesHalfUp() {
        assertEquals(new BigDecimal("1.01"), MoneyValues.money(new BigDecimal("1.005")));
        assertEquals(new BigDecimal("1.00"), MoneyValues.money(new BigDecimal("1.004")));
        assertEquals(new BigDecimal("0.00"), MoneyValues.money(new BigDecimal("0.004")));
    }

    @Test
    void computesCreditAmountsWithMoneyScale() {
        assertEquals(new BigDecimal("12.35"),
                MoneyValues.creditAmount(new BigDecimal("10.00"), new BigDecimal("1.2345")));
    }
}