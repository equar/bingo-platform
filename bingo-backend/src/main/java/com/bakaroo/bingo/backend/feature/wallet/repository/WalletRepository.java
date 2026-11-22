package com.bakaroo.bingo.backend.feature.wallet.repository;

import com.bakaroo.bingo.backend.feature.wallet.entity.Wallet;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import jakarta.persistence.LockModeType;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    @Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            insert into wallets
                (owner_id, currency, available_balance, promotional_balance, version, created_at, updated_at)
            values (:ownerId, :currency, 0.00, 0.00, 0, current_timestamp(6), current_timestamp(6))
            on duplicate key update id = last_insert_id(id)
            """, nativeQuery = true)
    int insertIfMissing(@Param("ownerId") Long ownerId, @Param("currency") String currency);

    Optional<Wallet> findByOwnerId(Long ownerId);

    long countByOwnerId(Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.owner.id = :ownerId")
    Optional<Wallet> findByOwnerIdForUpdate(@Param("ownerId") Long ownerId);
}
