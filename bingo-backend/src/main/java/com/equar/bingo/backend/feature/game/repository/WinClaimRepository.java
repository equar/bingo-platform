package com.equar.bingo.backend.feature.game.repository;

import com.equar.bingo.backend.feature.game.entity.BingoPatternType;
import com.equar.bingo.backend.feature.game.entity.WinClaim;
import com.equar.bingo.backend.feature.game.entity.WinClaimStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface WinClaimRepository extends JpaRepository<WinClaim, Long> {

    Page<WinClaim> findByClaimantIdOrderByCreatedAtDesc(Long claimantId, Pageable pageable);

    @Query(value = """
            select claim from WinClaim claim
            join claim.claimant claimant
            where (:status is null or claim.status = :status)
                and (:pattern is null or claim.claimedPattern = :pattern)
                and (:validationPassed is null or claim.serverValidationPassed = :validationPassed)
                and (
                    :search is null
                    or lower(claimant.displayName) like concat('%', :search, '%')
                    or lower(claimant.normalizedPhone) like concat('%', :search, '%')
                    or (:referenceId is not null and (
                        claim.id = :referenceId
                        or claim.ticket.id = :referenceId
                        or claim.session.id = :referenceId
                        or claimant.id = :referenceId
                    ))
                )
            """,
            countQuery = """
            select count(claim) from WinClaim claim
            join claim.claimant claimant
            where (:status is null or claim.status = :status)
                and (:pattern is null or claim.claimedPattern = :pattern)
                and (:validationPassed is null or claim.serverValidationPassed = :validationPassed)
                and (
                    :search is null
                    or lower(claimant.displayName) like concat('%', :search, '%')
                    or lower(claimant.normalizedPhone) like concat('%', :search, '%')
                    or (:referenceId is not null and (
                        claim.id = :referenceId
                        or claim.ticket.id = :referenceId
                        or claim.session.id = :referenceId
                        or claimant.id = :referenceId
                    ))
                )
            """)
    Page<WinClaim> searchAdminClaims(
            @Param("search") String search,
            @Param("referenceId") Long referenceId,
            @Param("status") WinClaimStatus status,
            @Param("pattern") BingoPatternType pattern,
            @Param("validationPassed") Boolean validationPassed,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from WinClaim c where c.id = :id")
    Optional<WinClaim> findByIdForUpdate(@Param("id") Long id);

    boolean existsByTicketIdAndClaimedPatternAndDrawSequenceSnapshot(Long ticketId, BingoPatternType claimedPattern, int drawSequenceSnapshot);

    List<WinClaim> findBySessionIdOrderByCreatedAtDesc(Long sessionId);
}