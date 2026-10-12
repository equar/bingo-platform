package com.equar.bingo.backend.feature.support.dto;

import jakarta.validation.constraints.NotNull;

public record SupportTicketAssignmentRequest(@NotNull Long assignedToUserId) {
}