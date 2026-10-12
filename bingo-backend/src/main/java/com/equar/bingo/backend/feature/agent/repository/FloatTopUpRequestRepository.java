package com.equar.bingo.backend.feature.agent.repository;

import com.equar.bingo.backend.feature.agent.entity.FloatTopUpRequest;
import com.equar.bingo.backend.feature.agent.entity.FloatTopUpRequestStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface FloatTopUpRequestRepository extends JpaRepository<FloatTopUpRequest, Long> {

    List<FloatTopUpRequest> findByAgentIdOrderByCreatedAtDesc(Long agentId);

    List<FloatTopUpRequest> findByStatusOrderByCreatedAtDesc(FloatTopUpRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FloatTopUpRequest r where r.id = :id")
    Optional<FloatTopUpRequest> findByIdForUpdate(@Param("id") Long id);
}