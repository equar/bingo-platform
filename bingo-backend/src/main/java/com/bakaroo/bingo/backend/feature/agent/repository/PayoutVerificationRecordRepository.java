package com.bakaroo.bingo.backend.feature.agent.repository;

import com.bakaroo.bingo.backend.feature.agent.entity.PayoutVerificationRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PayoutVerificationRecordRepository extends JpaRepository<PayoutVerificationRecord, Long> {

    Optional<PayoutVerificationRecord> findByPayoutCode(String payoutCode);

    List<PayoutVerificationRecord> findByAgentIdOrderByCreatedAtDesc(Long agentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PayoutVerificationRecord p where p.payoutCode = :payoutCode")
    Optional<PayoutVerificationRecord> findByPayoutCodeForUpdate(@Param("payoutCode") String payoutCode);
}