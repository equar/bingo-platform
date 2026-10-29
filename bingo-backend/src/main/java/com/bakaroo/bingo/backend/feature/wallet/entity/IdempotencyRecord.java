package com.bakaroo.bingo.backend.feature.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

    @Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "idempotency_key", nullable = false, length = 80)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_body", nullable = false, columnDefinition = "longtext")
    private String responseBody;

    @Column(name = "operation_id", nullable = false, length = 36)
    private String operationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IdempotencyRecord() {
    }

    public static IdempotencyRecord create(Long actorId,
            String idempotencyKey,
            String requestHash,
            int responseStatus,
            String responseBody,
            String operationId,
            Instant createdAt) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.actorId = actorId;
        record.idempotencyKey = idempotencyKey;
        record.requestHash = requestHash;
        record.responseStatus = responseStatus;
        record.responseBody = responseBody;
        record.operationId = operationId;
        record.createdAt = createdAt;
        record.updatedAt = createdAt;
        return record;
    }

    public Long getId() {
        return id;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getOperationId() {
        return operationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}