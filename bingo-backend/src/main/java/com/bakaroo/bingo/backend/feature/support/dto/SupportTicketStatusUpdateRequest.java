package com.bakaroo.bingo.backend.feature.support.dto;

import com.bakaroo.bingo.backend.feature.support.entity.SupportTicketStatus;
import jakarta.validation.constraints.NotNull;

public record SupportTicketStatusUpdateRequest(
        @NotNull SupportTicketStatus status,
        String resolutionSummary) {
}