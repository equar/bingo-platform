package com.equar.bingo.backend.feature.auth.security;

import com.equar.bingo.backend.common.api.ApiErrorResponseWriter;
import com.equar.bingo.backend.feature.auth.service.BingoUserDetailsService;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.auth.service.JwtService;
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

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

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
            try {
                String subject = jwtService.extractSubject(token);
                BingoUserPrincipal principal = (BingoUserPrincipal) userDetailsService.loadUserByUsername(subject);
                if (jwtService.isTokenValid(token, principal.getUsername()) && principal.isEnabled()) {
                    authenticatedPrincipal = principal;
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }

            if (authenticatedPrincipal != null) {
                if (authenticatedPrincipal.getUser().isMustChangePassword()
                        && !("PUT".equals(request.getMethod())
                                && "/api/v1/admin/me/password".equals(request.getRequestURI()))) {
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
}