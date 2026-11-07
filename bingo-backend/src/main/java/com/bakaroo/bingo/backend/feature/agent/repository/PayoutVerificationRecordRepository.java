package com.bakaroo.bingo.backend.feature.agent.repository;

import com.bakaroo.bingo.backend.feature.agent.entity.PayoutVerificationRecord;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayoutVerificationRecordRepository extends JpaRepository<PayoutVerificationRecord, Long> {

    Optional<PayoutVerificationRecord> findByPayoutCode(String payoutCode);

    List<PayoutVerificationRecord> findByAgentIdOrderByCreatedAtDesc(Long agentId);

    long countByAgentId(Long agentId);

    @Query("select r.agent.id, count(r) from PayoutVerificationRecord r "
            + "where r.agent.id in :agentIds group by r.agent.id")
    List<Object[]> countByAgentIds(@Param("agentIds") Collection<Long> agentIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PayoutVerificationRecord p where p.payoutCode = :payoutCode")
    Optional<PayoutVerificationRecord> findByPayoutCodeForUpdate(@Param("payoutCode") String payoutCode);
}
