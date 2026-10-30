package com.bakaroo.bingo.backend.feature.auth.service;

import com.bakaroo.bingo.backend.common.exception.TooManyRequestsException;
import com.bakaroo.bingo.backend.config.AuthenticationThrottleProperties;
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
        requireAccountAllowed(
                accountType, normalizedIdentifier, properties.accountAttemptLimit(), properties.window());
    }

    public void requireAccountAllowed(
            String accountType,
            String normalizedIdentifier,
            int limit,
            Duration window) {
        requireNotLocked("account:" + accountType + ":" + normalizedIdentifier, limit, window);
    }

    public void recordAccountFailure(String accountType, String normalizedIdentifier) {
        recordAccountFailure(
                accountType, normalizedIdentifier, properties.accountAttemptLimit(), properties.window());
    }

    public void recordAccountFailure(
            String accountType,
            String normalizedIdentifier,
            int limit,
            Duration window) {
        recordFailure("account:" + accountType + ":" + normalizedIdentifier, limit, window);
    }

    public void requireAllowed(String key, int limit, Duration window, String code, String message) {
        if (limit < 1 || !bucketStore.recordAttempt(hash(key), limit, window, properties.lockout())) {
            throw new TooManyRequestsException(code, message);
        }
    }

    public void clear(String key) {
        bucketStore.clear(hash(key));
    }

    public void clearAccountAttempts(String accountType, String normalizedIdentifier) {
        clear("account:" + accountType + ":" + normalizedIdentifier);
    }

    public void requireNetworkAllowed(String remoteAddress) {
        requireNotLocked("network:" + remoteAddress, properties.networkAttemptLimit(), properties.window());
    }

    public void recordNetworkFailure(String remoteAddress) {
        String key = "network:" + remoteAddress;
        if (properties.networkAttemptLimit() < 1) {
            throw new IllegalStateException("Authentication network-attempt limit must be positive");
        }
        bucketStore.recordAttempt(hash(key), properties.networkAttemptLimit(), properties.window(),
                properties.lockout());
    }

    private void requireNotLocked(String key, int limit, Duration window) {
        if (limit < 1 || !bucketStore.isAllowed(hash(key), limit, window, properties.lockout())) {
            throw new TooManyRequestsException(
                    "AUTH_RATE_LIMITED",
                    "Too many authentication attempts. Try again later.");
        }
    }

    private void recordFailure(String key, int limit, Duration window) {
        if (limit < 1 || !bucketStore.recordAttempt(hash(key), limit, window, properties.lockout())) {
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
