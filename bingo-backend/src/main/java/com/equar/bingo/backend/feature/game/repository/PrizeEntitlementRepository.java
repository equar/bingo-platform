package com.equar.bingo.backend.feature.game.repository;

import com.equar.bingo.backend.feature.game.entity.PrizeEntitlement;
import com.equar.bingo.backend.feature.game.entity.PrizeEntitlementStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PrizeEntitlementRepository extends JpaRepository<PrizeEntitlement, Long> {

    @Query("""
            select p from PrizeEntitlement p
            where (:status is null or p.status = :status)
              and (:query is null
            or lower(p.operationId) like concat('%', :query, '%')
            or lower(coalesce(p.payoutCode, '')) like concat('%', :query, '%')
            or lower(p.currency) like concat('%', :query, '%')
            or lower(str(p.prizeType)) like concat('%', :query, '%')
            or lower(str(p.settlementPath)) like concat('%', :query, '%')
            or lower(str(p.status)) like concat('%', :query, '%')
            or lower(coalesce(p.settlementReference, '')) like concat('%', :query, '%')
            or lower(coalesce(p.settlementNote, '')) like concat('%', :query, '%')
            or lower(p.claimant.displayName) like concat('%', :query, '%')
            or lower(p.claimant.normalizedPhone) like concat('%', :query, '%')
            or lower(coalesce(p.claimant.email, '')) like concat('%', :query, '%')
            or (:numericQuery is not null and (p.id = :numericQuery
                or p.winClaim.id = :numericQuery
                or p.session.id = :numericQuery
                or p.ticket.id = :numericQuery
                or p.claimant.id = :numericQuery)))
            """)
    Page<PrizeEntitlement> searchAdmin(@Param("query") String query, @Param("numericQuery") Long numericQuery,
            @Param("status") PrizeEntitlementStatus status, Pageable pageable);

    List<PrizeEntitlement> findByClaimantIdOrderByCreatedAtDesc(Long claimantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PrizeEntitlement p where p.id = :id")
    Optional<PrizeEntitlement> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PrizeEntitlement p where p.payoutCode = :payoutCode")
    Optional<PrizeEntitlement> findByPayoutCodeForUpdate(@Param("payoutCode") String payoutCode);

    Optional<PrizeEntitlement> findByPayoutCode(String payoutCode);

    boolean existsByWinClaimId(Long winClaimId);

    List<PrizeEntitlement> findByStatus(PrizeEntitlementStatus status);
}