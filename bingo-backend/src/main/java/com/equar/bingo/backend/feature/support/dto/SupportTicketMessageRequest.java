package com.equar.bingo.backend.feature.support.dto;

import jakarta.validation.constraints.NotBlank;

public record SupportTicketMessageRequest(@NotBlank String body) {
}