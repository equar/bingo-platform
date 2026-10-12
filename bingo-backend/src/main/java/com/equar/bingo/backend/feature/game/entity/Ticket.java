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
import java.time.Instant;

@Entity
@Table(name = "bingo_tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "public_ticket_code", nullable = false, length = 24, unique = true)
    private String publicTicketCode;

    @Column(name = "operation_id", nullable = false, length = 36, unique = true)
    private String operationId;

    @Column(name = "grid_json", nullable = false, columnDefinition = "text")
    private String gridJson;

    @Column(name = "purchased_at", nullable = false)
    private Instant purchasedAt;

    public Ticket() {
    }

    @PrePersist
    void prePersist() {
        if (purchasedAt == null) {
            purchasedAt = Instant.now();
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

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getPublicTicketCode() {
        return publicTicketCode;
    }

    public void setPublicTicketCode(String publicTicketCode) {
        this.publicTicketCode = publicTicketCode;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getGridJson() {
        return gridJson;
    }

    public void setGridJson(String gridJson) {
        this.gridJson = gridJson;
    }

    public Instant getPurchasedAt() {
        return purchasedAt;
    }
}