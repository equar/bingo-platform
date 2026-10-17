package com.equar.bingo.backend.feature.wallet.repository;

import com.equar.bingo.backend.feature.wallet.entity.WalletTransfer;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransferStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletTransferRepository extends JpaRepository<WalletTransfer, Long> {

    Optional<WalletTransfer> findByOperationId(String operationId);

    Page<WalletTransfer> findBySenderIdOrderByCreatedAtDesc(Long senderId, Pageable pageable);

    Page<WalletTransfer> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    @Query("select coalesce(sum(t.amount), 0) from WalletTransfer t where t.sender.id = :senderId and t.createdAt >= :since and t.status = :status")
    BigDecimal sumSentAmountSince(@Param("senderId") Long senderId, @Param("since") Instant since,
            @Param("status") WalletTransferStatus status);

    @Query("select t from WalletTransfer t where (:status is null or t.status = :status) and "
            + "(:senderPhone is null or lower(t.senderPhoneSnapshot) like concat('%', lower(:senderPhone), '%')) and "
            + "(:recipientPhone is null or lower(t.recipientPhoneSnapshot) like concat('%', lower(:recipientPhone), '%')) and "
            + "(:query is null or :query = '' or lower(t.note) like concat('%', lower(:query), '%') "
            + "or lower(t.senderPhoneSnapshot) like concat('%', lower(:query), '%') "
            + "or lower(t.recipientPhoneSnapshot) like concat('%', lower(:query), '%'))")
    Page<WalletTransfer> search(@Param("query") String query,
            @Param("status") WalletTransferStatus status,
            @Param("senderPhone") String senderPhone,
            @Param("recipientPhone") String recipientPhone,
            Pageable pageable);
}