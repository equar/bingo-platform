package com.bakaroo.bingo.backend.feature.wallet.repository;

import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransferPolicy;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletTransferPolicyRepository extends JpaRepository<WalletTransferPolicy, Long> {

    Optional<WalletTransferPolicy> findFirstByOrderByCreatedAtDescIdDesc();

    Page<WalletTransferPolicy> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    @Query("""
            select p from WalletTransferPolicy p
            where (:enabled is null or p.enabled = :enabled)
                and (:search is null or :search = ''
                    or lower(p.currency) like concat('%', :search, '%')
                    or lower(coalesce(p.reason, '')) like concat('%', :search, '%')
                    or (:amount is not null and (p.maxSingleTransferAmount = :amount
                        or p.dailyOutgoingLimitAmount = :amount)))
            """)
    Page<WalletTransferPolicy> searchHistory(@Param("search") String search,
            @Param("enabled") Boolean enabled,
            @Param("amount") BigDecimal amount,
            Pageable pageable);
}
