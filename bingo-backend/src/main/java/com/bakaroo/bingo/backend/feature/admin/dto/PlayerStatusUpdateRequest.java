package com.bakaroo.bingo.backend.feature.admin.dto;

import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlayerStatusUpdateRequest(
        @NotNull AccountStatus status,
        @NotBlank @Size(max = 500) String reason) {
}
