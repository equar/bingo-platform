package com.bakaroo.bingo.backend.common.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReportRangeValidatorTest {

    @Test
    void acceptsPositiveRangesUpTo366Days() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");

        assertThatCode(() -> ReportRangeValidator.validate(from, from.plusSeconds(366L * 24 * 60 * 60)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptyOrReversedRanges() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");

        assertThatThrownBy(() -> ReportRangeValidator.validate(from, from))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("after report start");
        assertThatThrownBy(() -> ReportRangeValidator.validate(from, from.minusSeconds(1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("after report start");
    }

    @Test
    void rejectsRangesLongerThan366Days() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");

        assertThatThrownBy(() -> ReportRangeValidator.validate(from, from.plusSeconds(366L * 24 * 60 * 60 + 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("limited to 366 days");
    }
}
