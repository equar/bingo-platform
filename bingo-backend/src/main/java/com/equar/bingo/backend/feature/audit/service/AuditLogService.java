package com.equar.bingo.backend.feature.audit.service;

import com.equar.bingo.backend.feature.audit.entity.AuditLogEntry;
import com.equar.bingo.backend.feature.audit.repository.AuditLogRepository;
import com.equar.bingo.backend.feature.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AuditLogEntry record(User actor, String action, String targetType, Long targetId, String reason,
            String operationId, Map<String, Object> metadata) {
        String resolvedOperationId = operationId == null ? UUID.randomUUID().toString() : operationId;
        String metadataJson = serialize(metadata);
        return auditLogRepository.save(AuditLogEntry.create(actor, action, targetType, targetId, reason,
                resolvedOperationId, metadataJson));
    }

    private String serialize(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize audit metadata", exception);
        }
    }
}