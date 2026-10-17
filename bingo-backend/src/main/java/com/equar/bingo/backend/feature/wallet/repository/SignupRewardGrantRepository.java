package com.equar.bingo.backend.feature.wallet.repository;

import com.equar.bingo.backend.feature.wallet.entity.SignupRewardGrant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignupRewardGrantRepository extends JpaRepository<SignupRewardGrant, Long> {

    boolean existsByPlayerId(Long playerId);

    Optional<SignupRewardGrant> findByPlayerId(Long playerId);

    Page<SignupRewardGrant> findAllByOrderByAwardedAtDesc(Pageable pageable);
}