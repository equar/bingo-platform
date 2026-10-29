package com.bakaroo.bingo.backend.feature.agent.entity;

import com.bakaroo.bingo.backend.feature.game.entity.Ticket;
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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "commission_accruals")
public class CommissionAccrual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false, unique = true)
    private Ticket ticket;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commission_rule_id", nullable = false)
    private CommissionRule commissionRule;

    @Column(name = "commission_rule_version", nullable = false)
    private int commissionRuleVersion;

    @Column(name = "sale_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal saleAmount;

    @Column(name = "commission_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal commissionAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CommissionAccrualStatus status;

    @Column(name = "operation_id", nullable = false, length = 36, unique = true)
    private String operationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "settlement_reference", length = 120)
    private String settlementReference;

    @Column(name = "settlement_note", length = 500)
    private String settlementNote;

    protected CommissionAccrual() {
    }

    public static CommissionAccrual create(Agent agent, Ticket ticket, Long sessionId, CommissionRule commissionRule,
            BigDecimal saleAmount, BigDecimal commissionAmount, String operationId) {
        CommissionAccrual accrual = new CommissionAccrual();
        accrual.agent = agent;
        accrual.ticket = ticket;
        accrual.sessionId = sessionId;
        accrual.commissionRule = commissionRule;
        accrual.commissionRuleVersion = commissionRule.getVersionNumber();
        accrual.saleAmount = saleAmount;
        accrual.commissionAmount = commissionAmount;
        accrual.status = CommissionAccrualStatus.ACCRUED;
        accrual.operationId = operationId;
        return accrual;
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

    public Agent getAgent() {
        return agent;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public CommissionRule getCommissionRule() {
        return commissionRule;
    }

    public int getCommissionRuleVersion() {
        return commissionRuleVersion;
    }

    public BigDecimal getSaleAmount() {
        return saleAmount;
    }

    public BigDecimal getCommissionAmount() {
        return commissionAmount;
    }

    public CommissionAccrualStatus getStatus() {
        return status;
    }

    public void setStatus(CommissionAccrualStatus status) {
        this.status = status;
    }

    public String getOperationId() {
        return operationId;
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