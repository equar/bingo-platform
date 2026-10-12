package com.equar.bingo.backend.feature.game.entity;

import com.equar.bingo.backend.feature.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "bingo_draw_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_bingo_draw_events_session_sequence", columnNames = { "session_id",
                "sequence_no" }),
        @UniqueConstraint(name = "uk_bingo_draw_events_session_number", columnNames = { "session_id", "number_drawn" }),
        @UniqueConstraint(name = "uk_bingo_draw_events_operation", columnNames = "operation_id")
})
public class DrawEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "number_drawn", nullable = false)
    private int numberDrawn;

    @Column(name = "operation_id", nullable = false, length = 36)
    private String operationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drawn_by_user_id")
    private User drawnBy;

    @Column(name = "drawn_at", nullable = false)
    private Instant drawnAt;

    public DrawEvent() {
    }

    @PrePersist
    void prePersist() {
        if (drawnAt == null) {
            drawnAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public GameSession getSession() {
        return session;
    }

    public void setSession(GameSession session) {
        this.session = session;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(int sequenceNo) {
        this.sequenceNo = sequenceNo;
    }

    public int getNumberDrawn() {
        return numberDrawn;
    }

    public void setNumberDrawn(int numberDrawn) {
        this.numberDrawn = numberDrawn;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public User getDrawnBy() {
        return drawnBy;
    }

    public void setDrawnBy(User drawnBy) {
        this.drawnBy = drawnBy;
    }

    public Instant getDrawnAt() {
        return drawnAt;
    }

    public void setDrawnAt(Instant drawnAt) {
        this.drawnAt = drawnAt;
    }
}