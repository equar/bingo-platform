package com.bakaroo.bingo.backend.feature.agent.repository;

import com.bakaroo.bingo.backend.feature.agent.entity.AgentFloatTransaction;
import com.bakaroo.bingo.backend.feature.agent.entity.AgentFloatTransactionType;
import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentFloatTransactionRepository extends JpaRepository<AgentFloatTransaction, Long> {

    Optional<AgentFloatTransaction> findByOperationId(String operationId);

    List<AgentFloatTransaction> findByAgentFloatAgentIdOrderByCreatedAtDesc(Long agentId);

    long countByAgentFloatAgentId(Long agentId);

    @Query("select count(transaction) from AgentFloatTransaction transaction "
            + "where transaction.agentFloat.agent.id = :agentId "
            + "and transaction.transactionType = :transactionType")
    long countByAgentIdAndTransactionType(
            @Param("agentId") Long agentId,
            @Param("transactionType") AgentFloatTransactionType transactionType);

    @Query("select t.agentFloat.agent.id, count(t) from AgentFloatTransaction t "
            + "where t.agentFloat.agent.id in :agentIds group by t.agentFloat.agent.id")
    List<Object[]> countByAgentIds(@Param("agentIds") Collection<Long> agentIds);

    @Query(value = """
            select transaction from AgentFloatTransaction transaction
            where transaction.agentFloat.agent.id = :agentId
              and (:transactionType is null or transaction.transactionType = :transactionType)
              and (:from is null or transaction.createdAt >= :from)
              and (:to is null or transaction.createdAt < :to)
            order by transaction.createdAt desc, transaction.id desc
            """,
            countQuery = """
            select count(transaction) from AgentFloatTransaction transaction
            where transaction.agentFloat.agent.id = :agentId
              and (:transactionType is null or transaction.transactionType = :transactionType)
              and (:from is null or transaction.createdAt >= :from)
              and (:to is null or transaction.createdAt < :to)
            """)
    Page<AgentFloatTransaction> findFinancialHistory(
            @Param("agentId") Long agentId,
            @Param("transactionType") com.bakaroo.bingo.backend.feature.agent.entity.AgentFloatTransactionType transactionType,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
