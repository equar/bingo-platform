package com.equar.bingo.backend.feature.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EthiopianPhoneNormalizerTest {

    private final EthiopianPhoneNormalizer normalizer = new EthiopianPhoneNormalizer();

    @Test
    void normalizesEthiopianMobileNumbersSafely() {
        assertThat(normalizer.normalize("0911 000 003")).isEqualTo("251911000003");
        assertThat(normalizer.normalize("+251 911 000 003")).isEqualTo("251911000003");
        assertThat(normalizer.normalize("251911000003")).isEqualTo("251911000003");
    }
}