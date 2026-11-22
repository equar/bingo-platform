package com.bakaroo.bingo.backend.feature.wallet.service;

import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Canonical provider-scoped withdrawal reference: ASCII alphanumerics, uppercase. */
@Component
public class WithdrawalReferenceNormalizer {

    public String normalize(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new BadRequestException("TRANSFER_REFERENCE_REQUIRED", "Transfer reference is required");
        }
        String normalized = canonicalize(reference);
        if (reference.length() > 120 || normalized.isEmpty() || normalized.length() > 120) {
            throw new BadRequestException("TRANSFER_REFERENCE_INVALID", "Transfer reference is invalid");
        }
        return normalized;
    }

    public static String canonicalize(String reference) {
        return reference == null ? ""
                : reference.trim().replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}
