package com.equar.bingo.backend.feature.game.repository;

import com.equar.bingo.backend.feature.game.entity.Ticket;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Page<Ticket> findBySessionIdAndOwnerId(Long sessionId, Long ownerId, Pageable pageable);

    List<Ticket> findBySessionIdAndOwnerIdOrderByPurchasedAtDesc(Long sessionId, Long ownerId);

    Optional<Ticket> findByIdAndOwnerId(Long id, Long ownerId);

    long countBySessionId(Long sessionId);

    long countBySessionIdAndOwnerId(Long sessionId, Long ownerId);
}