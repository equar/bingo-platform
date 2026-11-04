package com.bakaroo.bingo.backend.feature.admin.service;

import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import com.bakaroo.bingo.backend.common.exception.ConflictException;
import com.bakaroo.bingo.backend.common.exception.NotFoundException;
import com.bakaroo.bingo.backend.feature.admin.dto.PlayerVerificationQueueItemResponse;
import com.bakaroo.bingo.backend.feature.admin.dto.PlayerVerificationReviewResponse;
import com.bakaroo.bingo.backend.feature.admin.dto.PlayerVerificationUpdateRequest;
import com.bakaroo.bingo.backend.feature.admin.entity.PlayerVerificationReview;
import com.bakaroo.bingo.backend.feature.admin.repository.PlayerVerificationReviewRepository;
import com.bakaroo.bingo.backend.feature.user.entity.PlayerVerificationReason;
import com.bakaroo.bingo.backend.feature.user.entity.PlayerVerificationStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminPlayerVerificationService {

    private final UserRepository userRepository;
    private final PlayerVerificationReviewRepository reviewRepository;

    public AdminPlayerVerificationService(
            UserRepository userRepository,
            PlayerVerificationReviewRepository reviewRepository) {
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public Page<PlayerVerificationQueueItemResponse> queue(
            PlayerVerificationStatus status,
            String search,
            Pageable pageable) {
        String normalizedSearch = search == null || search.isBlank()
                ? null
                : search.trim().toLowerCase(Locale.ROOT);
        return userRepository.findPlayerVerificationQueue(
                        UserRole.PLAYER,
                        status,
                        normalizedSearch,
                        pageable)
                .map(this::queueItem);
    }

    @Transactional(readOnly = true)
    public List<PlayerVerificationReviewResponse> history(Long playerId) {
        requirePlayer(playerId);
        return reviewRepository.findByPlayer_IdOrderByReviewedAtDescIdDesc(playerId)
                .stream()
                .map(PlayerVerificationReviewResponse::from)
                .toList();
    }

    @Transactional
    public PlayerVerificationReviewResponse recordPlayerVerification(
            Long playerId,
            Long reviewerUserId,
            PlayerVerificationUpdateRequest request) {
        validateReason(request.status(), request.reason());
        User player = userRepository.findByIdForUpdate(playerId)
                .filter(user -> user.getRoles().equals(Set.of(UserRole.PLAYER)))
                .orElseThrow(() -> new NotFoundException("PLAYER_NOT_FOUND", "Player account not found"));
        User reviewer = userRepository.findById(reviewerUserId)
                .filter(user -> user.isActive() && user.hasRole(UserRole.ADMIN))
                .orElseThrow(() -> new NotFoundException("ADMIN_REVIEWER_NOT_FOUND", "Admin reviewer not found"));

        PlayerVerificationStatus currentStatus = player.getPlayerVerificationStatus();
        if (!allowedNextStatuses(currentStatus).contains(request.status())) {
            throw new ConflictException(
                    "INVALID_VERIFICATION_TRANSITION",
                    "This verification review step is no longer available. Refresh the queue and try again.");
        }

        Instant reviewedAt = Instant.now();
        player.setPlayerVerificationStatus(request.status());
        player.setPlayerVerificationReason(request.reason());
        player.setPlayerVerificationReviewedBy(reviewer);
        player.setPlayerVerificationReviewedAt(reviewedAt);
        userRepository.save(player);

        PlayerVerificationReview review = reviewRepository.save(PlayerVerificationReview.create(
                player,
                reviewer,
                request.status(),
                request.reason(),
                reviewedAt));
        return PlayerVerificationReviewResponse.from(review);
    }

    private User requirePlayer(Long playerId) {
        return userRepository.findById(playerId)
                .filter(user -> user.getRoles().equals(Set.of(UserRole.PLAYER)))
                .orElseThrow(() -> new NotFoundException("PLAYER_NOT_FOUND", "Player account not found"));
    }

    private PlayerVerificationQueueItemResponse queueItem(User player) {
        return new PlayerVerificationQueueItemResponse(
                player.getId(),
                player.getDisplayName(),
                player.getNormalizedPhone(),
                player.getStatus(),
                player.getPlayerVerificationStatus(),
                player.getPlayerVerificationReason(),
                player.getPlayerVerificationReviewedBy() == null
                        ? null
                        : player.getPlayerVerificationReviewedBy().getId(),
                player.getPlayerVerificationReviewedAt(),
                player.getCreatedAt());
    }

    private void validateReason(PlayerVerificationStatus status, PlayerVerificationReason reason) {
        boolean valid = switch (status) {
            case NOT_REVIEWED -> false;
            case IN_REVIEW -> reason == PlayerVerificationReason.REVIEW_STARTED;
            case VERIFIED -> reason == PlayerVerificationReason.IDENTITY_AND_AGE_CONFIRMED;
            case NEEDS_INFORMATION -> reason == PlayerVerificationReason.INFORMATION_REQUIRED;
            case REJECTED -> reason == PlayerVerificationReason.IDENTITY_MISMATCH
                    || reason == PlayerVerificationReason.AGE_NOT_VERIFIED
                    || reason == PlayerVerificationReason.OTHER;
        };
        if (!valid) {
            throw new BadRequestException(
                    "INVALID_VERIFICATION_REASON",
                    "The selected reason does not match the verification status");
        }
    }

    private Set<PlayerVerificationStatus> allowedNextStatuses(PlayerVerificationStatus currentStatus) {
        return switch (currentStatus) {
            case NOT_REVIEWED -> Set.of(PlayerVerificationStatus.IN_REVIEW);
            case IN_REVIEW -> Set.of(
                    PlayerVerificationStatus.VERIFIED,
                    PlayerVerificationStatus.NEEDS_INFORMATION,
                    PlayerVerificationStatus.REJECTED);
            case NEEDS_INFORMATION -> Set.of(PlayerVerificationStatus.IN_REVIEW);
            case VERIFIED, REJECTED -> Set.of();
        };
    }
}
