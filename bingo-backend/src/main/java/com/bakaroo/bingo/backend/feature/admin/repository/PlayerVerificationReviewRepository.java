package com.bakaroo.bingo.backend.feature.admin.repository;

import com.bakaroo.bingo.backend.feature.admin.entity.PlayerVerificationReview;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerVerificationReviewRepository extends JpaRepository<PlayerVerificationReview, Long> {

    List<PlayerVerificationReview> findByPlayer_IdOrderByReviewedAtDescIdDesc(Long playerId);

    Page<PlayerVerificationReview> findByReviewedAtGreaterThanEqualAndReviewedAtLessThanOrderByReviewedAtDescIdDesc(
            java.time.Instant from, java.time.Instant to, Pageable pageable);
}
