package com.equar.bingo.backend.feature.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "commission_rules")
public class CommissionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_number", nullable = false, unique = true)
    private int versionNumber;

    @Column(name = "commission_rate_bps", nullable = false)
    private int commissionRateBps;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CommissionRule() {
    }

    public static CommissionRule create(int versionNumber, int commissionRateBps, boolean active,
            Instant effectiveFrom) {
        CommissionRule rule = new CommissionRule();
        rule.versionNumber = versionNumber;
        rule.commissionRateBps = commissionRateBps;
        rule.active = active;
        rule.effectiveFrom = effectiveFrom;
        return rule;
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

    public int getVersionNumber() {
        return versionNumber;
    }

    public int getCommissionRateBps() {
        return commissionRateBps;
    }

    public void setCommissionRateBps(int commissionRateBps) {
        this.commissionRateBps = commissionRateBps;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(Instant effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}