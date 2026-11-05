package com.bakaroo.bingo.backend.feature.user.entity;

import java.util.Set;

/** Stable UI language codes supported across the platform. */
public final class UserLanguages {

    public static final String DEFAULT_LANGUAGE = "en";
    public static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "am");

    private UserLanguages() {
    }

    public static boolean isSupported(String language) {
        return language != null && SUPPORTED_LANGUAGES.contains(language);
    }

    /** Resolves a stored preference to a supported language, defaulting to English. */
    public static String resolve(String storedPreference) {
        return isSupported(storedPreference) ? storedPreference : DEFAULT_LANGUAGE;
    }
}
