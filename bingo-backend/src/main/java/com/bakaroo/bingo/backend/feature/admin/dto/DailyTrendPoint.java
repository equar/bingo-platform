package com.bakaroo.bingo.backend.feature.admin.dto;

import java.time.LocalDate;

public record DailyTrendPoint(LocalDate date, long count) {
}
