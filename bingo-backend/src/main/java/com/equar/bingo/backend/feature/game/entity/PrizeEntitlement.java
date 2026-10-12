package com.equar.bingo.backend.feature.game.entity;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bingo_prize_entitlements")
public class PrizeEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "win_claim_id", nullable = false, unique = true)
    private WinClaim winClaim;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claimant_id", nullable = false)
    private User claimant;

    @Enumerated(EnumType.STRING)
    @Column(name = "prize_type", nullable = false, length = 20)
    private BingoPatternType prizeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_path", nullable = false, length = 20)
    private PrizeSettlementPath settlementPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PrizeEntitlementStatus status;

    @Column(name = "prize_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal prizeAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "operation_id", nullable = false, length = 36, unique = true)
    private String operationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settled_by_user_id")
    private User settledBy;

    @Column(name = "settlement_reference", length = 120)
    private String settlementReference;

    @Column(name = "settlement_note", length = 500)
    private String settlementNote;

    public PrizeEntitlement() {
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public WinClaim getWinClaim() {
        return winClaim;
    }

    public void setWinClaim(WinClaim winClaim) {
        this.winClaim = winClaim;
    }

    public GameSession getSession() {
        return session;
    }

    public void setSession(GameSession session) {
        this.session = session;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public void setTicket(Ticket ticket) {
        this.ticket = ticket;
    }

    public User getClaimant() {
        return claimant;
    }

    public void setClaimant(User claimant) {
        this.claimant = claimant;
    }

    public BingoPatternType getPrizeType() {
        return prizeType;
    }

    public void setPrizeType(BingoPatternType prizeType) {
        this.prizeType = prizeType;
    }

    public PrizeSettlementPath getSettlementPath() {
        return settlementPath;
    }

    public void setSettlementPath(PrizeSettlementPath settlementPath) {
        this.settlementPath = settlementPath;
    }

    public PrizeEntitlementStatus getStatus() {
        return status;
    }

    public void setStatus(PrizeEntitlementStatus status) {
        this.status = status;
    }

    public BigDecimal getPrizeAmount() {
        return prizeAmount;
    }

    public void setPrizeAmount(BigDecimal prizeAmount) {
        this.prizeAmount = prizeAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(Instant settledAt) {
        this.settledAt = settledAt;
    }

    public User getSettledBy() {
        return settledBy;
    }

    public void setSettledBy(User settledBy) {
        this.settledBy = settledBy;
    }

    public String getSettlementReference() {
        return settlementReference;
    }

    public void setSettlementReference(String settlementReference) {
        this.settlementReference = settlementReference;
    }

    public String getSettlementNote() {
        return settlementNote;
    }

    public void setSettlementNote(String settlementNote) {
        this.settlementNote = settlementNote;
    }
}