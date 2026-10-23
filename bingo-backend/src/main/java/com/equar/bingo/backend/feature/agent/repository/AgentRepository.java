package com.equar.bingo.backend.feature.agent.repository;

import com.equar.bingo.backend.feature.agent.entity.Agent;
import com.equar.bingo.backend.feature.agent.entity.AgentStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<Agent, Long> {

    Optional<Agent> findByUserId(Long userId);

    Optional<Agent> findByUserNormalizedPhone(String normalizedPhone);

    boolean existsByUserId(Long userId);

    boolean existsByIdDocumentTypeIgnoreCaseAndIdDocumentNumberIgnoreCase(String idDocumentType,
            String idDocumentNumber);

    long countByStatus(AgentStatus status);
}
