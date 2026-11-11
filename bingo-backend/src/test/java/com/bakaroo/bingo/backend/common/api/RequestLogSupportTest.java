package com.bakaroo.bingo.backend.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestLogSupportTest {

    @Test
    void routeLogOmitsQueryAndReplacesPersonalOrUntrustedSegments() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/123");
        request.setQueryString("token=do-not-log");

        assertThat(RequestLogSupport.safeRoute(request)).isEqualTo("/api/v1/users/{id}");

        request.setRequestURI("/api/v1/users/person@example.com/hello%0d%0aInjected");
        assertThat(RequestLogSupport.safeRoute(request)).isEqualTo("/api/v1/users/{id}/hello_0d_0aInjected");

        request.setRequestURI("/api/v1/auth/activation/eyJhbGciOiJIUzI1NiJ9.secret-token-segment");
        assertThat(RequestLogSupport.safeRoute(request)).isEqualTo("/api/v1/auth/activation/{id}");
    }

    @Test
    void successfulBusinessRequestsAreVisibleAtProductionInfoLevel() {
        assertThat(RequestCompletionLoggingFilter.levelFor(new MockHttpServletRequest("GET", "/api/v1/me"), 200))
                .isEqualTo(Level.INFO);
        assertThat(RequestCompletionLoggingFilter.levelFor(new MockHttpServletRequest("HEAD", "/api/v1/me"), 200))
                .isEqualTo(Level.INFO);
        assertThat(RequestCompletionLoggingFilter.levelFor(new MockHttpServletRequest("OPTIONS", "/api/v1/me"), 204))
                .isEqualTo(Level.DEBUG);
        assertThat(RequestCompletionLoggingFilter.levelFor(new MockHttpServletRequest("GET", "/api/v1/me"), 403))
                .isEqualTo(Level.WARN);
        assertThat(RequestCompletionLoggingFilter.levelFor(new MockHttpServletRequest("GET", "/api/v1/me"), 500))
                .isEqualTo(Level.ERROR);
    }
}
