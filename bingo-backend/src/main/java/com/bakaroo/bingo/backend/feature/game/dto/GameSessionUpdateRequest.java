package com.bakaroo.bingo.backend.feature.game.dto;

import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;
import com.bakaroo.bingo.backend.feature.game.entity.BingoPatternType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record GameSessionUpdateRequest(
        @NotBlank @Size(max = 120) String sessionName,
        @NotNull @Future Instant scheduledStartAt,
        @NotNull GameSessionState state,
        List<@NotNull BingoPatternType> configuredPatterns,
        List<@NotNull @Valid BingoPrizeConfigRequest> configuredPrizes) {
}
