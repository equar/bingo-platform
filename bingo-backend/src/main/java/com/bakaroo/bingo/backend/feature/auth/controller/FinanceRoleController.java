package com.bakaroo.bingo.backend.feature.auth.controller;

import com.bakaroo.bingo.backend.feature.auth.dto.FinanceRoleAssignmentRequest;
import com.bakaroo.bingo.backend.feature.auth.dto.UserSummaryResponse;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.auth.service.FinanceRoleService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class FinanceRoleController {
    private final FinanceRoleService service;

    public FinanceRoleController(FinanceRoleService service) { this.service = service; }

    @PostMapping("/{userId}/finance-roles")
    public UserSummaryResponse assign(@AuthenticationPrincipal BingoUserPrincipal principal,
            @PathVariable Long userId, @Valid @RequestBody FinanceRoleAssignmentRequest request) {
        return service.assign(principal.getUser(), userId, request);
    }

    @DeleteMapping("/{userId}/finance-roles")
    public UserSummaryResponse revoke(@AuthenticationPrincipal BingoUserPrincipal principal,
            @PathVariable Long userId, @RequestParam com.bakaroo.bingo.backend.feature.user.entity.UserRole role) {
        return service.revoke(principal.getUser(), userId, role);
    }
}
