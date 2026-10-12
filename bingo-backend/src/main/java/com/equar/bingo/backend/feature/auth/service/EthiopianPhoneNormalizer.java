package com.equar.bingo.backend.feature.auth.service;

import com.equar.bingo.backend.common.exception.BadRequestException;
import org.springframework.stereotype.Component;

@Component
public class EthiopianPhoneNormalizer {

    public String normalize(String rawPhoneNumber) {
        if (rawPhoneNumber == null || rawPhoneNumber.isBlank()) {
            throw new BadRequestException("PHONE_REQUIRED", "Phone number is required");
        }

        String digitsOnly = rawPhoneNumber.trim().replaceAll("[\\s\\-().]", "");
        if (digitsOnly.startsWith("+")) {
            digitsOnly = digitsOnly.substring(1);
        }
        digitsOnly = digitsOnly.replaceAll("[^0-9]", "");

        if (digitsOnly.length() == 12 && digitsOnly.startsWith("251")) {
            return validateLocalMobile(digitsOnly.substring(3));
        }

        if (digitsOnly.length() == 10 && digitsOnly.startsWith("0")) {
            return validateLocalMobile(digitsOnly.substring(1));
        }

        if (digitsOnly.length() == 9) {
            return validateLocalMobile(digitsOnly);
        }

        throw new BadRequestException("INVALID_PHONE_NUMBER", "Phone number must be a valid Ethiopian mobile number");
    }

    private String validateLocalMobile(String localNumber) {
        if (localNumber.length() != 9) {
            throw new BadRequestException("INVALID_PHONE_NUMBER",
                    "Phone number must be a valid Ethiopian mobile number");
        }
        if (!(localNumber.startsWith("9") || localNumber.startsWith("7"))) {
            throw new BadRequestException("INVALID_PHONE_NUMBER",
                    "Phone number must be a valid Ethiopian mobile number");
        }
        return "251" + localNumber;
    }
}