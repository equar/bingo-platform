package com.equar.bingo.backend.feature.agent.repository;

import com.equar.bingo.backend.feature.agent.entity.AgentFloatTransaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentFloatTransactionRepository extends JpaRepository<AgentFloatTransaction, Long> {

    Optional<AgentFloatTransaction> findByOperationId(String operationId);

    List<AgentFloatTransaction> findByAgentFloatAgentIdOrderByCreatedAtDesc(Long agentId);
}