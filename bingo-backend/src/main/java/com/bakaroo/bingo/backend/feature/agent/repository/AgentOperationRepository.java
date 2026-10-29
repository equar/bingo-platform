package com.bakaroo.bingo.backend.feature.agent.repository;

import com.bakaroo.bingo.backend.feature.agent.entity.AgentOperation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentOperationRepository extends JpaRepository<AgentOperation, Long> {

    Optional<AgentOperation> findByOperationId(String operationId);

    List<AgentOperation> findByAgentIdOrderByCreatedAtDesc(Long agentId);
}