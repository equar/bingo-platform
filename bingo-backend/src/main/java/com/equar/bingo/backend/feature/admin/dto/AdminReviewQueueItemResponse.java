package com.equar.bingo.backend.feature.admin.dto;

import java.time.Instant;

public record AdminReviewQueueItemResponse(
        Long id,
        String queueType,
        String title,
        String status,
        String priority,
        Long requesterUserId,
        Long assignedToUserId,
        Long linkedEntityId,
        String linkedEntityType,
        Instant createdAt,
        Instant updatedAt) {
}