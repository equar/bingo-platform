package com.bakaroo.bingo.backend.feature.game;

import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSessionStateTest {

    @Test
    void allowsOnlyValidForwardTransitions() {
        assertTrue(GameSessionState.DRAFT.canTransitionTo(GameSessionState.OPEN));
        assertTrue(GameSessionState.OPEN.canTransitionTo(GameSessionState.STARTED));
        assertTrue(GameSessionState.STARTED.canTransitionTo(GameSessionState.DRAWING));
        assertTrue(GameSessionState.CLOSED.canTransitionTo(GameSessionState.SETTLED));
        assertTrue(GameSessionState.DRAFT.canTransitionTo(GameSessionState.CANCELLED));
        assertTrue(GameSessionState.OPEN.canTransitionTo(GameSessionState.CANCELLED));
    }

    @Test
    void rejectsBackwardsAndTerminalTransitions() {
        assertFalse(GameSessionState.SETTLED.canTransitionTo(GameSessionState.OPEN));
        assertFalse(GameSessionState.CANCELLED.canTransitionTo(GameSessionState.DRAFT));
        assertFalse(GameSessionState.OPEN.canTransitionTo(GameSessionState.DRAFT));
        assertFalse(GameSessionState.STARTED.canTransitionTo(GameSessionState.CANCELLED));
        assertFalse(GameSessionState.DRAWING.canTransitionTo(GameSessionState.CANCELLED));
        assertFalse(GameSessionState.CLOSED.canTransitionTo(GameSessionState.CANCELLED));
    }
}
