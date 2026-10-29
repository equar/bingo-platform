package com.bakaroo.bingo.backend.feature.auth.controller;

import com.bakaroo.bingo.backend.common.api.PageResponse;
import com.bakaroo.bingo.backend.feature.auth.dto.AdminUserCreateRequest;
import com.bakaroo.bingo.backend.feature.auth.dto.AdminUserUpdateRequest;
import com.bakaroo.bingo.backend.feature.auth.dto.SetAgentPinRequest;
import com.bakaroo.bingo.backend.feature.auth.dto.UserSummaryResponse;
import com.bakaroo.bingo.backend.feature.auth.service.AgentPinService;
import com.bakaroo.bingo.backend.feature.auth.service.AdminUserService;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AgentPinService agentPinService;

    public AdminUserController(AdminUserService adminUserService, AgentPinService agentPinService) {
        this.adminUserService = adminUserService;
        this.agentPinService = agentPinService;
    }

    @GetMapping
    public PageResponse<UserSummaryResponse> listUsers(
            @PageableDefault(size = 20) Pageable pageable,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String search,
            @org.springframework.web.bind.annotation.RequestParam(required = false) AccountStatus status) {
        return PageResponse.from(adminUserService.listUsers(pageable, search, status));
    }

    @PostMapping
    public UserSummaryResponse createUser(@Valid @RequestBody AdminUserCreateRequest request) {
        return adminUserService.createUser(request);
    }

    @PutMapping("/{userId}")
    public UserSummaryResponse updateUser(@PathVariable Long userId, @Valid @RequestBody AdminUserUpdateRequest request) {
        return adminUserService.updateUser(userId, request);
    }

    @PostMapping("/{userId}/agent-pin")
    public void setAgentPin(@PathVariable Long userId, @Valid @RequestBody SetAgentPinRequest request) {
        agentPinService.setAgentPin(userId, request);
    }
}