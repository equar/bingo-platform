package com.equar.bingo.backend.feature.support.dto;

import com.equar.bingo.backend.feature.support.entity.SupportLinkedEntityType;
import com.equar.bingo.backend.feature.support.entity.SupportTicketCategory;
import com.equar.bingo.backend.feature.support.entity.SupportTicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SupportTicketCreateRequest(
        @NotBlank String subject,
        @NotBlank String description,
        @NotNull SupportTicketCategory category,
        @NotNull SupportTicketPriority priority,
        SupportLinkedEntityType linkedEntityType,
        Long linkedEntityId,
        String linkedEntityReference) {
}