package com.bakaroo.bingo.backend.feature.auth.security;

import com.bakaroo.bingo.backend.config.AuthenticationThrottleProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthenticationClientAddressResolverTest {

    private final AuthenticationClientAddressResolver resolver = new AuthenticationClientAddressResolver(
            new AuthenticationThrottleProperties(5, 50, Duration.ofMinutes(15), Duration.ofMinutes(15),
                    List.of("127.0.0.1", "::1")));

    @Test
    void usesForwardedAddressOnlyForTrustedProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "203.0.113.12");

        assertEquals("203.0.113.12", resolver.resolve(request));
    }

    @Test
    void ignoresForwardedAddressFromUntrustedClient() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.8");
        request.addHeader("X-Real-IP", "203.0.113.12");

        assertEquals("198.51.100.8", resolver.resolve(request));
    }

    @Test
    void fallsBackToProxyAddressWhenForwardedAddressIsBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", " ");

        assertEquals("127.0.0.1", resolver.resolve(request));
    }
}
