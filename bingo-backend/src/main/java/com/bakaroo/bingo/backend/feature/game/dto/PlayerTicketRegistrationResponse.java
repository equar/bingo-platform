package com.bakaroo.bingo.backend.feature.game.dto;

import java.util.List;

public record PlayerTicketRegistrationResponse(boolean initialized, List<Long> ticketIds) {
}
