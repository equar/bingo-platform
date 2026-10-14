package com.equar.bingo.backend.feature.auth.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class AdminPasswordGenerator {

    private static final char[] LOWER = "abcdefghjkmnpqrstuvwxyz".toCharArray();
    private static final char[] UPPER = "ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final char[] DIGITS = "23456789".toCharArray();
    private static final char[] SYMBOLS = "!@#$%&*?".toCharArray();
    private static final char[] ALL = (new String(LOWER) + new String(UPPER) + new String(DIGITS)
            + new String(SYMBOLS)).toCharArray();

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        char[] password = new char[12];
        password[0] = pick(LOWER);
        password[1] = pick(UPPER);
        password[2] = pick(DIGITS);
        password[3] = pick(SYMBOLS);

        for (int index = 4; index < password.length; index++) {
            password[index] = pick(ALL);
        }

        shuffle(password);
        return new String(password);
    }

    private char pick(char[] characters) {
        return characters[secureRandom.nextInt(characters.length)];
    }

    private void shuffle(char[] characters) {
        for (int index = characters.length - 1; index > 0; index--) {
            int swapIndex = secureRandom.nextInt(index + 1);
            char temp = characters[index];
            characters[index] = characters[swapIndex];
            characters[swapIndex] = temp;
        }
    }
}
