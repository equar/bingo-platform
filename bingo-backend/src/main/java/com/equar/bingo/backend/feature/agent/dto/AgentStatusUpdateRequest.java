package com.equar.bingo.backend.feature.agent.dto;

import com.equar.bingo.backend.feature.agent.entity.AgentStatus;
import jakarta.validation.constraints.NotNull;

public record AgentStatusUpdateRequest(@NotNull AgentStatus status) {
}