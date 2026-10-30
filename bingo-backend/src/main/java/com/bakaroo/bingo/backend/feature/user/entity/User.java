package com.bakaroo.bingo.backend.feature.user.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_normalized_phone", columnNames = "normalized_phone"),
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "normalized_phone", nullable = false, length = 32)
    private String normalizedPhone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "temporary_password_expires_at")
    private Instant temporaryPasswordExpiresAt;

    @Column(name = "account_activation_token_hash", length = 64)
    private String accountActivationTokenHash;

    @Column(name = "account_activation_expires_at")
    private Instant accountActivationExpiresAt;

    @Column(name = "agent_pin_hash", length = 100)
    private String agentPinHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Set<UserRole> roles = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public static User create(String normalizedPhone, String displayName, String passwordHash, AccountStatus status,
            Set<UserRole> roles, Instant now) {
        User user = new User();
        user.normalizedPhone = normalizedPhone;
        user.displayName = displayName;
        user.passwordHash = passwordHash;
        user.status = status;
        user.roles = new LinkedHashSet<>(roles);
        user.createdAt = now;
        user.updatedAt = now;
        return user;
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

    public String getNormalizedPhone() {
        return normalizedPhone;
    }

    public void setNormalizedPhone(String normalizedPhone) {
        this.normalizedPhone = normalizedPhone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public Instant getTemporaryPasswordExpiresAt() {
        return temporaryPasswordExpiresAt;
    }

    public String getAccountActivationTokenHash() {
        return accountActivationTokenHash;
    }

    public Instant getAccountActivationExpiresAt() {
        return accountActivationExpiresAt;
    }

    public boolean isAccountActivationExpired(Instant now) {
        return accountActivationExpiresAt == null || !accountActivationExpiresAt.isAfter(now);
    }

    public boolean isTemporaryPasswordExpired(Instant now) {
        return temporaryPasswordExpiresAt != null && !temporaryPasswordExpiresAt.isAfter(now);
    }

    public void requirePasswordChangeUntil(Instant expiresAt) {
        mustChangePassword = true;
        temporaryPasswordExpiresAt = expiresAt;
        accountActivationTokenHash = null;
        accountActivationExpiresAt = null;
    }

    public void requireAccountActivation(String tokenHash, Instant expiresAt) {
        mustChangePassword = true;
        temporaryPasswordExpiresAt = null;
        accountActivationTokenHash = tokenHash;
        accountActivationExpiresAt = expiresAt;
    }

    public void completePasswordChange() {
        mustChangePassword = false;
        temporaryPasswordExpiresAt = null;
        accountActivationTokenHash = null;
        accountActivationExpiresAt = null;
    }

    public String getAgentPinHash() {
        return agentPinHash;
    }

    public void setAgentPinHash(String agentPinHash) {
        this.agentPinHash = agentPinHash;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public Set<UserRole> getRoles() {
        return roles;
    }

    public void setRoles(Set<UserRole> roles) {
        this.roles = roles;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean hasRole(UserRole role) {
        return roles.contains(role);
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }
}