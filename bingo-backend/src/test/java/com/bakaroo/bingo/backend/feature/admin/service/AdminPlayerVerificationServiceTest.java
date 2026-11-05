package com.bakaroo.bingo.backend.feature.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import com.bakaroo.bingo.backend.common.exception.NotFoundException;
import com.bakaroo.bingo.backend.feature.admin.dto.PlayerVerificationUpdateRequest;
import com.bakaroo.bingo.backend.feature.admin.entity.PlayerVerificationReview;
import com.bakaroo.bingo.backend.feature.admin.repository.PlayerVerificationReviewRepository;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.PlayerVerificationReason;
import com.bakaroo.bingo.backend.feature.user.entity.PlayerVerificationStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminPlayerVerificationServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PlayerVerificationReviewRepository reviewRepository;

    private AdminPlayerVerificationService service;
    private User player;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new AdminPlayerVerificationService(userRepository, reviewRepository);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        player = User.create("251911000025", "Test Player", "hash", AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER), now);
        admin = User.create("251911000001", "Admin User", "hash", AccountStatus.ACTIVE,
                Set.of(UserRole.ADMIN), now);
        ReflectionTestUtils.setField(player, "id", 25L);
        ReflectionTestUtils.setField(admin, "id", 1L);
    }

    @Test
    void recordsReviewAndUpdatesCurrentPlayerStatus() {
        when(userRepository.findByIdForUpdate(25L)).thenReturn(Optional.of(player));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.save(player)).thenReturn(player);
        when(reviewRepository.save(any(PlayerVerificationReview.class))).thenAnswer(invocation -> {
            PlayerVerificationReview review = invocation.getArgument(0);
            ReflectionTestUtils.setField(review, "id", 7L);
            return review;
        });

        var response = service.recordPlayerVerification(
                25L,
                1L,
                new PlayerVerificationUpdateRequest(
                        PlayerVerificationStatus.VERIFIED,
                        PlayerVerificationReason.IDENTITY_AND_AGE_CONFIRMED));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.playerId()).isEqualTo(25L);
        assertThat(response.reviewerUserId()).isEqualTo(1L);
        assertThat(player.getPlayerVerificationStatus()).isEqualTo(PlayerVerificationStatus.VERIFIED);
        assertThat(player.getPlayerVerificationReason())
                .isEqualTo(PlayerVerificationReason.IDENTITY_AND_AGE_CONFIRMED);
        assertThat(player.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(player.getPlayerVerificationReviewedBy()).isSameAs(admin);
        assertThat(player.getPlayerVerificationReviewedAt()).isNotNull();
        verify(userRepository).save(player);
        verify(reviewRepository).save(any(PlayerVerificationReview.class));
    }

    @Test
    void rejectsReasonThatDoesNotMatchTheStatus() {
        assertThatThrownBy(() -> service.recordPlayerVerification(
                25L,
                1L,
                new PlayerVerificationUpdateRequest(
                        PlayerVerificationStatus.VERIFIED,
                        PlayerVerificationReason.AGE_NOT_VERIFIED)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void reportUsesTheRequestedReviewTimeRangeAndPage() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-08T00:00:00Z");
        var pageable = PageRequest.of(1, 25);
        when(reviewRepository
                .findByReviewedAtGreaterThanEqualAndReviewedAtLessThanOrderByReviewedAtDescIdDesc(from, to, pageable))
                .thenReturn(Page.empty(pageable));

        assertThat(service.report(from, to, pageable)).isEmpty();

        verify(reviewRepository)
                .findByReviewedAtGreaterThanEqualAndReviewedAtLessThanOrderByReviewedAtDescIdDesc(from, to, pageable);
    }

    @Test
    void rejectsNonPlayerAccounts() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.recordPlayerVerification(
                1L,
                1L,
                new PlayerVerificationUpdateRequest(
                        PlayerVerificationStatus.IN_REVIEW,
                        PlayerVerificationReason.REVIEW_STARTED)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Player account not found");
    }

    @Test
    void rejectsReviewersWithoutAnActiveAdminRole() {
        User support = User.create("251911000002", "Support User", "hash", AccountStatus.ACTIVE,
                Set.of(UserRole.SUPPORT), Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(support, "id", 2L);
        when(userRepository.findByIdForUpdate(25L)).thenReturn(Optional.of(player));
        when(userRepository.findById(2L)).thenReturn(Optional.of(support));

        assertThatThrownBy(() -> service.recordPlayerVerification(
                25L,
                2L,
                new PlayerVerificationUpdateRequest(
                        PlayerVerificationStatus.IN_REVIEW,
                        PlayerVerificationReason.REVIEW_STARTED)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Admin reviewer not found");
    }
}
