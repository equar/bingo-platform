package com.equar.bingo.backend.feature.agent.repository;

import com.equar.bingo.backend.feature.agent.entity.AgentFloat;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AgentFloatRepository extends JpaRepository<AgentFloat, Long> {

    Optional<AgentFloat> findByAgentId(Long agentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from AgentFloat f where f.agent.id = :agentId")
    Optional<AgentFloat> findByAgentIdForUpdate(@Param("agentId") Long agentId);
}