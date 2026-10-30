package com.bakaroo.bingo.backend.feature.support.dto;

import com.bakaroo.bingo.backend.feature.support.entity.SupportTicketCategory;
import com.bakaroo.bingo.backend.feature.support.entity.SupportTicketPriority;
import com.bakaroo.bingo.backend.feature.support.entity.SupportTicketStatus;
import java.time.Instant;

public record SupportTicketSummaryResponse(
        Long id,
        String ticketNumber,
        Long requesterUserId,
        String subject,
        SupportTicketCategory category,
        SupportTicketPriority priority,
        SupportTicketStatus status,
        Long assignedToUserId,
        String linkedEntityType,
        Long linkedEntityId,
        Instant createdAt,
        Instant updatedAt) {
}