package com.bakaroo.bingo.backend.feature.wallet.repository;

import com.bakaroo.bingo.backend.feature.wallet.entity.SignupRewardPolicy;
import java.util.Optional;
import java.util.List;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SignupRewardPolicyRepository extends JpaRepository<SignupRewardPolicy, Long> {

    Optional<SignupRewardPolicy> findFirstByOrderByCreatedAtDescIdDesc();

    Optional<SignupRewardPolicy> findFirstByTriggerTypeOrderByCreatedAtDescIdDesc(
            SignupRewardPolicy.TriggerType triggerType);

    List<SignupRewardPolicy> findAllByTriggerTypeOrderByCreatedAtDescIdDesc(SignupRewardPolicy.TriggerType triggerType);

    List<SignupRewardPolicy> findAllByEnabledTrueOrderByCreatedAtDesc();

    Page<SignupRewardPolicy> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
            select p from SignupRewardPolicy p
            where (:enabled is null or p.enabled = :enabled)
                and (:search is null or :search = ''
                    or lower(p.currency) like concat('%', :search, '%')
                    or lower(p.name) like concat('%', :search, '%')
                    or lower(coalesce(p.reason, '')) like concat('%', :search, '%')
                    or (:amount is not null and p.rewardAmount = :amount))
            """)
    Page<SignupRewardPolicy> searchHistory(@Param("search") String search,
            @Param("enabled") Boolean enabled,
            @Param("amount") BigDecimal amount,
            Pageable pageable);
}
