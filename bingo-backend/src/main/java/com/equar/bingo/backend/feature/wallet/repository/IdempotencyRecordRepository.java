package com.equar.bingo.backend.feature.wallet.repository;

import com.equar.bingo.backend.feature.wallet.entity.IdempotencyRecord;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByActorIdAndIdempotencyKey(Long actorId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select record from IdempotencyRecord record
            where record.actorId = :actorId and record.idempotencyKey = :idempotencyKey
            """)
    Optional<IdempotencyRecord> findByActorIdAndIdempotencyKeyForUpdate(
            @Param("actorId") Long actorId,
            @Param("idempotencyKey") String idempotencyKey);
}