package com.equar.bingo.backend.feature.game.repository;

import com.equar.bingo.backend.feature.game.entity.PrizeEntitlement;
import com.equar.bingo.backend.feature.game.entity.PrizeEntitlementStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PrizeEntitlementRepository extends JpaRepository<PrizeEntitlement, Long> {

    List<PrizeEntitlement> findByClaimantIdOrderByCreatedAtDesc(Long claimantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PrizeEntitlement p where p.id = :id")
    Optional<PrizeEntitlement> findByIdForUpdate(@Param("id") Long id);

    boolean existsByWinClaimId(Long winClaimId);

    List<PrizeEntitlement> findByStatus(PrizeEntitlementStatus status);
}