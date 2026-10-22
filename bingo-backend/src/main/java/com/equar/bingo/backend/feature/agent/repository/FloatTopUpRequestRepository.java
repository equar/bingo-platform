package com.equar.bingo.backend.feature.agent.repository;

import com.equar.bingo.backend.feature.agent.entity.FloatTopUpRequest;
import com.equar.bingo.backend.feature.agent.entity.FloatTopUpRequestStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface FloatTopUpRequestRepository extends JpaRepository<FloatTopUpRequest, Long> {

    List<FloatTopUpRequest> findByAgentIdOrderByCreatedAtDesc(Long agentId);

    List<FloatTopUpRequest> findByStatusOrderByCreatedAtDesc(FloatTopUpRequestStatus status);

    @Query(value = """
            select request from FloatTopUpRequest request
            where request.agent.id = :agentId
              and (:status is null or request.status = :status)
              and (:from is null or request.createdAt >= :from)
              and (:to is null or request.createdAt < :to)
            order by request.createdAt desc, request.id desc
            """,
            countQuery = """
            select count(request) from FloatTopUpRequest request
            where request.agent.id = :agentId
              and (:status is null or request.status = :status)
              and (:from is null or request.createdAt >= :from)
              and (:to is null or request.createdAt < :to)
            """)
    Page<FloatTopUpRequest> findFinancialHistory(
            @Param("agentId") Long agentId,
            @Param("status") FloatTopUpRequestStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FloatTopUpRequest r where r.id = :id")
    Optional<FloatTopUpRequest> findByIdForUpdate(@Param("id") Long id);
}