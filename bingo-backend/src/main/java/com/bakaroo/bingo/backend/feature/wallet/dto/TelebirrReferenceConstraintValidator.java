package com.bakaroo.bingo.backend.feature.wallet.dto;

import com.bakaroo.bingo.backend.feature.wallet.service.TelebirrReferenceNormalizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TelebirrReferenceConstraintValidator
        implements ConstraintValidator<ValidTelebirrReference, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = TelebirrReferenceNormalizer.canonicalize(value);
        return !normalized.isEmpty() && normalized.length() <= 120;
    }
}
