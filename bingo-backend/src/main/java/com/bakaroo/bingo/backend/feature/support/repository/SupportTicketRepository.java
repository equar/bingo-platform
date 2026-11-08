package com.bakaroo.bingo.backend.feature.support.repository;

import com.bakaroo.bingo.backend.feature.support.entity.SupportTicket;
import com.bakaroo.bingo.backend.feature.support.entity.SupportLinkedEntityType;
import com.bakaroo.bingo.backend.feature.support.entity.SupportTicketStatus;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
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

    long countByRequesterId(Long requesterId);

    long countByLinkedEntityTypeAndLinkedEntityId(SupportLinkedEntityType linkedEntityType, Long linkedEntityId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SupportTicket t where t.id = :id")
    Optional<SupportTicket> findByIdForUpdate(@Param("id") Long id);

    List<SupportTicket> findByStatusInOrderByCreatedAtDesc(List<SupportTicketStatus> statuses);

    Page<SupportTicket> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            Instant from, Instant to, Pageable pageable);

    @Query("""
            select t from SupportTicket t
            left join t.assignedTo assigned
            where t.createdAt >= :from and t.createdAt < :to
                and (:status is null or t.status = :status)
                and (:query is null
                    or lower(t.ticketNumber) like concat('%', :query, '%')
                    or lower(t.subject) like concat('%', :query, '%')
                    or lower(t.requester.displayName) like concat('%', :query, '%')
                    or lower(t.requester.normalizedPhone) like concat('%', :query, '%')
                    or lower(coalesce(t.linkedEntityReference, '')) like concat('%', :query, '%')
                    or (:numericQuery is not null and (t.id = :numericQuery
                        or t.requester.id = :numericQuery
                        or t.linkedEntityId = :numericQuery
                        or assigned.id = :numericQuery)))
            order by t.createdAt desc, t.id desc
            """)
    Page<SupportTicket> searchReport(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("query") String query,
            @Param("numericQuery") Long numericQuery,
            @Param("status") SupportTicketStatus status,
            Pageable pageable);
}
