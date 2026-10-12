package com.equar.bingo.backend.feature.agent.entity;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "bingo_agents")
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AgentStatus status;

    @Column(name = "city", nullable = false, length = 80)
    private String city;

    @Column(name = "territory", nullable = false, length = 120)
    private String territory;

    @Column(name = "id_document_type", nullable = false, length = 40)
    private String idDocumentType;

    @Column(name = "id_document_number", nullable = false, length = 80)
    private String idDocumentNumber;

    @Column(name = "id_document_front_reference", length = 200)
    private String idDocumentFrontReference;

    @Column(name = "id_document_back_reference", length = 200)
    private String idDocumentBackReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Agent() {
    }

    public static Agent create(User user, AgentStatus status, String city, String territory, String idDocumentType,
            String idDocumentNumber, String idDocumentFrontReference, String idDocumentBackReference) {
        Agent agent = new Agent();
        agent.user = user;
        agent.status = status;
        agent.city = city;
        agent.territory = territory;
        agent.idDocumentType = idDocumentType;
        agent.idDocumentNumber = idDocumentNumber;
        agent.idDocumentFrontReference = idDocumentFrontReference;
        agent.idDocumentBackReference = idDocumentBackReference;
        return agent;
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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public AgentStatus getStatus() {
        return status;
    }

    public void setStatus(AgentStatus status) {
        this.status = status;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getTerritory() {
        return territory;
    }

    public void setTerritory(String territory) {
        this.territory = territory;
    }

    public String getIdDocumentType() {
        return idDocumentType;
    }

    public void setIdDocumentType(String idDocumentType) {
        this.idDocumentType = idDocumentType;
    }

    public String getIdDocumentNumber() {
        return idDocumentNumber;
    }

    public void setIdDocumentNumber(String idDocumentNumber) {
        this.idDocumentNumber = idDocumentNumber;
    }

    public String getIdDocumentFrontReference() {
        return idDocumentFrontReference;
    }

    public void setIdDocumentFrontReference(String idDocumentFrontReference) {
        this.idDocumentFrontReference = idDocumentFrontReference;
    }

    public String getIdDocumentBackReference() {
        return idDocumentBackReference;
    }

    public void setIdDocumentBackReference(String idDocumentBackReference) {
        this.idDocumentBackReference = idDocumentBackReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}