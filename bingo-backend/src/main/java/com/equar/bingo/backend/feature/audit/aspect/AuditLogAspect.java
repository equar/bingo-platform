package com.equar.bingo.backend.feature.audit.aspect;

import com.equar.bingo.backend.feature.audit.service.AuditLogService;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.user.entity.User;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditLogAspect {

    private static final List<String> MUTATING_PREFIXES = List.of(
            "create",
            "update",
            "delete",
            "approve",
            "reject",
            "settle",
            "review",
            "assign",
            "submit",
            "record",
            "purchase",
            "transfer",
            "save",
            "close",
            "open",
            "disable",
            "enable",
            "reply",
            "topUp",
            "withdraw",
            "deposit",
            "send",
            "upsert");

    private final AuditLogService auditLogService;

    public AuditLogAspect(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Around("execution(public * com.equar.bingo.backend.feature..service..*(..)) && !within(com.equar.bingo.backend.feature.audit..*)")
    public Object recordMutations(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getName();
        if (!shouldAudit(methodName)) {
            return result;
        }

        User actor = currentUser();
        Long targetId = extractTargetId(joinPoint.getArgs(), result);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("service", signature.getDeclaringType().getSimpleName());
        metadata.put("method", methodName);
        metadata.put("argsCount", joinPoint.getArgs().length);
        metadata.put("resultType", result == null ? null : result.getClass().getSimpleName());

        auditLogService.record(actor, methodName.toUpperCase(), signature.getDeclaringType().getSimpleName(), targetId,
                null, UUID.randomUUID().toString(), metadata);
        return result;
    }

    private boolean shouldAudit(String methodName) {
        return MUTATING_PREFIXES.stream().anyMatch(methodName::startsWith);
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof BingoUserPrincipal principal)) {
            return null;
        }
        return principal.getUser();
    }

    private Long extractTargetId(Object[] args, Object result) {
        for (Object arg : args) {
            if (arg instanceof Long value) {
                return value;
            }
            if (arg instanceof Integer value) {
                return value.longValue();
            }
        }

        if (result instanceof com.equar.bingo.backend.feature.audit.entity.AuditLogEntry auditLogEntry) {
            return auditLogEntry.getId();
        }
        return null;
    }
}