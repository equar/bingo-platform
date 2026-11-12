package com.bakaroo.bingo.backend.common.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "correlationId";
    public static final int MAX_CORRELATION_ID_LENGTH = 64;
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String incomingCorrelationId = request.getHeader(ApiErrorResponseWriter.CORRELATION_ID_HEADER);
        String correlationId = isValidCorrelationId(incomingCorrelationId)
                ? incomingCorrelationId
                : UUID.randomUUID().toString();

        request.setAttribute(ApiErrorResponseWriter.CORRELATION_ID_HEADER, correlationId);
        request.setAttribute(START_NANOS_ATTRIBUTE, System.nanoTime());
        String previousCorrelationId = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(ApiErrorResponseWriter.CORRELATION_ID_HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previousCorrelationId == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previousCorrelationId);
            }
        }
    }

    public static boolean isValidCorrelationId(String value) {
        return value != null && value.length() <= MAX_CORRELATION_ID_LENGTH
                && SAFE_CORRELATION_ID.matcher(value).matches();
    }

    public static final String START_NANOS_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".startNanos";
}
