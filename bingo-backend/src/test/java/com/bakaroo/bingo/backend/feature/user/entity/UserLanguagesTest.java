package com.bakaroo.bingo.backend.feature.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserLanguagesTest {

    @Test
    void supportsOnlyStableLanguageCodes() {
        assertThat(UserLanguages.isSupported("en")).isTrue();
        assertThat(UserLanguages.isSupported("am")).isTrue();
        assertThat(UserLanguages.isSupported("fr")).isFalse();
        assertThat(UserLanguages.isSupported("EN")).isFalse();
        assertThat(UserLanguages.isSupported("")).isFalse();
        assertThat(UserLanguages.isSupported(null)).isFalse();
    }

    @Test
    void resolvesMissingOrUnsupportedPreferencesToEnglish() {
        assertThat(UserLanguages.resolve(null)).isEqualTo("en");
        assertThat(UserLanguages.resolve("")).isEqualTo("en");
        assertThat(UserLanguages.resolve("de")).isEqualTo("en");
        assertThat(UserLanguages.resolve("am")).isEqualTo("am");
        assertThat(UserLanguages.resolve("en")).isEqualTo("en");
    }
}
