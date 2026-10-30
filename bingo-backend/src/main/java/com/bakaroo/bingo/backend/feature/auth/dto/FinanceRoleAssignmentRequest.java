package com.bakaroo.bingo.backend.feature.auth.dto;

import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record FinanceRoleAssignmentRequest(@NotEmpty Set<UserRole> roles) {
}
