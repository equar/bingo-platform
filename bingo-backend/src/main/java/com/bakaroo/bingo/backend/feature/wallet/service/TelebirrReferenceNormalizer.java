package com.bakaroo.bingo.backend.feature.wallet.service;

import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import org.springframework.stereotype.Component;

@Component
public class TelebirrReferenceNormalizer {

    public String normalize(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new BadRequestException("TELEBIRR_REFERENCE_REQUIRED", "Telebirr reference is required");
        }

        String normalized = reference.trim().replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (normalized.isBlank()) {
            throw new BadRequestException("TELEBIRR_REFERENCE_INVALID", "Telebirr reference is invalid");
        }
        return normalized;
    }
}