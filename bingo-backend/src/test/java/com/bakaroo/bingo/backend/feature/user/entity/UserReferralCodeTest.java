package com.bakaroo.bingo.backend.feature.user.entity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UserReferralCodeTest {

    @Test
    void newReferralCodeIsSixUppercaseLettersOrDigits() {
        assertTrue(User.newReferralCode().matches("[A-Z0-9]{6}"));
    }
}
