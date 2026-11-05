package com.bakaroo.bingo.backend.feature.admin.dto;

import java.util.List;

public record AdminDashboardTrendsResponse(
        String businessTimezone,
        List<DailyTrendPoint> playerRegistrations,
        List<DailyTrendPoint> supportTicketsCreated) {
}
