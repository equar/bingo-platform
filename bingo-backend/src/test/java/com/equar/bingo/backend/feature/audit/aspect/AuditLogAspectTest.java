package com.equar.bingo.backend.feature.audit.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.equar.bingo.backend.feature.audit.service.AuditLogService;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditLogAspectTest {

    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditLogAspect aspect = new AuditLogAspect(auditLogService, new ObjectMapper(), userRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsSanitizedArgumentsAndResultForSuccessfulMutation() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = MutationService.class.getMethod("updateSessionState", Long.class, ChangeRequest.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new MutationService());
        when(joinPoint.getArgs()).thenReturn(new Object[] { 91L, new ChangeRequest("CLOSED", "do-not-store") });
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[] { "sessionId", "request" });
        when(joinPoint.proceed()).thenReturn(new ChangeResult(91L, "CLOSED"));

        Object result = aspect.recordMutations(joinPoint);

        assertThat(result).isEqualTo(new ChangeResult(91L, "CLOSED"));
        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).record(isNull(), eq("UPDATE_SESSION_STATE"), eq("ChangeResult"), eq(91L), isNull(), any(),
                metadataCaptor.capture());
        Map<String, Object> metadata = metadataCaptor.getValue();
        assertThat(metadata).containsEntry("outcome", "SUCCESS").containsEntry("method", "updateSessionState");

        String json = new ObjectMapper().writeValueAsString(metadata);
        assertThat(json).contains("CLOSED").contains("password").contains("[REDACTED]");
        assertThat(json).doesNotContain("do-not-store");
    }

    @Test
    void recordsFailureOutcomeAndRethrowsTheOriginalFailure() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = MutationService.class.getMethod("deleteSession", Long.class);
        IllegalStateException failure = new IllegalStateException("operation rejected");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new MutationService());
        when(joinPoint.getArgs()).thenReturn(new Object[] { 91L });
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[] { "sessionId" });
        when(joinPoint.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> aspect.recordMutations(joinPoint)).isSameAs(failure);

        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).recordFailure(isNull(), eq("DELETE_SESSION"), eq("MutationService"), eq(91L), isNull(),
                any(), metadataCaptor.capture());
        Map<String, Object> metadata = metadataCaptor.getValue();
        assertThat(metadata).containsEntry("outcome", "FAILURE").containsEntry("errorType", "IllegalStateException");
    }

    @Test
    void attributesSuccessfulLoginToTheReturnedUserAndRedactsTheToken() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = MutationService.class.getMethod("login", LoginRequest.class);
        User actor = mock(User.class);
        when(actor.getId()).thenReturn(7L);
        when(actor.getDisplayName()).thenReturn("Admin Example");
        when(actor.getRoles()).thenReturn(java.util.Set.of(com.equar.bingo.backend.feature.user.entity.UserRole.ADMIN));
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.of(actor));
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new MutationService());
        when(joinPoint.getArgs()).thenReturn(new Object[] { new LoginRequest("admin@example.test", "hidden-password") });
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[] { "request" });
        when(joinPoint.proceed()).thenReturn(new AuthResult("hidden-token", new UserProfile(7L, "Admin Example")));

        aspect.recordMutations(joinPoint);

        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).record(eq(actor), eq("LOGIN"), eq("User"), eq(7L), isNull(), any(),
                metadataCaptor.capture());
        String json = new ObjectMapper().writeValueAsString(metadataCaptor.getValue());
        assertThat(json).contains("Admin Example").contains("[REDACTED]").doesNotContain("hidden-token")
                .doesNotContain("hidden-password");
    }

    public static class MutationService {
        public ChangeResult updateSessionState(Long sessionId, ChangeRequest request) {
            return new ChangeResult(sessionId, request.getState());
        }

        public void deleteSession(Long sessionId) {
        }

        public AuthResult login(LoginRequest request) {
            return null;
        }
    }

    public static class ChangeRequest {
        private final String state;
        private final String password;

        public ChangeRequest(String state, String password) {
            this.state = state;
            this.password = password;
        }

        public String getState() {
            return state;
        }

        public String getPassword() {
            return password;
        }
    }

    public record ChangeResult(Long id, String state) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record UserProfile(Long id, String displayName) {
    }

    public record AuthResult(String accessToken, UserProfile user) {
    }
}
