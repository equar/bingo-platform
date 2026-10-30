package com.bakaroo.bingo.backend.feature.agent.dto;

import com.bakaroo.bingo.backend.feature.auth.dto.UserProfileResponse;

public record AgentPlayerLookupResponse(UserProfileResponse player) {
}