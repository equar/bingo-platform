package com.bakaroo.bingo.backend.feature.admin.dto;

import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public record AdminPlayerDetailResponse(
        Long id,
        String displayName,
        String normalizedPhone,
        String email,
        Set<UserRole> roles,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt,
        String walletCurrency,
        BigDecimal walletAvailableBalance,
        BigDecimal walletPromotionalBalance,
        long walletTransactionCount,
        long depositClaimCount,
        long supportTicketCount,
        long ticketCount,
        long winClaimCount) {
}