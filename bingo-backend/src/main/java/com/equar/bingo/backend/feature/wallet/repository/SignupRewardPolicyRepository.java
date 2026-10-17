package com.equar.bingo.backend.feature.wallet.repository;

import com.equar.bingo.backend.feature.wallet.entity.SignupRewardPolicy;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignupRewardPolicyRepository extends JpaRepository<SignupRewardPolicy, Long> {

    Optional<SignupRewardPolicy> findFirstByOrderByCreatedAtDesc();

    Page<SignupRewardPolicy> findAllByOrderByCreatedAtDesc(Pageable pageable);
}