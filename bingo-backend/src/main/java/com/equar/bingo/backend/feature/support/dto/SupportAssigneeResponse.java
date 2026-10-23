package com.equar.bingo.backend.feature.support.dto;

import com.equar.bingo.backend.feature.user.entity.UserRole;
import java.util.Set;

public record SupportAssigneeResponse(Long userId, String displayName, Set<UserRole> roles) {
}
