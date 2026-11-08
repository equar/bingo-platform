package com.bakaroo.bingo.backend.feature.admin.repository;

import com.bakaroo.bingo.backend.feature.admin.entity.PlayerVerificationReview;
import com.bakaroo.bingo.backend.feature.user.entity.PlayerVerificationStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerVerificationReviewRepository extends JpaRepository<PlayerVerificationReview, Long> {

    List<PlayerVerificationReview> findByPlayer_IdOrderByReviewedAtDescIdDesc(Long playerId);

    Page<PlayerVerificationReview> findByReviewedAtGreaterThanEqualAndReviewedAtLessThanOrderByReviewedAtDescIdDesc(
            Instant from, Instant to, Pageable pageable);

    @Query("""
            select r from PlayerVerificationReview r
            where r.reviewedAt >= :from and r.reviewedAt < :to
                and (:status is null or r.status = :status)
                and (:query is null
                    or lower(r.player.displayName) like concat('%', :query, '%')
                    or lower(r.player.normalizedPhone) like concat('%', :query, '%')
                    or lower(r.reviewer.displayName) like concat('%', :query, '%')
                    or lower(str(r.status)) like concat('%', :query, '%')
                    or lower(str(r.reason)) like concat('%', :query, '%')
                    or (:numericQuery is not null and (r.id = :numericQuery
                        or r.player.id = :numericQuery or r.reviewer.id = :numericQuery)))
            order by r.reviewedAt desc, r.id desc
            """)
    Page<PlayerVerificationReview> searchReport(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("query") String query,
            @Param("numericQuery") Long numericQuery,
            @Param("status") PlayerVerificationStatus status,
            Pageable pageable);
}
