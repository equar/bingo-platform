package com.bakaroo.bingo.backend.config.performance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PerformanceTargetControllerTest {

    @Test
    void acceptsOnlyAFullLengthExactGuardToken() {
        String secret = "test-only-performance-guard-token-32+";
        assertThat(PerformanceTargetController.matchesGuardToken(secret, secret)).isTrue();
        assertThat(PerformanceTargetController.matchesGuardToken(secret, secret + "x")).isFalse();
        assertThat(PerformanceTargetController.matchesGuardToken("short", "short")).isFalse();
        assertThat(PerformanceTargetController.matchesGuardToken(secret, null)).isFalse();
    }
}
