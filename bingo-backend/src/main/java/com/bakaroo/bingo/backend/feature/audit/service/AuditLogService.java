package com.bakaroo.bingo.backend.feature.audit.service;

import com.bakaroo.bingo.backend.feature.audit.entity.AuditLogEntry;
import com.bakaroo.bingo.backend.feature.audit.repository.AuditLogRepository;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private static final int MAX_METADATA_JSON_LENGTH = 64_000;

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AuditLogEntry record(User actor, String action, String targetType, Long targetId, String reason,
            String operationId, Map<String, Object> metadata) {
        return persist(actor, action, targetType, targetId, reason, operationId, metadata);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLogEntry recordFailure(User actor, String action, String targetType, Long targetId, String reason,
            String operationId, Map<String, Object> metadata) {
        return persist(actor, action, targetType, targetId, reason, operationId, metadata);
    }

    private AuditLogEntry persist(User actor, String action, String targetType, Long targetId, String reason,
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
            String serialized = objectMapper.writeValueAsString(metadata);
            if (serialized.length() <= MAX_METADATA_JSON_LENGTH) {
                return serialized;
            }

            Map<String, Object> compact = new LinkedHashMap<>();
            for (String key : new String[] { "schemaVersion", "outcome", "service", "method", "actor", "request" }) {
                if (metadata.containsKey(key)) {
                    compact.put(key, metadata.get(key));
                }
            }
            compact.put("captureTruncated", true);
            return objectMapper.writeValueAsString(compact);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize audit metadata", exception);
        }
    }
}
