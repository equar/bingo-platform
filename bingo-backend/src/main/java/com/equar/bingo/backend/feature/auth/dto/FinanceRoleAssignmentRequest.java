package com.equar.bingo.backend.feature.auth.dto;

import com.equar.bingo.backend.feature.user.entity.UserRole;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record FinanceRoleAssignmentRequest(@NotEmpty Set<UserRole> roles) {
}
