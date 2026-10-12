package com.equar.bingo.backend.feature.game.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bingo_rooms", uniqueConstraints = @UniqueConstraint(name = "uk_bingo_rooms_name", columnNames = "name"))
public class BingoRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "ticket_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal ticketPrice;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "timezone", nullable = false, length = 80)
    private String timezone;

    @Column(name = "schedule_rule", nullable = false, length = 500)
    private String scheduleRule;

    @Column(name = "session_capacity", nullable = false)
    private int sessionCapacity;

    @Column(name = "max_tickets_per_player", nullable = false)
    private int maxTicketsPerPlayer;

    @Column(name = "ticket_sales_close_on_session_start", nullable = false)
    private boolean ticketSalesCloseOnSessionStart;

    @Column(name = "allow_multiple_winners_per_prize", nullable = false)
    private boolean allowMultipleWinnersPerPrize;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "configured_patterns_json", nullable = false, columnDefinition = "text")
    private String configuredPatternsJson;

    @Column(name = "configured_prizes_json", nullable = false, columnDefinition = "text")
    private String configuredPrizesJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public BingoRoom() {
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(BigDecimal ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getScheduleRule() {
        return scheduleRule;
    }

    public void setScheduleRule(String scheduleRule) {
        this.scheduleRule = scheduleRule;
    }

    public int getSessionCapacity() {
        return sessionCapacity;
    }

    public void setSessionCapacity(int sessionCapacity) {
        this.sessionCapacity = sessionCapacity;
    }

    public int getMaxTicketsPerPlayer() {
        return maxTicketsPerPlayer;
    }

    public void setMaxTicketsPerPlayer(int maxTicketsPerPlayer) {
        this.maxTicketsPerPlayer = maxTicketsPerPlayer;
    }

    public boolean isTicketSalesCloseOnSessionStart() {
        return ticketSalesCloseOnSessionStart;
    }

    public void setTicketSalesCloseOnSessionStart(boolean ticketSalesCloseOnSessionStart) {
        this.ticketSalesCloseOnSessionStart = ticketSalesCloseOnSessionStart;
    }

    public boolean isAllowMultipleWinnersPerPrize() {
        return allowMultipleWinnersPerPrize;
    }

    public void setAllowMultipleWinnersPerPrize(boolean allowMultipleWinnersPerPrize) {
        this.allowMultipleWinnersPerPrize = allowMultipleWinnersPerPrize;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getConfiguredPatternsJson() {
        return configuredPatternsJson;
    }

    public void setConfiguredPatternsJson(String configuredPatternsJson) {
        this.configuredPatternsJson = configuredPatternsJson;
    }

    public String getConfiguredPrizesJson() {
        return configuredPrizesJson;
    }

    public void setConfiguredPrizesJson(String configuredPrizesJson) {
        this.configuredPrizesJson = configuredPrizesJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}