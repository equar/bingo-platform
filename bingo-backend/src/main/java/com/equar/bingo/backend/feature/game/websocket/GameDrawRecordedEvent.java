package com.equar.bingo.backend.feature.game.websocket;

import com.equar.bingo.backend.feature.game.dto.DrawEventResponse;

public record GameDrawRecordedEvent(DrawEventResponse draw) {
}