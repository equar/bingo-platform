package com.equar.bingo.backend.feature.wallet.repository;

import com.equar.bingo.backend.feature.wallet.entity.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    @Query("select t from WalletTransaction t where t.wallet.owner.id = :ownerId order by t.createdAt desc, t.id desc")
    Page<WalletTransaction> findByOwnerId(@Param("ownerId") Long ownerId, Pageable pageable);

    @Query("select count(t) from WalletTransaction t where t.wallet.owner.id = :ownerId")
    long countByOwnerId(@Param("ownerId") Long ownerId);

    boolean existsByOperationId(String operationId);
}