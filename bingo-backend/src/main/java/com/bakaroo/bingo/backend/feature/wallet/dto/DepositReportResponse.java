package com.bakaroo.bingo.backend.feature.wallet.dto;

import com.bakaroo.bingo.backend.feature.wallet.entity.DepositClaim;
import com.bakaroo.bingo.backend.feature.wallet.entity.DepositClaimStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record DepositReportResponse(
        Long id,
        Long playerId,
        BigDecimal submittedCashAmount,
        BigDecimal calculatedCreditAmount,
        String currency,
        DepositClaimStatus status,
        Instant submittedAt,
        Instant verifiedAt,
        Instant reviewedAt) {

    public static DepositReportResponse from(DepositClaim claim) {
        return new DepositReportResponse(
                claim.getId(),
                claim.getPlayer().getId(),
                claim.getSubmittedCashAmount(),
                claim.getCalculatedCreditAmount(),
                claim.getCurrency(),
                claim.getStatus(),
                claim.getSubmittedAt(),
                claim.getVerifiedAt(),
                claim.getReviewedAt());
    }
}
