package com.bakaroo.bingo.backend.common.validation;

import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import java.time.Instant;

public final class ReportRangeValidator {

    private static final long MAX_RANGE_SECONDS = 366L * 24 * 60 * 60;

    private ReportRangeValidator() {
    }

    public static void validate(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new BadRequestException("INVALID_REPORT_RANGE", "Report end must be after report start");
        }
        if (to.isAfter(from.plusSeconds(MAX_RANGE_SECONDS))) {
            throw new BadRequestException("REPORT_RANGE_TOO_LARGE", "Reports are limited to 366 days");
        }
    }
}
