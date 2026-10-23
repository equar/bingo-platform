package com.equar.bingo.backend.feature.admin.controller;

import com.equar.bingo.backend.feature.admin.dto.PlayerStatusResponse;
import com.equar.bingo.backend.feature.admin.dto.PlayerStatusUpdateRequest;
import com.equar.bingo.backend.feature.admin.service.AdminPlayerAccountService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/players/{playerId}")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPlayerAccountController {

    private final AdminPlayerAccountService adminPlayerAccountService;

    public AdminPlayerAccountController(AdminPlayerAccountService adminPlayerAccountService) {
        this.adminPlayerAccountService = adminPlayerAccountService;
    }

    @PutMapping("/status")
    public PlayerStatusResponse updateStatus(@PathVariable Long playerId,
            @Valid @RequestBody PlayerStatusUpdateRequest request) {
        return adminPlayerAccountService.updatePlayerStatus(playerId, request);
    }
}
