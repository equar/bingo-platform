package com.bakaroo.bingo.backend.feature.admin.dto;

import com.bakaroo.bingo.backend.feature.user.entity.PlayerVerificationReason;
import com.bakaroo.bingo.backend.feature.user.entity.PlayerVerificationStatus;
import jakarta.validation.constraints.NotNull;

public record PlayerVerificationUpdateRequest(
        @NotNull PlayerVerificationStatus status,
        @NotNull PlayerVerificationReason reason) {
}
