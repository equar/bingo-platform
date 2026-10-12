package com.equar.bingo.backend.feature.agent.dto;

import com.equar.bingo.backend.feature.auth.dto.UserProfileResponse;

public record AgentPlayerLookupResponse(UserProfileResponse player) {
}