package com.equar.bingo.backend.feature.auth.service;

import com.equar.bingo.backend.common.exception.TooManyRequestsException;
import com.equar.bingo.backend.config.AuthenticationThrottleProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationAttemptThrottleService {

    private final AuthenticationAttemptBucketStore bucketStore;
    private final AuthenticationThrottleProperties properties;

    public AuthenticationAttemptThrottleService(
            AuthenticationAttemptBucketStore bucketStore,
            AuthenticationThrottleProperties properties) {
        this.bucketStore = bucketStore;
        this.properties = properties;
    }

    public void requireAccountAllowed(String accountType, String normalizedIdentifier) {
        requireAllowed(
                "account:" + accountType + ":" + normalizedIdentifier,
                properties.accountAttemptLimit(),
                properties.window(),
                properties.lockout());
    }

    public void requireAccountAllowed(
            String accountType,
            String normalizedIdentifier,
            int limit,
            Duration window) {
        requireAllowed("account:" + accountType + ":" + normalizedIdentifier, limit, window, properties.lockout());
    }

    public void requireAllowed(String key, int limit, Duration window, String code, String message) {
        if (limit < 1 || !bucketStore.recordAttempt(hash(key), limit, window, properties.lockout())) {
            throw new TooManyRequestsException(code, message);
        }
    }

    public void clear(String key) {
        bucketStore.clear(hash(key));
    }

    public void requireNetworkAllowed(String remoteAddress) {
        requireAllowed(
                "network:" + remoteAddress,
                properties.networkAttemptLimit(),
                properties.window(),
                properties.lockout());
    }

    private void requireAllowed(String key, int limit, Duration window, Duration lockout) {
        if (limit < 1 || !bucketStore.recordAttempt(hash(key), limit, window, lockout)) {
            throw new TooManyRequestsException(
                    "AUTH_RATE_LIMITED",
                    "Too many authentication attempts. Try again later.");
        }
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for authentication throttling", exception);
        }
    }
}
