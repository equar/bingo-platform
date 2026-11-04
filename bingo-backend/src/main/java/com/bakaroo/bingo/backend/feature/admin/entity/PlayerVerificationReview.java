package com.bakaroo.bingo.backend.feature.admin.entity;

import com.bakaroo.bingo.backend.feature.user.entity.PlayerVerificationReason;
import com.bakaroo.bingo.backend.feature.user.entity.PlayerVerificationStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "player_verification_reviews")
public class PlayerVerificationReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private User player;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_user_id", nullable = false)
    private User reviewer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PlayerVerificationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    private PlayerVerificationReason reason;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    protected PlayerVerificationReview() {
    }

    public static PlayerVerificationReview create(
            User player,
            User reviewer,
            PlayerVerificationStatus status,
            PlayerVerificationReason reason,
            Instant reviewedAt) {
        PlayerVerificationReview review = new PlayerVerificationReview();
        review.player = player;
        review.reviewer = reviewer;
        review.status = status;
        review.reason = reason;
        review.reviewedAt = reviewedAt;
        return review;
    }

    public Long getId() {
        return id;
    }

    public User getPlayer() {
        return player;
    }

    public User getReviewer() {
        return reviewer;
    }

    public PlayerVerificationStatus getStatus() {
        return status;
    }

    public PlayerVerificationReason getReason() {
        return reason;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
