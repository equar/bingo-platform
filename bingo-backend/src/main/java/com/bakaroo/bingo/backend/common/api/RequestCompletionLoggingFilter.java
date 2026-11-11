package com.bakaroo.bingo.backend.common.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Logs completed API requests without logging credentials, query strings, or payloads. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestCompletionLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestCompletionLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            if (!Boolean.TRUE.equals(request.getAttribute(RequestLogSupport.EXCEPTION_LOGGED_ATTRIBUTE))) {
                request.setAttribute(RequestLogSupport.EXCEPTION_LOGGED_ATTRIBUTE, true);
                request.setAttribute(RequestLogSupport.FAILURE_LOGGED_ATTRIBUTE, true);
                int status = response.getStatus() >= 400 ? response.getStatus() : 500;
                RequestLogSupport.withRequestFields(log.atError(), request, "http_request", "failed", status)
                        .addKeyValue("failureType", exception.getClass().getSimpleName())
                        .setCause(exception)
                        .log("Unhandled request failure");
            }
            throw exception;
        } finally {
            if (!Boolean.TRUE.equals(request.getAttribute(RequestLogSupport.FAILURE_LOGGED_ATTRIBUTE))) {
                int status = response.getStatus();
                String outcome = status >= 500 ? "failed" : status >= 400 ? "rejected" : "success";
                var event = RequestLogSupport.withRequestFields(log.atLevel(levelFor(request, status)), request,
                        "http_request", outcome, status);
                event.log("HTTP request completed");
            }
        }
    }

    static org.slf4j.event.Level levelFor(HttpServletRequest request, int status) {
        if (status >= 500) return org.slf4j.event.Level.ERROR;
        if (status >= 400) return org.slf4j.event.Level.WARN;
        if ("OPTIONS".equals(request.getMethod())) return org.slf4j.event.Level.DEBUG;
        return org.slf4j.event.Level.INFO;
    }
}
