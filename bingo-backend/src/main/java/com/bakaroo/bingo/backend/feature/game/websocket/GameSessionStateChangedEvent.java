package com.bakaroo.bingo.backend.feature.game.websocket;

import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;

public record GameSessionStateChangedEvent(Long sessionId, GameSessionState state) {
}
