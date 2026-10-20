package com.equar.bingo.backend.feature.admin.dto;

import java.time.Instant;

public record AdminAuditLogSummaryResponse(
        Long id,
        Long actorUserId,
        String action,
        String targetType,
        Long targetId,
        String reason,
        String operationId,
        Instant createdAt) {
}
