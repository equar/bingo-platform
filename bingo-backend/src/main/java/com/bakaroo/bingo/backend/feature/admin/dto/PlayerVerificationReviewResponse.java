package com.bakaroo.bingo.backend.feature.admin.dto;

import com.bakaroo.bingo.backend.feature.admin.entity.PlayerVerificationReview;
import com.bakaroo.bingo.backend.feature.user.entity.PlayerVerificationReason;
import com.bakaroo.bingo.backend.feature.user.entity.PlayerVerificationStatus;
import java.time.Instant;

public record PlayerVerificationReviewResponse(
        Long id,
        Long playerId,
        Long reviewerUserId,
        PlayerVerificationStatus status,
        PlayerVerificationReason reason,
        Instant reviewedAt) {

    public static PlayerVerificationReviewResponse from(PlayerVerificationReview review) {
        return new PlayerVerificationReviewResponse(
                review.getId(),
                review.getPlayer().getId(),
                review.getReviewer().getId(),
                review.getStatus(),
                review.getReason(),
                review.getReviewedAt());
    }
}
