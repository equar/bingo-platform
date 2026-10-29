package com.bakaroo.bingo.backend.feature.auth.security;

import com.bakaroo.bingo.backend.config.AuthenticationThrottleProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationClientAddressResolver {

    private final Set<String> trustedProxyAddresses;

    public AuthenticationClientAddressResolver(AuthenticationThrottleProperties properties) {
        this.trustedProxyAddresses = properties.trustedProxyAddresses().stream()
                .map(String::trim)
                .filter(address -> !address.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!trustedProxyAddresses.contains(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedAddress = request.getHeader("X-Real-IP");
        return forwardedAddress == null || forwardedAddress.isBlank()
                ? remoteAddress
                : forwardedAddress.trim();
    }
}
