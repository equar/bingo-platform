package com.bakaroo.bingo.backend.feature.game.websocket;

import com.bakaroo.bingo.backend.feature.game.dto.DrawEventResponse;

public record GameDrawRecordedEvent(DrawEventResponse draw) {
}