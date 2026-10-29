package com.bakaroo.bingo.backend.feature.agent.dto;

import com.bakaroo.bingo.backend.feature.agent.entity.AgentStatus;
import java.time.Instant;

public record AgentProfileResponse(
        Long agentId,
        Long userId,
        String phoneNumber,
        String displayName,
        AgentStatus status,
        String city,
        String territory,
        String idDocumentType,
        String idDocumentNumber,
        String idDocumentFrontReference,
        String idDocumentBackReference,
        Instant createdAt,
        Instant updatedAt) {
}