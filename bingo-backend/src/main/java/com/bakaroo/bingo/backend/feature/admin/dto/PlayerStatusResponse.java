package com.bakaroo.bingo.backend.feature.admin.dto;

import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import java.time.Instant;

public record PlayerStatusResponse(Long playerId, AccountStatus status, Instant updatedAt) {
}
