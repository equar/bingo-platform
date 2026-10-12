package com.equar.bingo.backend.feature.game.entity;

import java.util.EnumSet;
import java.util.Set;

public enum GameSessionState {
    DRAFT,
    OPEN,
    STARTED,
    DRAWING,
    CLOSED,
    SETTLED,
    CANCELLED;

    public boolean canTransitionTo(GameSessionState nextState) {
        return allowedTransitions().contains(nextState);
    }

    public Set<GameSessionState> allowedTransitions() {
        return switch (this) {
            case DRAFT -> EnumSet.of(OPEN, CANCELLED);
            case OPEN -> EnumSet.of(STARTED, CANCELLED);
            case STARTED -> EnumSet.of(DRAWING, CLOSED, CANCELLED);
            case DRAWING -> EnumSet.of(CLOSED, CANCELLED);
            case CLOSED -> EnumSet.of(SETTLED, CANCELLED);
            case SETTLED, CANCELLED -> EnumSet.noneOf(GameSessionState.class);
        };
    }
}