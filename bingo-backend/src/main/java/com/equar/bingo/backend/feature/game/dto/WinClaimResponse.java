package com.equar.bingo.backend.feature.game.dto;

import com.equar.bingo.backend.feature.game.entity.BingoPatternType;
import com.equar.bingo.backend.feature.game.entity.WinClaimStatus;
import java.time.Instant;
import java.util.List;

public record WinClaimResponse(
        Long id,
        Long ticketId,
        Long sessionId,
        Long claimantId,
        BingoPatternType claimedPattern,
        WinClaimStatus status,
        boolean serverValidationPassed,
        String serverValidationCode,
        String serverValidationMessage,
        int drawSequenceSnapshot,
        List<Integer> calledNumbersSnapshot,
        String evidenceJson,
        String operationId,
        String reviewNote,
        Long reviewedBy,
        Instant reviewedAt,
        Long entitlementId,
        Instant createdAt,
        Instant updatedAt) {
}