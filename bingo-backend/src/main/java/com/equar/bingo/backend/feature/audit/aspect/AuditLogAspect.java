package com.equar.bingo.backend.feature.audit.aspect;

import com.equar.bingo.backend.common.api.ApiErrorResponseWriter;
import com.equar.bingo.backend.common.api.CorrelationIdFilter;
import com.equar.bingo.backend.feature.audit.service.AuditLogService;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);
    private static final int MAX_DEPTH = 5;
    private static final int MAX_COLLECTION_SIZE = 50;
    private static final int MAX_STRING_LENGTH = 1_000;
    private static final List<String> MUTATING_PREFIXES = List.of(
            "create", "update", "delete", "approve", "reject", "settle", "review", "assign", "submit",
            "record", "purchase", "transfer", "save", "close", "open", "disable", "enable", "reply", "add",
            "topUp", "withdraw", "deposit", "send", "upsert", "verify", "suspend", "activate", "cancel",
            "adminSuspend", "adminApprove", "register", "setAgentPin", "issue", "advance", "changePassword",
            "reset", "revoke", "login", "logout", "configure", "start", "stop");
    private static final List<String> SENSITIVE_KEY_PARTS = List.of(
            "password", "pin", "secret", "token", "credential", "authorization", "otp", "phone", "email",
            "document", "account", "bank", "card", "cvv", "code", "apikey", "accesskey", "message", "comment",
            "description", "body", "text", "address", "birth", "nationalid");

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public AuditLogAspect(AuditLogService auditLogService, ObjectMapper objectMapper, UserRepository userRepository) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    @Around("execution(public * com.equar.bingo.backend.feature..service..*(..)) && !within(com.equar.bingo.backend.feature.audit..*)")
    public Object recordMutations(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getName();
        if (!shouldAudit(methodName)) {
            return joinPoint.proceed();
        }

        Object[] arguments = joinPoint.getArgs();
        String operationId = UUID.randomUUID().toString();
        User actor = currentUser();
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable failure) {
            Map<String, Object> metadata = buildMetadata(joinPoint, signature, "FAILURE", actor, null, failure);
            try {
                auditLogService.recordFailure(actor, actionName(methodName), targetType(joinPoint, methodName, null),
                        extractTargetId(arguments, null, signature), extractReason(arguments, signature),
                        operationId, metadata);
            } catch (RuntimeException auditFailure) {
                failure.addSuppressed(auditFailure);
                log.error("Unable to record failed audit event for {}", methodName, auditFailure);
            }
            throw failure;
        }

        if (actor == null && methodName.startsWith("login")) {
            Long loggedInUserId = findId(result);
            if (loggedInUserId != null) {
                actor = userRepository.findById(loggedInUserId).orElse(null);
            }
        }
        Map<String, Object> metadata = buildMetadata(joinPoint, signature, "SUCCESS", actor, result, null);
        auditLogService.record(actor, actionName(methodName), targetType(joinPoint, methodName, result),
                extractTargetId(arguments, result, signature), extractReason(arguments, signature), operationId, metadata);
        return result;
    }

    private boolean shouldAudit(String methodName) {
        return MUTATING_PREFIXES.stream().anyMatch(methodName::startsWith);
    }

    private String actionName(String methodName) {
        return methodName.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof BingoUserPrincipal principal)) {
            return null;
        }
        return principal.getUser();
    }

    private Map<String, Object> buildMetadata(ProceedingJoinPoint joinPoint, MethodSignature signature, String outcome,
            User actor, Object result, Throwable failure) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schemaVersion", 1);
        metadata.put("outcome", outcome);
        metadata.put("service", joinPoint.getTarget().getClass().getSimpleName());
        metadata.put("method", signature.getMethod().getName());
        Map<String, Object> actorSnapshot = actorSnapshot(actor);
        if (!actorSnapshot.isEmpty()) {
            metadata.put("actor", actorSnapshot);
        }
        metadata.put("arguments", namedArguments(joinPoint.getArgs(), signature));
        if (result != null) {
            metadata.put("result", sanitize(result, 0));
        }
        if (failure != null) {
            metadata.put("errorType", failure.getClass().getSimpleName());
        }

        Map<String, Object> requestDetails = requestDetails();
        if (!requestDetails.isEmpty()) {
            metadata.put("request", requestDetails);
        }
        return metadata;
    }

    private Map<String, Object> requestDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            Object correlationId = request.getAttribute(ApiErrorResponseWriter.CORRELATION_ID_HEADER);
            String resolvedCorrelationId = correlationId instanceof String value && !value.isBlank()
                    ? value
                    : MDC.get(CorrelationIdFilter.MDC_KEY);
            if (resolvedCorrelationId != null && !resolvedCorrelationId.isBlank()) {
                details.put("correlationId", truncate(resolvedCorrelationId, 128));
            }
            details.put("httpMethod", request.getMethod());
            details.put("path", truncate(request.getRequestURI(), 512));
            details.put("clientIp", request.getRemoteAddr());
            String userAgent = request.getHeader("User-Agent");
            if (userAgent != null && !userAgent.isBlank()) {
                details.put("userAgent", truncate(userAgent, 512));
            }
        }
        return details;
    }

    private Map<String, Object> namedArguments(Object[] arguments, MethodSignature signature) {
        Map<String, Object> named = new LinkedHashMap<>();
        String[] parameterNames = signature.getParameterNames();
        for (int index = 0; index < arguments.length; index++) {
            String name = parameterNames != null && index < parameterNames.length ? parameterNames[index] : "arg" + index;
            if (isSensitiveKey(name)) {
                named.put(name, "[REDACTED]");
            } else {
                named.put(name, sanitize(arguments[index], 0));
            }
        }
        return named;
    }

    private Object sanitize(Object value, int depth) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof CharSequence text) {
            return truncate(text.toString(), MAX_STRING_LENGTH);
        }
        if (value instanceof TemporalAccessor || value instanceof java.util.Date) {
            return value.toString();
        }
        if (value instanceof byte[]) {
            return "[BINARY DATA REDACTED]";
        }
        if (depth >= MAX_DEPTH) {
            return "[MAX DEPTH]";
        }

        Object converted;
        try {
            converted = objectMapper.convertValue(value, Object.class);
        } catch (IllegalArgumentException exception) {
            return "[UNAVAILABLE: " + value.getClass().getSimpleName() + "]";
        }
        return sanitizeConverted(converted, depth + 1);
    }

    private Object sanitizeConverted(Object value, int depth) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            int captured = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (captured++ >= MAX_COLLECTION_SIZE) {
                    sanitized.put("_truncated", true);
                    break;
                }
                String key = String.valueOf(entry.getKey());
                sanitized.put(key, isSensitiveKey(key) ? "[REDACTED]" : sanitize(entry.getValue(), depth));
            }
            return sanitized;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> sanitized = new ArrayList<>();
            int captured = 0;
            for (Object item : collection) {
                if (captured++ >= MAX_COLLECTION_SIZE) {
                    sanitized.add("[TRUNCATED]");
                    break;
                }
                sanitized.add(sanitize(item, depth));
            }
            return sanitized;
        }
        if (value instanceof String text) {
            return truncate(text, MAX_STRING_LENGTH);
        }
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return truncate(String.valueOf(value), MAX_STRING_LENGTH);
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return SENSITIVE_KEY_PARTS.stream().anyMatch(normalized::contains);
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "…[TRUNCATED]";
    }

    private Long extractTargetId(Object[] arguments, Object result, MethodSignature signature) {
        Long resultId = findId(result);
        if (resultId != null) {
            return resultId;
        }

        String[] names = signature.getParameterNames();
        Long onlyNumericArgument = null;
        int numericCount = 0;
        for (int index = 0; index < arguments.length; index++) {
            if (!(arguments[index] instanceof Number number)) {
                continue;
            }
            Long value = number.longValue();
            numericCount++;
            onlyNumericArgument = value;
            String name = names != null && index < names.length ? names[index] : "";
            if (name.equalsIgnoreCase("id") || name.toLowerCase(Locale.ROOT).endsWith("id")) {
                return value;
            }
        }
        return numericCount == 1 ? onlyNumericArgument : null;
    }

    private Long findId(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                || value instanceof Enum<?>) {
            return null;
        }
        if (value instanceof Map<?, ?> map && map.get("id") instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof Map<?, ?> map) {
            for (String nestedKey : List.of("user", "body", "data", "result")) {
                Long nestedId = findId(map.get(nestedKey));
                if (nestedId != null) {
                    return nestedId;
                }
            }
        }
        for (String accessor : List.of("id", "getId")) {
            try {
                Method method = value.getClass().getMethod(accessor);
                Object id = method.invoke(value);
                if (id instanceof Number number) {
                    return number.longValue();
                }
            } catch (ReflectiveOperationException ignored) {
                // This return type does not expose an id through a public accessor.
            }
        }
        if (!(value instanceof Map<?, ?>)) {
            try {
                Object converted = objectMapper.convertValue(value, Object.class);
                if (converted instanceof Map<?, ?>) {
                    return findId(converted);
                }
            } catch (IllegalArgumentException ignored) {
                // The value is not a serializable DTO or map.
            }
        }
        return null;
    }

    private String extractReason(Object[] arguments, MethodSignature signature) {
        String[] names = signature.getParameterNames();
        if (names == null) {
            return null;
        }
        for (int index = 0; index < Math.min(arguments.length, names.length); index++) {
            String name = names[index].toLowerCase(Locale.ROOT);
            if ((name.equals("reason") || name.endsWith("reviewnote") || name.endsWith("settlementnote"))
                    && arguments[index] instanceof CharSequence reason) {
                return truncate(reason.toString(), 500);
            }
        }
        return null;
    }

    private Map<String, Object> actorSnapshot(User actor) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (actor == null) {
            return snapshot;
        }
        snapshot.put("userId", actor.getId());
        snapshot.put("displayName", actor.getDisplayName());
        snapshot.put("roles", actor.getRoles().stream().map(role -> role.name()).sorted().toList());
        return snapshot;
    }

    private String targetType(ProceedingJoinPoint joinPoint, String methodName, Object result) {
        if (methodName.startsWith("login") && findId(result) != null) {
            return "User";
        }
        Object targetResult = unwrapResult(result);
        if (targetResult != null && !(targetResult instanceof String) && !(targetResult instanceof Number)
                && !(targetResult instanceof Boolean) && !(targetResult instanceof Collection<?>)) {
            String simpleName = targetResult.getClass().getSimpleName();
            return simpleName.replaceFirst("(Response|Dto|DTO)$", "");
        }
        return joinPoint.getTarget().getClass().getSimpleName();
    }

    private Object unwrapResult(Object result) {
        Object unwrapped = result;
        for (int depth = 0; depth < 2 && unwrapped != null; depth++) {
            Object next = invokeAccessor(unwrapped, "body");
            if (next == null) {
                next = invokeAccessor(unwrapped, "getBody");
            }
            if (next == null || next == unwrapped) {
                return unwrapped;
            }
            unwrapped = next;
        }
        return unwrapped;
    }

    private Object invokeAccessor(Object value, String accessor) {
        try {
            Method method = value.getClass().getMethod(accessor);
            return method.invoke(value);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
