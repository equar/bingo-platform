package com.equar.bingo.backend.feature.auth.controller;

import com.equar.bingo.backend.feature.auth.dto.ChangePasswordRequest;
import com.equar.bingo.backend.feature.auth.dto.AdminLoginRequest;
import com.equar.bingo.backend.feature.auth.dto.AuthResponse;
import com.equar.bingo.backend.feature.auth.service.AdminAuthService;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    @Operation
    @SecurityRequirements
    public AuthResponse login(@Valid @RequestBody AdminLoginRequest request) {
        return adminAuthService.login(request);
    }

    @PutMapping("/me/password")
    public void changePassword(
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        adminAuthService.changePassword(principal.getUser().getId(), request);
    }
}