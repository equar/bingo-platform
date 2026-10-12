package com.equar.bingo.backend.feature.game.dto;

import com.equar.bingo.backend.feature.game.entity.BingoPatternType;
import jakarta.validation.constraints.NotNull;

public record WinClaimRequest(@NotNull BingoPatternType claimedPattern) {
}