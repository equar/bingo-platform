package com.bakaroo.bingo.backend.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void acceptsSafeIncomingIdReturnsItAndRestoresPriorMdcValue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader(ApiErrorResponseWriter.CORRELATION_ID_HEADER, "client-req_42:a.b");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put(CorrelationIdFilter.MDC_KEY, "outer-context");

        filter.doFilter(request, response, (currentRequest, currentResponse) -> {
            assertEquals("client-req_42:a.b", MDC.get(CorrelationIdFilter.MDC_KEY));
        });

        assertEquals("client-req_42:a.b", response.getHeader(ApiErrorResponseWriter.CORRELATION_ID_HEADER));
        assertEquals("outer-context", MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    @Test
    void replacesUnsafeIdAndKeepsFinancialIdempotencySeparate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/wallet/transfers");
        request.addHeader(ApiErrorResponseWriter.CORRELATION_ID_HEADER, "bad\r\nInjected: true");
        request.addHeader("Idempotency-Key", "financial-operation-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (currentRequest, currentResponse) -> {
            String generated = MDC.get(CorrelationIdFilter.MDC_KEY);
            assertThat(generated).matches("[0-9a-f-]{36}");
            assertThat(generated).isNotEqualTo("financial-operation-key");
        });

        String generated = response.getHeader(ApiErrorResponseWriter.CORRELATION_ID_HEADER);
        assertThat(generated).matches("[0-9a-f-]{36}");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void concurrentRequestsKeepTheirOwnCorrelationIdsAndCleanUpMdc() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var bothInsideChain = new CountDownLatch(2);
        try {
            var first = executor.submit(() -> runConcurrentRequest("request-A", bothInsideChain));
            var second = executor.submit(() -> runConcurrentRequest("request-B", bothInsideChain));

            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("request-A");
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("request-B");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void errorResponseKeepsCorrelationHeaderAndBodyAndStillCleansMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/wallet/transfers");
        request.addHeader(ApiErrorResponseWriter.CORRELATION_ID_HEADER, "failed-request-27");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper().findAndRegisterModules());

        filter.doFilter(request, response, (currentRequest, currentResponse) -> {
            assertEquals("failed-request-27", MDC.get(CorrelationIdFilter.MDC_KEY));
            writer.write((MockHttpServletResponse) currentResponse, 500, "INTERNAL_ERROR", "Request failed",
                    "/api/v1/wallet/transfers", ApiErrorResponseWriter.resolveCorrelationId(
                            (MockHttpServletRequest) currentRequest));
        });

        assertEquals("failed-request-27", response.getHeader(ApiErrorResponseWriter.CORRELATION_ID_HEADER));
        assertThat(response.getContentAsString()).contains("failed-request-27").doesNotContain("token");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    private String runConcurrentRequest(String correlationId, CountDownLatch bothInsideChain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader(ApiErrorResponseWriter.CORRELATION_ID_HEADER, correlationId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (currentRequest, currentResponse) -> {
            bothInsideChain.countDown();
            try {
                assertThat(bothInsideChain.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new jakarta.servlet.ServletException(exception);
            }
            assertEquals(correlationId, MDC.get(CorrelationIdFilter.MDC_KEY));
        });
        assertEquals(correlationId, response.getHeader(ApiErrorResponseWriter.CORRELATION_ID_HEADER));
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
        return correlationId;
    }
}
