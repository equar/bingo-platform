package com.bakaroo.bingo.backend.feature.game.entity;

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
import jakarta.persistence.Version;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bingo_sessions", uniqueConstraints = @UniqueConstraint(
        name = "uk_bingo_sessions_room_schedule", columnNames = { "room_id", "scheduled_start_at" }))
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private BingoRoom room;

    @Column(name = "session_name", nullable = false, length = 120)
    private String sessionName;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 30)
    private GameSessionState state;

    @Column(name = "scheduled_start_at", nullable = false)
    private Instant scheduledStartAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "ticket_price_snapshot", nullable = false, precision = 19, scale = 2)
    private BigDecimal ticketPriceSnapshot;

    @Column(name = "currency_snapshot", nullable = false, length = 3)
    private String currencySnapshot;

    @Column(name = "session_capacity_snapshot", nullable = false)
    private int sessionCapacitySnapshot;

    @Column(name = "max_tickets_per_player_snapshot", nullable = false)
    private int maxTicketsPerPlayerSnapshot;

    @Column(name = "ticket_sales_close_on_session_start", nullable = false)
    private boolean ticketSalesCloseOnSessionStart;

    @Column(name = "allow_multiple_winners_per_prize", nullable = false)
    private boolean allowMultipleWinnersPerPrize;

    @Column(name = "patterns_snapshot_json", nullable = false, columnDefinition = "text")
    private String patternsSnapshotJson;

    @Column(name = "prizes_snapshot_json", nullable = false, columnDefinition = "text")
    private String prizesSnapshotJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public GameSession() {
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

    public BingoRoom getRoom() {
        return room;
    }

    public void setRoom(BingoRoom room) {
        this.room = room;
    }

    public String getSessionName() {
        return sessionName;
    }

    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }

    public GameSessionState getState() {
        return state;
    }

    public void setState(GameSessionState state) {
        this.state = state;
    }

    public Instant getScheduledStartAt() {
        return scheduledStartAt;
    }

    public void setScheduledStartAt(Instant scheduledStartAt) {
        this.scheduledStartAt = scheduledStartAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public BigDecimal getTicketPriceSnapshot() {
        return ticketPriceSnapshot;
    }

    public void setTicketPriceSnapshot(BigDecimal ticketPriceSnapshot) {
        this.ticketPriceSnapshot = ticketPriceSnapshot;
    }

    public String getCurrencySnapshot() {
        return currencySnapshot;
    }

    public void setCurrencySnapshot(String currencySnapshot) {
        this.currencySnapshot = currencySnapshot;
    }

    public int getSessionCapacitySnapshot() {
        return sessionCapacitySnapshot;
    }

    public void setSessionCapacitySnapshot(int sessionCapacitySnapshot) {
        this.sessionCapacitySnapshot = sessionCapacitySnapshot;
    }

    public int getMaxTicketsPerPlayerSnapshot() {
        return maxTicketsPerPlayerSnapshot;
    }

    public void setMaxTicketsPerPlayerSnapshot(int maxTicketsPerPlayerSnapshot) {
        this.maxTicketsPerPlayerSnapshot = maxTicketsPerPlayerSnapshot;
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

    public String getPatternsSnapshotJson() {
        return patternsSnapshotJson;
    }

    public void setPatternsSnapshotJson(String patternsSnapshotJson) {
        this.patternsSnapshotJson = patternsSnapshotJson;
    }

    public String getPrizesSnapshotJson() {
        return prizesSnapshotJson;
    }

    public void setPrizesSnapshotJson(String prizesSnapshotJson) {
        this.prizesSnapshotJson = prizesSnapshotJson;
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
