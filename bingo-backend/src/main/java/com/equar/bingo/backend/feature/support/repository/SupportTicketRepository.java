package com.equar.bingo.backend.feature.support.repository;

import com.equar.bingo.backend.feature.support.entity.SupportTicket;
import com.equar.bingo.backend.feature.support.entity.SupportTicketStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface SupportTicketRepository
        extends JpaRepository<SupportTicket, Long>, JpaSpecificationExecutor<SupportTicket> {

    Page<SupportTicket> findByRequesterIdOrderByCreatedAtDesc(Long requesterId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SupportTicket t where t.id = :id")
    Optional<SupportTicket> findByIdForUpdate(@Param("id") Long id);

    List<SupportTicket> findByStatusInOrderByCreatedAtDesc(List<SupportTicketStatus> statuses);
}