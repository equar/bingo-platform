package com.equar.bingo.backend.feature.auth.dto;

import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import java.time.Instant;
import java.util.Set;

public record UserSummaryResponse(
        Long id,
        String displayName,
        String phoneNumber,
        Set<UserRole> roles,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt) {
}