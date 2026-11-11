package com.bakaroo.bingo.backend.feature.auth.dto;

import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.entity.ActivationEmailStatus;
import com.bakaroo.bingo.backend.feature.user.entity.PasswordResetEmailStatus;
import java.time.Instant;
import java.util.Set;

public record UserSummaryResponse(
        Long id,
        String displayName,
        String email,
        String phoneNumber,
        Set<UserRole> roles,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt,
        String createdByDisplayName,
        String updatedByDisplayName,
        ActivationEmailStatus activationEmailStatus,
        Instant activationEmailSentAt,
        Instant activationEmailStatusUpdatedAt,
        PasswordResetEmailStatus passwordResetEmailStatus,
        Instant passwordResetEmailStatusUpdatedAt) {
}
