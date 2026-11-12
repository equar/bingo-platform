package com.bakaroo.bingo.backend.feature.game.service;

import com.bakaroo.bingo.backend.common.exception.ConflictException;
import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;

/** Validates server-authoritative session state before a new claim is created. */
public class WinClaimSessionValidator {

    public void validate(GameSessionState state) {
        if (state == null) {
            throw new ConflictException("SESSION_STATE_INVALID", "The game session has an invalid state");
        }
        if (state == GameSessionState.CANCELLED) {
            throw new ConflictException("SESSION_CANCELLED", "Claims are not accepted for cancelled sessions");
        }
        if (state != GameSessionState.STARTED && state != GameSessionState.DRAWING
                && state != GameSessionState.CLOSED) {
            throw new ConflictException("SESSION_NOT_CLAIMABLE", "Claims require a started game that has not been settled");
        }
    }
}
