package com.equar.bingo.backend.feature.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.equar.bingo.backend.common.exception.BadRequestException;

import org.junit.jupiter.api.Test;

class EthiopianPhoneNormalizerTest {

    private final EthiopianPhoneNormalizer normalizer = new EthiopianPhoneNormalizer();

    @Test
    void normalizesEthiopianMobileNumbersSafely() {
        assertThat(normalizer.normalize("0911 000 003")).isEqualTo("251911000003");
        assertThat(normalizer.normalize("+251 911 000 003")).isEqualTo("251911000003");
        assertThat(normalizer.normalize("251911000003")).isEqualTo("251911000003");
        assertThat(normalizer.normalize("+251-711-222-333")).isEqualTo("251711222333");
    }

    @Test
    void rejectsBlankAndNonMobileNumbers() {
        assertThrows(BadRequestException.class, () -> normalizer.normalize(null));
        assertThrows(BadRequestException.class, () -> normalizer.normalize("   "));
        assertThrows(BadRequestException.class, () -> normalizer.normalize("+251 611 000 003"));
        assertThrows(BadRequestException.class, () -> normalizer.normalize("12345"));
    }
}