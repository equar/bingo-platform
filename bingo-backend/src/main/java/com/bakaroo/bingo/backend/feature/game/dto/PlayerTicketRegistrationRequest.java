package com.bakaroo.bingo.backend.feature.game.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PlayerTicketRegistrationRequest(
        @NotNull @Size(max = 4) List<@NotNull @Positive Long> ticketIds) {
}
