package com.bakaroo.bingo.backend.feature.agent.entity;

import com.bakaroo.bingo.backend.feature.game.entity.PrizeEntitlement;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payout_verification_records")
public class PayoutVerificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entitlement_id", nullable = false, unique = true)
    private PrizeEntitlement entitlement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "payout_code", nullable = false, length = 80, unique = true)
    private String payoutCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayoutVerificationStatus status;

    @Column(name = "disbursed_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal disbursedAmount;

    @Column(name = "operation_id", nullable = false, length = 36, unique = true)
    private String operationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "float_transaction_id")
    private AgentFloatTransaction floatTransaction;

    @Column(name = "clearing_reference", length = 120)
    private String clearingReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_user_id")
    private User verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PayoutVerificationRecord() {
    }

    public static PayoutVerificationRecord create(PrizeEntitlement entitlement, Agent agent, String payoutCode,
            BigDecimal disbursedAmount, String operationId) {
        PayoutVerificationRecord record = new PayoutVerificationRecord();
        record.entitlement = entitlement;
        record.agent = agent;
        record.payoutCode = payoutCode;
        record.status = PayoutVerificationStatus.PENDING;
        record.disbursedAmount = disbursedAmount;
        record.operationId = operationId;
        return record;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public PrizeEntitlement getEntitlement() {
        return entitlement;
    }

    public Agent getAgent() {
        return agent;
    }

    public String getPayoutCode() {
        return payoutCode;
    }

    public PayoutVerificationStatus getStatus() {
        return status;
    }

    public void setStatus(PayoutVerificationStatus status) {
        this.status = status;
    }

    public BigDecimal getDisbursedAmount() {
        return disbursedAmount;
    }

    public String getOperationId() {
        return operationId;
    }

    public AgentFloatTransaction getFloatTransaction() {
        return floatTransaction;
    }

    public void setFloatTransaction(AgentFloatTransaction floatTransaction) {
        this.floatTransaction = floatTransaction;
    }

    public String getClearingReference() {
        return clearingReference;
    }

    public void setClearingReference(String clearingReference) {
        this.clearingReference = clearingReference;
    }

    public User getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(User verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}