package com.equar.bingo.backend.feature.support.entity;

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
@Table(name = "support_messages")
public class SupportMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private SupportTicket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id", nullable = false)
    private User author;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "operation_id", nullable = false, unique = true, length = 36)
    private String operationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SupportMessage() {
    }

    public static SupportMessage create(SupportTicket ticket, User author, String body, String operationId) {
        SupportMessage message = new SupportMessage();
        message.ticket = ticket;
        message.author = author;
        message.body = body;
        message.operationId = operationId;
        return message;
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

    public SupportTicket getTicket() {
        return ticket;
    }

    public User getAuthor() {
        return author;
    }

    public String getBody() {
        return body;
    }

    public String getOperationId() {
        return operationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}