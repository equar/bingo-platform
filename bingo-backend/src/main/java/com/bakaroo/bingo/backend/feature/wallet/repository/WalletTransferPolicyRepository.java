package com.bakaroo.bingo.backend.feature.wallet.repository;

import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransferPolicy;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransferPolicyRepository extends JpaRepository<WalletTransferPolicy, Long> {

    Optional<WalletTransferPolicy> findFirstByOrderByCreatedAtDescIdDesc();

    Page<WalletTransferPolicy> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
