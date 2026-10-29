package com.bakaroo.bingo.backend.feature.wallet.entity;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "deposit_claims", uniqueConstraints = @UniqueConstraint(name = "uk_deposit_claims_reference", columnNames = "normalized_telebirr_reference"))
public class DepositClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private User player;

    @Column(name = "submitted_cash_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal submittedCashAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "conversion_rate_snapshot", nullable = false, precision = 19, scale = 6)
    private BigDecimal conversionRateSnapshot;

    @Column(name = "calculated_credit_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal calculatedCreditAmount;

    @Column(name = "normalized_telebirr_reference", nullable = false, length = 120)
    private String normalizedTelebirrReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private DepositClaimStatus status;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_user_id")
    private User verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "operation_id", nullable = false, length = 36)
    private String operationId;

    protected DepositClaim() {
    }

    public static DepositClaim create(User player,
            BigDecimal submittedCashAmount,
            String currency,
            BigDecimal conversionRateSnapshot,
            BigDecimal calculatedCreditAmount,
            String normalizedTelebirrReference,
            String operationId,
            Instant submittedAt) {
        DepositClaim claim = new DepositClaim();
        claim.player = player;
        claim.submittedCashAmount = submittedCashAmount;
        claim.currency = currency;
        claim.conversionRateSnapshot = conversionRateSnapshot;
        claim.calculatedCreditAmount = calculatedCreditAmount;
        claim.normalizedTelebirrReference = normalizedTelebirrReference;
        claim.status = DepositClaimStatus.PENDING;
        claim.operationId = operationId;
        claim.submittedAt = submittedAt;
        return claim;
    }

    public Long getId() {
        return id;
    }

    public User getPlayer() {
        return player;
    }

    public BigDecimal getSubmittedCashAmount() {
        return submittedCashAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getConversionRateSnapshot() {
        return conversionRateSnapshot;
    }

    public BigDecimal getCalculatedCreditAmount() {
        return calculatedCreditAmount;
    }

    public String getNormalizedTelebirrReference() {
        return normalizedTelebirrReference;
    }

    public DepositClaimStatus getStatus() {
        return status;
    }

    public void setStatus(DepositClaimStatus status) {
        this.status = status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
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

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public String getOperationId() {
        return operationId;
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
}