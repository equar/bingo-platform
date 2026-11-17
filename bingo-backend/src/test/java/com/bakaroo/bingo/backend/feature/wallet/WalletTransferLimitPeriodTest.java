package com.bakaroo.bingo.backend.feature.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransferLimitPeriod;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class WalletTransferLimitPeriodTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Africa/Addis_Ababa");

    @Test
    void resetsDailyAtBusinessMidnight() {
        Instant now = Instant.parse("2026-11-17T09:00:00Z");
        assertEquals(Instant.parse("2026-11-16T21:00:00Z"),
                WalletTransferLimitPeriod.DAILY.startOfPeriod(now, BUSINESS_ZONE));
    }

    @Test
    void resetsWeeklyOnMondayAtBusinessMidnight() {
        Instant now = Instant.parse("2026-11-17T09:00:00Z");
        assertEquals(Instant.parse("2026-11-15T21:00:00Z"),
                WalletTransferLimitPeriod.WEEKLY.startOfPeriod(now, BUSINESS_ZONE));
    }

    @Test
    void resetsBiweeklyAtTheStartOfAnAnchoredTwoWeekWindow() {
        Instant now = Instant.parse("2026-11-17T09:00:00Z");
        assertEquals(Instant.parse("2026-11-08T21:00:00Z"),
                WalletTransferLimitPeriod.BIWEEKLY.startOfPeriod(now, BUSINESS_ZONE));
    }

    @Test
    void resetsMonthlyOnTheFirstAtBusinessMidnight() {
        Instant now = Instant.parse("2026-11-17T09:00:00Z");
        assertEquals(Instant.parse("2026-10-31T21:00:00Z"),
                WalletTransferLimitPeriod.MONTHLY.startOfPeriod(now, BUSINESS_ZONE));
    }
}
