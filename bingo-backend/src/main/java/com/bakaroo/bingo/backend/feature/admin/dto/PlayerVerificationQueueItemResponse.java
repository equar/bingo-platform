package com.bakaroo.bingo.backend.feature.admin.dto;

import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.PlayerVerificationReason;
import com.bakaroo.bingo.backend.feature.user.entity.PlayerVerificationStatus;
import java.time.Instant;

public record PlayerVerificationQueueItemResponse(
        Long playerId,
        String displayName,
        String phoneNumber,
        AccountStatus accountStatus,
        PlayerVerificationStatus verificationStatus,
        PlayerVerificationReason verificationReason,
        Long reviewedByUserId,
        Instant reviewedAt,
        Instant createdAt) {
}
