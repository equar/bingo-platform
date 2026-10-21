package com.equar.bingo.backend.feature.auth.service;

import com.equar.bingo.backend.feature.audit.service.AuditLogService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationAttemptAuditService {

    private final AuditLogService auditLogService;

    public AuthenticationAttemptAuditService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    public void auditNetworkRateLimit(String path) {
        auditLogService.recordFailure(
                null,
                "AUTHENTICATION_RATE_LIMITED",
                "AUTHENTICATION",
                null,
                null,
                null,
                Map.of("outcome", "RATE_LIMITED", "scope", "NETWORK", "path", path));
    }
}
