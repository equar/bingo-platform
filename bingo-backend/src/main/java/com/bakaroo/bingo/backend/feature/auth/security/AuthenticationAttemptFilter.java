package com.bakaroo.bingo.backend.feature.auth.security;

import com.bakaroo.bingo.backend.common.api.ApiErrorResponseWriter;
import com.bakaroo.bingo.backend.common.exception.TooManyRequestsException;
import com.bakaroo.bingo.backend.feature.auth.service.AuthenticationAttemptAuditService;
import com.bakaroo.bingo.backend.feature.auth.service.AuthenticationAttemptThrottleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthenticationAttemptFilter extends OncePerRequestFilter {

    private static final Set<String> LOGIN_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/admin/login",
            "/api/v1/agent/login");

    private final AuthenticationAttemptThrottleService throttleService;
    private final AuthenticationAttemptAuditService auditService;
    private final AuthenticationClientAddressResolver addressResolver;
    private final ApiErrorResponseWriter errorWriter;

    public AuthenticationAttemptFilter(
            AuthenticationAttemptThrottleService throttleService,
            AuthenticationAttemptAuditService auditService,
            AuthenticationClientAddressResolver addressResolver,
            ObjectMapper objectMapper) {
        this.throttleService = throttleService;
        this.auditService = auditService;
        this.addressResolver = addressResolver;
        this.errorWriter = new ApiErrorResponseWriter(objectMapper);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LOGIN_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String clientAddress = addressResolver.resolve(request);
        try {
            throttleService.requireNetworkAllowed(clientAddress);
        } catch (TooManyRequestsException exception) {
            auditService.auditNetworkRateLimit(request.getRequestURI());
            errorWriter.write(
                    response,
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "AUTH_RATE_LIMITED",
                    "Too many authentication attempts. Try again later.",
                    request.getRequestURI(),
                    ApiErrorResponseWriter.resolveCorrelationId(request));
            return;
        }
        filterChain.doFilter(request, response);
        if (response.getStatus() >= 400 && response.getStatus() < 500
                && response.getStatus() != HttpStatus.TOO_MANY_REQUESTS.value()) {
            throttleService.recordNetworkFailure(clientAddress);
        }
    }
}
