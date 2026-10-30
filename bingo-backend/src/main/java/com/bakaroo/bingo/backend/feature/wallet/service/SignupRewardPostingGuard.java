package com.bakaroo.bingo.backend.feature.wallet.service;

import com.bakaroo.bingo.backend.common.exception.ConflictException;
import org.springframework.stereotype.Component;

/** Restricts positive signup reward posting to isolated MySQL integration tests. */
@Component
public class SignupRewardPostingGuard {
    private final LocalFinancialSimulationGuard financialSimulationGuard;

    public SignupRewardPostingGuard(LocalFinancialSimulationGuard financialSimulationGuard) {
        this.financialSimulationGuard = financialSimulationGuard;
    }

    public void validateConfiguration(boolean enabled) {
        financialSimulationGuard.validateConfiguration(enabled, "Signup reward posting");
    }

    public void requireIsolatedTestPosting(boolean enabled) {
        if (!enabled) {
            throw new ConflictException("SIGNUP_REWARD_POSTING_NOT_APPROVED",
                    "Positive signup rewards are disabled until Product and Finance approve their posting policy");
        }
        financialSimulationGuard.requireEnabled(true, "Signup reward posting");
    }
}
