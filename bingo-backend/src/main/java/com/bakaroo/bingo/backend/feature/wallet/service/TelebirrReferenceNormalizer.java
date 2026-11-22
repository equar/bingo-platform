package com.bakaroo.bingo.backend.feature.wallet.service;

import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class TelebirrReferenceNormalizer {

    public String normalize(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new BadRequestException("TELEBIRR_REFERENCE_REQUIRED", "Telebirr reference is required");
        }
        String normalized = canonicalize(reference);
        if (normalized.isBlank() || normalized.length() > 120) {
            throw new BadRequestException("TELEBIRR_REFERENCE_INVALID", "Telebirr reference is invalid");
        }
        return normalized;
    }

    public static String canonicalize(String reference) {
        return reference == null ? ""
                : reference.trim().replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}
