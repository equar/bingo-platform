package com.bakaroo.bingo.backend.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.bakaroo.bingo.backend.common.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesApiExceptionsAndFallbacks() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        ApiErrorResponse apiError = handler.handleApiException(new BadRequestException("BAD_REQUEST", "bad request"), request)
                .getBody();
        assertThat(apiError.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(apiError.code()).isEqualTo("BAD_REQUEST");

        ApiErrorResponse badCredentials = handler.handleBadCredentials(new BadCredentialsException("nope"), request)
                .getBody();
        assertThat(badCredentials.status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());

        ApiErrorResponse accessDenied = handler.handleAccessDenied(new AccessDeniedException("forbidden"), request)
                .getBody();
        assertThat(accessDenied.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(accessDenied.code()).isEqualTo("ACCESS_DENIED");

        ApiErrorResponse fallback = handler.handleFallback(new RuntimeException("boom"), request).getBody();
        assertThat(fallback.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    @Test
    void handlesMissingResourcesAsNotFound() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/missing");
        ApiErrorResponse notFound = handler.handleNoResourceFound(
                org.mockito.Mockito.mock(NoResourceFoundException.class), request).getBody();

        assertThat(notFound.status()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(notFound.code()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void logsUnexpectedExceptionOnceWithCorrelationFieldsAndSafeResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/wallet/transfers/25");
        request.setAttribute("X-Correlation-Id", "request-42");
        MDC.put("correlationId", "request-42");
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            ApiErrorResponse fallback = handler.handleFallback(new RuntimeException("private internal detail"), request)
                    .getBody();

            assertThat(fallback.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
            assertThat(fallback.message()).doesNotContain("private internal detail");
            assertThat(request.getAttribute(com.bakaroo.bingo.backend.common.api.RequestLogSupport.EXCEPTION_LOGGED_ATTRIBUTE))
                    .isEqualTo(true);
            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.getFirst();
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getMDCPropertyMap()).containsEntry("correlationId", "request-42");
            assertThat(event.getKeyValuePairs()).anySatisfy(pair -> {
                assertThat(pair.key).isEqualTo("route");
                assertThat(pair.value).isEqualTo("/api/v1/wallet/transfers/{id}");
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            MDC.remove("correlationId");
        }
    }
}
