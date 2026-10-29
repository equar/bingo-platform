package com.bakaroo.bingo.backend.feature.support.entity;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "support_tickets")
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_number", nullable = false, unique = true, length = 36)
    private String ticketNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_user_id", nullable = false)
    private User requester;

    @Column(name = "subject", nullable = false, length = 180)
    private String subject;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private SupportTicketCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private SupportTicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SupportTicketStatus status;

    @Column(name = "linked_entity_type", length = 80)
    @Enumerated(EnumType.STRING)
    private SupportLinkedEntityType linkedEntityType;

    @Column(name = "linked_entity_id")
    private Long linkedEntityId;

    @Column(name = "linked_entity_reference", length = 120)
    private String linkedEntityReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id")
    private User assignedTo;

    @Column(name = "resolution_summary", length = 1000)
    private String resolutionSummary;

    @Column(name = "operation_id", nullable = false, unique = true, length = 36)
    private String operationId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SupportTicket() {
    }

    public static SupportTicket create(User requester, String subject, String description,
            SupportTicketCategory category, SupportTicketPriority priority, SupportLinkedEntityType linkedEntityType,
            Long linkedEntityId, String linkedEntityReference, String operationId) {
        SupportTicket ticket = new SupportTicket();
        ticket.requester = requester;
        ticket.subject = subject;
        ticket.description = description;
        ticket.category = category;
        ticket.priority = priority;
        ticket.status = SupportTicketStatus.OPEN;
        ticket.linkedEntityType = linkedEntityType;
        ticket.linkedEntityId = linkedEntityId;
        ticket.linkedEntityReference = linkedEntityReference;
        ticket.operationId = operationId;
        ticket.ticketNumber = "ST-" + operationId.substring(0, 8).toUpperCase();
        return ticket;
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

    public String getTicketNumber() {
        return ticketNumber;
    }

    public User getRequester() {
        return requester;
    }

    public String getSubject() {
        return subject;
    }

    public String getDescription() {
        return description;
    }

    public SupportTicketCategory getCategory() {
        return category;
    }

    public SupportTicketPriority getPriority() {
        return priority;
    }

    public void setPriority(SupportTicketPriority priority) {
        this.priority = priority;
    }

    public SupportTicketStatus getStatus() {
        return status;
    }

    public void setStatus(SupportTicketStatus status) {
        this.status = status;
    }

    public SupportLinkedEntityType getLinkedEntityType() {
        return linkedEntityType;
    }

    public Long getLinkedEntityId() {
        return linkedEntityId;
    }

    public String getLinkedEntityReference() {
        return linkedEntityReference;
    }

    public User getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(User assignedTo) {
        this.assignedTo = assignedTo;
    }

    public String getResolutionSummary() {
        return resolutionSummary;
    }

    public void setResolutionSummary(String resolutionSummary) {
        this.resolutionSummary = resolutionSummary;
    }

    public String getOperationId() {
        return operationId;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}