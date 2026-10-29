package com.bakaroo.bingo.backend.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.bakaroo.bingo.backend.common.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;

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
}
