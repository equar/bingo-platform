package com.equar.bingo.backend.feature.game.repository;

import com.equar.bingo.backend.feature.game.entity.BingoPatternType;
import com.equar.bingo.backend.feature.game.entity.WinClaim;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from WinClaim c where c.id = :id")
    Optional<WinClaim> findByIdForUpdate(@Param("id") Long id);

    boolean existsByTicketIdAndClaimedPatternAndDrawSequenceSnapshot(Long ticketId, BingoPatternType claimedPattern, int drawSequenceSnapshot);

    List<WinClaim> findBySessionIdOrderByCreatedAtDesc(Long sessionId);
}