package com.bakaroo.bingo.backend.feature.support.dto;

import com.bakaroo.bingo.backend.feature.support.entity.SupportLinkedEntityType;
import com.bakaroo.bingo.backend.feature.support.entity.SupportTicketCategory;
import com.bakaroo.bingo.backend.feature.support.entity.SupportTicketPriority;
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