package com.equar.bingo.backend.feature.auth.controller;

import com.equar.bingo.backend.common.api.PageResponse;
import com.equar.bingo.backend.feature.auth.dto.SetAgentPinRequest;
import com.equar.bingo.backend.feature.auth.dto.UserSummaryResponse;
import com.equar.bingo.backend.feature.auth.service.AgentPinService;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final UserRepository userRepository;
    private final AgentPinService agentPinService;

    public AdminUserController(UserRepository userRepository, AgentPinService agentPinService) {
        this.userRepository = userRepository;
        this.agentPinService = agentPinService;
    }

    @GetMapping
    public PageResponse<UserSummaryResponse> listUsers(@PageableDefault(size = 20) Pageable pageable) {
        Page<UserSummaryResponse> page = userRepository.findAll(pageable).map(this::toSummary);
        return PageResponse.from(page);
    }

    @PostMapping("/{userId}/agent-pin")
    public void setAgentPin(@PathVariable Long userId, @Valid @RequestBody SetAgentPinRequest request) {
        agentPinService.setAgentPin(userId, request);
    }

    private UserSummaryResponse toSummary(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getDisplayName(),
                user.getNormalizedPhone(),
                user.getRoles(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}