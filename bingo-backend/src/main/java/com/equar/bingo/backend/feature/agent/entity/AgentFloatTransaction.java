package com.equar.bingo.backend.feature.agent.entity;

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
@Table(name = "agent_float_transactions")
public class AgentFloatTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_float_id", nullable = false)
    private AgentFloat agentFloat;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 50)
    private AgentFloatTransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "available_before", nullable = false, precision = 19, scale = 2)
    private BigDecimal availableBefore;

    @Column(name = "available_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal availableAfter;

    @Column(name = "clearing_before", nullable = false, precision = 19, scale = 2)
    private BigDecimal clearingBefore;

    @Column(name = "clearing_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal clearingAfter;

    @Column(name = "related_entity_type", nullable = false, length = 80)
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    @Column(name = "operation_id", nullable = false, length = 36, unique = true)
    private String operationId;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentFloatTransaction() {
    }

    public static AgentFloatTransaction create(AgentFloat agentFloat,
            AgentFloatTransactionType transactionType,
            BigDecimal amount,
            BigDecimal availableBefore,
            BigDecimal availableAfter,
            BigDecimal clearingBefore,
            BigDecimal clearingAfter,
            String relatedEntityType,
            Long relatedEntityId,
            String operationId,
            String note) {
        AgentFloatTransaction transaction = new AgentFloatTransaction();
        transaction.agentFloat = agentFloat;
        transaction.transactionType = transactionType;
        transaction.amount = amount;
        transaction.availableBefore = availableBefore;
        transaction.availableAfter = availableAfter;
        transaction.clearingBefore = clearingBefore;
        transaction.clearingAfter = clearingAfter;
        transaction.relatedEntityType = relatedEntityType;
        transaction.relatedEntityId = relatedEntityId;
        transaction.operationId = operationId;
        transaction.note = note;
        return transaction;
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

    public AgentFloat getAgentFloat() {
        return agentFloat;
    }

    public AgentFloatTransactionType getTransactionType() {
        return transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getAvailableBefore() {
        return availableBefore;
    }

    public BigDecimal getAvailableAfter() {
        return availableAfter;
    }

    public BigDecimal getClearingBefore() {
        return clearingBefore;
    }

    public BigDecimal getClearingAfter() {
        return clearingAfter;
    }

    public String getRelatedEntityType() {
        return relatedEntityType;
    }

    public Long getRelatedEntityId() {
        return relatedEntityId;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}