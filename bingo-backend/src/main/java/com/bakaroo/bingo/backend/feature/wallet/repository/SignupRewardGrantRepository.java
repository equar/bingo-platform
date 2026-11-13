package com.bakaroo.bingo.backend.feature.wallet.repository;

import com.bakaroo.bingo.backend.feature.wallet.entity.SignupRewardGrant;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SignupRewardGrantRepository extends JpaRepository<SignupRewardGrant, Long> {

    boolean existsByPlayerId(Long playerId);

    boolean existsByPolicyIdAndPlayerId(Long policyId, Long playerId);

    boolean existsByPolicyIdAndTriggerReferenceKey(Long policyId, String triggerReferenceKey);

    Optional<SignupRewardGrant> findByPlayerId(Long playerId);

    Page<SignupRewardGrant> findAllByOrderByAwardedAtDesc(Pageable pageable);

    @Query("""
            select g from SignupRewardGrant g
            join g.player player
            join g.policy policy
            where (:search is null or :search = ''
                or lower(player.displayName) like concat('%', :search, '%')
                or lower(coalesce(player.normalizedPhone, '')) like concat('%', :search, '%')
                or lower(g.currency) like concat('%', :search, '%')
                or lower(policy.name) like concat('%', :search, '%')
                or lower(coalesce(policy.reason, '')) like concat('%', :search, '%')
                or (:amount is not null and g.rewardAmount = :amount))
            """)
    Page<SignupRewardGrant> search(@Param("search") String search,
            @Param("amount") BigDecimal amount,
            Pageable pageable);
}
