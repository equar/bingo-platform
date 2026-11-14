package com.bakaroo.bingo.backend.feature.wallet.repository;

import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransaction;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    @Query("""
            select coalesce(sum(t.amount), 0) from WalletTransaction t
            where t.wallet.owner.id = :ownerId
                and t.transactionType = com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransactionType.DEPOSIT_CREDIT
                and t.currency = 'ETB'
                and t.createdAt >= :effectiveFrom
                and (:effectiveUntil is null or t.createdAt < :effectiveUntil)
            """)
    java.math.BigDecimal sumEtbDepositCreditsPostedInCampaignWindow(@Param("ownerId") Long ownerId,
            @Param("effectiveFrom") java.time.Instant effectiveFrom,
            @Param("effectiveUntil") java.time.Instant effectiveUntil);

    @Query("select t from WalletTransaction t where t.wallet.owner.id = :ownerId order by t.createdAt desc, t.id desc")
    Page<WalletTransaction> findByOwnerId(@Param("ownerId") Long ownerId, Pageable pageable);

    @Query("select count(t) from WalletTransaction t where t.wallet.owner.id = :ownerId")
    long countByOwnerId(@Param("ownerId") Long ownerId);

    boolean existsByOperationId(String operationId);

    Optional<WalletTransaction> findFirstByReferenceTypeAndReferenceIdOrderByCreatedAtDescIdDesc(
            String referenceType, Long referenceId);

    java.util.List<WalletTransaction> findByReferenceTypeAndReferenceIdOrderByCreatedAtAscIdAsc(
            String referenceType, Long referenceId);
}
