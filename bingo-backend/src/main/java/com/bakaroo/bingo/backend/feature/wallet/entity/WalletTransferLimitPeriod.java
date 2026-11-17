package com.bakaroo.bingo.backend.feature.wallet.entity;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

public enum WalletTransferLimitPeriod {
    DAILY,
    WEEKLY,
    BIWEEKLY,
    MONTHLY;

    public Instant startOfPeriod(Instant now, ZoneId zone) {
        LocalDate today = now.atZone(zone).toLocalDate();
        LocalDate start = switch (this) {
            case DAILY -> today;
            case WEEKLY -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case BIWEEKLY -> {
                LocalDate anchor = LocalDate.of(1970, 1, 5);
                long daysSinceAnchor = ChronoUnit.DAYS.between(anchor, today);
                yield anchor.plusDays(Math.floorDiv(daysSinceAnchor, 14) * 14);
            }
            case MONTHLY -> today.withDayOfMonth(1);
        };
        return start.atStartOfDay(zone).toInstant();
    }
}
