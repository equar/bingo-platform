package com.equar.bingo.backend.feature.support.dto;

import com.equar.bingo.backend.feature.support.entity.SupportTicketCategory;
import com.equar.bingo.backend.feature.support.entity.SupportTicketPriority;
import com.equar.bingo.backend.feature.support.entity.SupportTicketStatus;
import java.time.Instant;

public record SupportTicketResponse(
        Long id,
        String ticketNumber,
        Long requesterUserId,
        String requesterDisplayName,
        String subject,
        String description,
        SupportTicketCategory category,
        SupportTicketPriority priority,
        SupportTicketStatus status,
        String linkedEntityType,
        Long linkedEntityId,
        String linkedEntityReference,
        Long assignedToUserId,
        String resolutionSummary,
        Instant createdAt,
        Instant updatedAt,
        java.util.List<SupportMessageResponse> messages,
        java.util.List<InternalNoteResponse> internalNotes) {
}