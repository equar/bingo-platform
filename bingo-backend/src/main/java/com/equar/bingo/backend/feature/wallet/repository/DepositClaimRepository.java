package com.equar.bingo.backend.feature.wallet.repository;

import com.equar.bingo.backend.feature.wallet.entity.DepositClaim;
import com.equar.bingo.backend.feature.wallet.entity.DepositClaimStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface DepositClaimRepository extends JpaRepository<DepositClaim, Long> {

    @Query("select d from DepositClaim d where d.player.id = :playerId order by d.submittedAt desc, d.id desc")
    Page<DepositClaim> findByPlayerId(@Param("playerId") Long playerId, Pageable pageable);

    @Query("select d from DepositClaim d order by d.submittedAt desc, d.id desc")
    Page<DepositClaim> findAllOrderBySubmittedAt(Pageable pageable);

    @Query("""
            select d from DepositClaim d
            where (:status is null or d.status = :status)
                and (:search is null
                    or lower(d.normalizedTelebirrReference) like concat('%', :search, '%')
                    or lower(d.operationId) like concat('%', :search, '%')
                    or lower(d.player.displayName) like concat('%', :search, '%')
                    or lower(d.player.normalizedPhone) like concat('%', :search, '%'))
                and (:referenceId is null or d.id = :referenceId or d.player.id = :referenceId)
            order by d.submittedAt desc, d.id desc
            """)
    Page<DepositClaim> searchAdminDeposits(@Param("status") DepositClaimStatus status,
            @Param("search") String search, @Param("referenceId") Long referenceId, Pageable pageable);

    boolean existsByNormalizedTelebirrReference(String normalizedTelebirrReference);

    Optional<DepositClaim> findByNormalizedTelebirrReference(String normalizedTelebirrReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DepositClaim d where d.id = :id")
    Optional<DepositClaim> findByIdForUpdate(@Param("id") Long id);

    Page<DepositClaim> findByStatus(DepositClaimStatus status, Pageable pageable);
}