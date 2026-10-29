package com.bakaroo.bingo.backend.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

public class ApiErrorResponseWriter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private final ObjectMapper objectMapper;

    public ApiErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response,
            int status,
            String code,
            String message,
            String path,
            String correlationId) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), new ApiErrorResponse(
                Instant.now(),
                status,
                HttpStatus.valueOf(status).getReasonPhrase(),
                code,
                message,
                path,
                correlationId,
                List.of()));
    }

    public static String resolveCorrelationId(HttpServletRequest request) {
        Object correlationId = request.getAttribute(CORRELATION_ID_HEADER);
        if (correlationId instanceof String value && !value.isBlank()) {
            return value;
        }

        String mdcCorrelationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (mdcCorrelationId != null && !mdcCorrelationId.isBlank()) {
            return mdcCorrelationId;
        }

        String headerCorrelationId = request.getHeader(CORRELATION_ID_HEADER);
        return headerCorrelationId == null || headerCorrelationId.isBlank() ? null : headerCorrelationId;
    }
}