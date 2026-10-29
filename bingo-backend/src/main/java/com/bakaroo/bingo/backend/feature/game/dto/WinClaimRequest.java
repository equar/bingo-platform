package com.bakaroo.bingo.backend.feature.game.dto;

import com.bakaroo.bingo.backend.feature.game.entity.BingoPatternType;
import jakarta.validation.constraints.NotNull;

public record WinClaimRequest(@NotNull BingoPatternType claimedPattern) {
}