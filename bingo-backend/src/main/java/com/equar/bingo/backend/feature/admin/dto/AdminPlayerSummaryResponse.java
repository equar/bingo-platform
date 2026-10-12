package com.equar.bingo.backend.feature.admin.dto;

import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import java.time.Instant;
import java.util.Set;

public record AdminPlayerSummaryResponse(
        Long id,
        String displayName,
        String normalizedPhone,
        Set<UserRole> roles,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt,
        long walletTransactionCount,
        long depositClaimCount,
        long supportTicketCount) {
}