package com.equar.bingo.backend.feature.agent.entity;

import com.equar.bingo.backend.feature.user.entity.User;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "agent_float_top_up_requests")
public class FloatTopUpRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private User requestedBy;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private FloatTopUpRequestStatus status;

    @Column(name = "operation_id", nullable = false, length = 36, unique = true)
    private String operationId;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "submission_note", length = 500)
    private String submissionNote;

    @Column(name = "receiving_bank_account_id")
    private Long receivingBankAccountId;

    @Column(name = "bank_name_snapshot", length = 120)
    private String bankNameSnapshot;

    @Column(name = "masked_account_snapshot", length = 32)
    private String maskedAccountSnapshot;

    @Column(name = "account_holder_snapshot", length = 120)
    private String accountHolderSnapshot;

    @Column(name = "transfer_reference", length = 120)
    private String transferReference;

    @Column(name = "supporting_evidence_note", length = 500)
    private String supportingEvidenceNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FloatTopUpRequest() {
    }

    public static FloatTopUpRequest create(Agent agent, User requestedBy, BigDecimal amount, String currency,
            String operationId) {
        FloatTopUpRequest request = new FloatTopUpRequest();
        request.agent = agent;
        request.requestedBy = requestedBy;
        request.amount = amount;
        request.currency = currency;
        request.status = FloatTopUpRequestStatus.PENDING;
        request.operationId = operationId;
        return request;
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

    public Agent getAgent() {
        return agent;
    }

    public User getRequestedBy() {
        return requestedBy;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public FloatTopUpRequestStatus getStatus() {
        return status;
    }

    public void setStatus(FloatTopUpRequestStatus status) {
        this.status = status;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public String getSubmissionNote() {
        return submissionNote;
    }

    public void setReceivingBankSnapshot(Long bankAccountId, String bankName, String maskedAccount,
            String accountHolder, String reference, String evidenceNote) {
        this.receivingBankAccountId = bankAccountId;
        this.bankNameSnapshot = bankName;
        this.maskedAccountSnapshot = maskedAccount;
        this.accountHolderSnapshot = accountHolder;
        this.transferReference = reference;
        this.supportingEvidenceNote = evidenceNote;
    }

    public Long getReceivingBankAccountId() { return receivingBankAccountId; }
    public String getBankNameSnapshot() { return bankNameSnapshot; }
    public String getMaskedAccountSnapshot() { return maskedAccountSnapshot; }
    public String getAccountHolderSnapshot() { return accountHolderSnapshot; }
    public String getTransferReference() { return transferReference; }
    public String getSupportingEvidenceNote() { return supportingEvidenceNote; }

    public void setSubmissionNote(String submissionNote) {
        this.submissionNote = submissionNote;
    }

    public User getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(User reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
