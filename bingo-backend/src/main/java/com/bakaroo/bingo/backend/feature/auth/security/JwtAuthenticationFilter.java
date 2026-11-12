package com.bakaroo.bingo.backend.feature.auth.security;

import com.bakaroo.bingo.backend.common.api.ApiErrorResponseWriter;
import com.bakaroo.bingo.backend.common.exception.AccountSuspendedException;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserDetailsService;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.auth.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.jsonwebtoken.JwtException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final BingoUserDetailsService userDetailsService;
    private final ApiErrorResponseWriter errorWriter;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            BingoUserDetailsService userDetailsService,
            ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.errorWriter = new ApiErrorResponseWriter(objectMapper);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            BingoUserPrincipal authenticatedPrincipal = null;
            boolean suspended = false;
            try {
                Long userId = jwtService.extractUserId(token);
                BingoUserPrincipal principal = userDetailsService.loadById(userId);
                boolean validToken = jwtService.isTokenValidForUser(token, principal.getUser().getId());
                suspended = validToken && principal.getUser().getStatus() == AccountStatus.SUSPENDED;
                if (validToken && principal.isEnabled()
                        && principal.isAccountNonLocked()) {
                    authenticatedPrincipal = principal;
                }
            } catch (JwtException | UsernameNotFoundException | IllegalArgumentException expectedFailure) {
                log.debug("JWT authentication rejected failureType={}",
                        expectedFailure.getClass().getSimpleName());
                SecurityContextHolder.clearContext();
            } catch (Exception failure) {
                log.error("JWT authentication lookup failed failureType={}",
                        failure.getClass().getSimpleName(), failure);
                SecurityContextHolder.clearContext();
            }

            if (suspended) {
                SecurityContextHolder.clearContext();
                errorWriter.write(response, HttpStatus.FORBIDDEN.value(),
                        AccountSuspendedException.CODE, AccountSuspendedException.MESSAGE,
                        request.getRequestURI(), ApiErrorResponseWriter.resolveCorrelationId(request));
                return;
            }

            if (authenticatedPrincipal != null) {
                if (authenticatedPrincipal.getUser().isMustChangePassword()
                        && !isPasswordChangeOrProfileRequest(request)) {
                    errorWriter.write(
                            response,
                            HttpStatus.FORBIDDEN.value(),
                            "PASSWORD_CHANGE_REQUIRED",
                            "Change your temporary password before continuing",
                            request.getRequestURI(),
                            ApiErrorResponseWriter.resolveCorrelationId(request));
                    return;
                }

                UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                        authenticatedPrincipal,
                        null,
                        authenticatedPrincipal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isPasswordChangeOrProfileRequest(HttpServletRequest request) {
        // The admin route guard refreshes identity before rendering the mandatory password form.
        // Only this read-only identity endpoint and the password update are available at first login.
        return ("PUT".equals(request.getMethod())
                && "/api/v1/admin/me/password".equals(request.getRequestURI()))
                || ("GET".equals(request.getMethod())
                && "/api/v1/auth/me".equals(request.getRequestURI()));
    }
}
