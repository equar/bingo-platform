package com.equar.bingo.backend.feature.agent.repository;

import com.equar.bingo.backend.feature.agent.entity.CommissionAccrual;
import com.equar.bingo.backend.feature.agent.entity.CommissionAccrualStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommissionAccrualRepository extends JpaRepository<CommissionAccrual, Long> {

    Optional<CommissionAccrual> findByTicketId(Long ticketId);

    List<CommissionAccrual> findByAgentIdOrderByCreatedAtDesc(Long agentId);

    List<CommissionAccrual> findByStatusOrderByCreatedAtDesc(CommissionAccrualStatus status);
}