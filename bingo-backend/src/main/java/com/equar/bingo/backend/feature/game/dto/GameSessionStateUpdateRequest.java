package com.equar.bingo.backend.feature.game.dto;

import com.equar.bingo.backend.feature.game.entity.GameSessionState;
import jakarta.validation.constraints.NotNull;

public record GameSessionStateUpdateRequest(@NotNull GameSessionState state) {
}