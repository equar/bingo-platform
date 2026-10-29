package com.bakaroo.bingo.backend.feature.agent.repository;

import com.bakaroo.bingo.backend.feature.agent.entity.CommissionAccrual;
import com.bakaroo.bingo.backend.feature.agent.entity.CommissionAccrualStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommissionAccrualRepository extends JpaRepository<CommissionAccrual, Long> {

    Optional<CommissionAccrual> findByTicketId(Long ticketId);

    List<CommissionAccrual> findByAgentIdOrderByCreatedAtDesc(Long agentId);

    List<CommissionAccrual> findByStatusOrderByCreatedAtDesc(CommissionAccrualStatus status);

    @Query(value = """
            select accrual from CommissionAccrual accrual
            where accrual.agent.id = :agentId
              and (:status is null or accrual.status = :status)
              and (:from is null or coalesce(accrual.settledAt, accrual.createdAt) >= :from)
              and (:to is null or coalesce(accrual.settledAt, accrual.createdAt) < :to)
            order by coalesce(accrual.settledAt, accrual.createdAt) desc, accrual.id desc
            """,
            countQuery = """
            select count(accrual) from CommissionAccrual accrual
            where accrual.agent.id = :agentId
              and (:status is null or accrual.status = :status)
              and (:from is null or coalesce(accrual.settledAt, accrual.createdAt) >= :from)
              and (:to is null or coalesce(accrual.settledAt, accrual.createdAt) < :to)
            """)
    Page<CommissionAccrual> findFinancialHistory(
            @Param("agentId") Long agentId,
            @Param("status") CommissionAccrualStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}