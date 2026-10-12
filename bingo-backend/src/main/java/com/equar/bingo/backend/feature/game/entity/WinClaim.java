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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "bingo_win_claims")
public class WinClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claimant_id", nullable = false)
    private User claimant;

    @Enumerated(EnumType.STRING)
    @Column(name = "claimed_pattern", nullable = false, length = 20)
    private BingoPatternType claimedPattern;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WinClaimStatus status;

    @Column(name = "server_validation_passed", nullable = false)
    private boolean serverValidationPassed;

    @Column(name = "server_validation_code", length = 80)
    private String serverValidationCode;

    @Column(name = "server_validation_message", length = 500)
    private String serverValidationMessage;

    @Column(name = "draw_sequence_snapshot", nullable = false)
    private int drawSequenceSnapshot;

    @Column(name = "called_numbers_snapshot_json", nullable = false, columnDefinition = "text")
    private String calledNumbersSnapshotJson;

    @Column(name = "evidence_json", nullable = false, columnDefinition = "text")
    private String evidenceJson;

    @Column(name = "operation_id", nullable = false, length = 36, unique = true)
    private String operationId;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @OneToOne(mappedBy = "winClaim", fetch = FetchType.LAZY)
    private PrizeEntitlement entitlement;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public WinClaim() {
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

    public Ticket getTicket() {
        return ticket;
    }

    public void setTicket(Ticket ticket) {
        this.ticket = ticket;
    }

    public GameSession getSession() {
        return session;
    }

    public void setSession(GameSession session) {
        this.session = session;
    }

    public User getClaimant() {
        return claimant;
    }

    public void setClaimant(User claimant) {
        this.claimant = claimant;
    }

    public BingoPatternType getClaimedPattern() {
        return claimedPattern;
    }

    public void setClaimedPattern(BingoPatternType claimedPattern) {
        this.claimedPattern = claimedPattern;
    }

    public WinClaimStatus getStatus() {
        return status;
    }

    public void setStatus(WinClaimStatus status) {
        this.status = status;
    }

    public boolean isServerValidationPassed() {
        return serverValidationPassed;
    }

    public void setServerValidationPassed(boolean serverValidationPassed) {
        this.serverValidationPassed = serverValidationPassed;
    }

    public String getServerValidationCode() {
        return serverValidationCode;
    }

    public void setServerValidationCode(String serverValidationCode) {
        this.serverValidationCode = serverValidationCode;
    }

    public String getServerValidationMessage() {
        return serverValidationMessage;
    }

    public void setServerValidationMessage(String serverValidationMessage) {
        this.serverValidationMessage = serverValidationMessage;
    }

    public int getDrawSequenceSnapshot() {
        return drawSequenceSnapshot;
    }

    public void setDrawSequenceSnapshot(int drawSequenceSnapshot) {
        this.drawSequenceSnapshot = drawSequenceSnapshot;
    }

    public String getCalledNumbersSnapshotJson() {
        return calledNumbersSnapshotJson;
    }

    public void setCalledNumbersSnapshotJson(String calledNumbersSnapshotJson) {
        this.calledNumbersSnapshotJson = calledNumbersSnapshotJson;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public void setEvidenceJson(String evidenceJson) {
        this.evidenceJson = evidenceJson;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
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

    public PrizeEntitlement getEntitlement() {
        return entitlement;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}