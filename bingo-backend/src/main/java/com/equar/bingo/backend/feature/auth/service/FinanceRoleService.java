package com.equar.bingo.backend.feature.auth.service;

import com.equar.bingo.backend.common.exception.ConflictException;
import com.equar.bingo.backend.common.exception.NotFoundException;
import com.equar.bingo.backend.feature.auth.dto.FinanceRoleAssignmentRequest;
import com.equar.bingo.backend.feature.auth.dto.UserSummaryResponse;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceRoleService {
    private final UserRepository users;

    public FinanceRoleService(UserRepository users) { this.users = users; }

    @Transactional
    public UserSummaryResponse assign(User actor, Long targetId, FinanceRoleAssignmentRequest request) {
        if (!actor.isActive() || !actor.hasRole(UserRole.ADMIN)) {
            throw new ConflictException("ADMIN_REQUIRED", "An active administrator is required");
        }
        if (actor.getId().equals(targetId)) {
            throw new ConflictException("FINANCE_ROLE_SELF_ASSIGNMENT_DENIED",
                    "An administrator cannot assign finance permissions to their own account");
        }
        Set<UserRole> requested = request.roles();
        if (requested == null || requested.isEmpty()
                || !EnumSet.of(UserRole.FINANCE_VERIFIER, UserRole.FINANCE_APPROVER).containsAll(requested)) {
            throw new ConflictException("INVALID_FINANCE_ROLE", "Only finance verifier and approver roles may be assigned");
        }
        User target = users.findById(targetId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User account not found"));
        if (!target.isActive() || (!target.hasRole(UserRole.ADMIN) && !target.hasRole(UserRole.SUPPORT))) {
            throw new ConflictException("FINANCE_ROLE_TARGET_NOT_ELIGIBLE",
                    "Finance permissions may only be assigned to active administrator or support accounts");
        }
        Set<UserRole> roles = new LinkedHashSet<>(target.getRoles());
        roles.addAll(requested);
        target.setRoles(roles);
        User saved = users.save(target);
        return new UserSummaryResponse(saved.getId(), saved.getDisplayName(), saved.getEmail(),
                saved.getNormalizedPhone(), saved.getRoles(), saved.getStatus(), saved.getCreatedAt(), saved.getUpdatedAt());
    }

    @Transactional
    public UserSummaryResponse revoke(User actor, Long targetId, UserRole role) {
        if (!actor.isActive() || !actor.hasRole(UserRole.ADMIN)) {
            throw new ConflictException("ADMIN_REQUIRED", "An active administrator is required");
        }
        if (actor.getId().equals(targetId)) {
            throw new ConflictException("FINANCE_ROLE_SELF_ASSIGNMENT_DENIED",
                    "An administrator cannot change finance permissions on their own account");
        }
        if (role != UserRole.FINANCE_VERIFIER && role != UserRole.FINANCE_APPROVER) {
            throw new ConflictException("INVALID_FINANCE_ROLE", "Only finance verifier and approver roles may be revoked");
        }
        User target = users.findById(targetId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User account not found"));
        Set<UserRole> roles = new LinkedHashSet<>(target.getRoles());
        roles.remove(role);
        target.setRoles(roles);
        User saved = users.save(target);
        return new UserSummaryResponse(saved.getId(), saved.getDisplayName(), saved.getEmail(),
                saved.getNormalizedPhone(), saved.getRoles(), saved.getStatus(), saved.getCreatedAt(), saved.getUpdatedAt());
    }
}
