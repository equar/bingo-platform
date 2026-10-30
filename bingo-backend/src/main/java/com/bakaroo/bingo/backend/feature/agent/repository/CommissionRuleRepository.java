package com.bakaroo.bingo.backend.feature.agent.repository;

import com.bakaroo.bingo.backend.feature.agent.entity.CommissionRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommissionRuleRepository extends JpaRepository<CommissionRule, Long> {

    Optional<CommissionRule> findFirstByActiveTrueOrderByVersionNumberDesc();

    Optional<CommissionRule> findFirstByOrderByVersionNumberDesc();

    List<CommissionRule> findAllByOrderByVersionNumberDesc();
}
