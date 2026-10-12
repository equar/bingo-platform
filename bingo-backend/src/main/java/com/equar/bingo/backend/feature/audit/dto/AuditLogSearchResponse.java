package com.equar.bingo.backend.feature.audit.dto;

import java.time.Instant;

public record AuditLogSearchResponse(
        Long id,
        Long actorUserId,
        String action,
        String targetType,
        Long targetId,
        String reason,
        String operationId,
        String metadataJson,
        Instant createdAt) {
}