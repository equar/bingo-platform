package com.bakaroo.bingo.backend.feature.wallet.repository;
import com.bakaroo.bingo.backend.feature.wallet.entity.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest,Long>{
 Page<WithdrawalRequest> findByPlayerIdOrderByCreatedAtDescIdDesc(Long playerId,Pageable pageable);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from WithdrawalRequest r where r.id=:id") Optional<WithdrawalRequest> findByIdForUpdate(@Param("id") Long id);
 @Query("select r from WithdrawalRequest r where (:status is null or r.status=:status) and (:query is null or lower(r.player.displayName) like concat('%',:query,'%') or lower(r.player.normalizedPhone) like concat('%',:query,'%') or lower(r.operationId) like concat('%',:query,'%') or r.id=:id) order by r.createdAt desc,r.id desc")
 Page<WithdrawalRequest> search(@Param("query") String query,@Param("id") Long id,@Param("status") WithdrawalStatus status,Pageable pageable);
}
