package com.bakaroo.bingo.backend.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Monetary values use precision 19, scale 2 for balances and ledger amounts.
 * Conversion rates use precision 19, scale 6 so deposit credits can be derived
 * without using floating-point arithmetic.
 */
public final class MoneyValues {

    public static final int MONEY_SCALE = 2;
    public static final int MONEY_PRECISION = 19;
    public static final int CONVERSION_RATE_SCALE = 6;

    private MoneyValues() {
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Validates an externally supplied amount before any rounding or persistence. */
    public static BigDecimal requireMoneyPrecision(BigDecimal value) {
        if (value == null || value.scale() > MONEY_SCALE
                || value.precision() - value.scale() > MONEY_PRECISION - MONEY_SCALE) {
            throw new IllegalArgumentException("Amount must fit DECIMAL(19,2) without rounding");
        }
        return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal conversionRate(BigDecimal value) {
        return value.setScale(CONVERSION_RATE_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal creditAmount(BigDecimal cashAmount, BigDecimal conversionRate) {
        return money(cashAmount.multiply(conversionRate));
    }
}
