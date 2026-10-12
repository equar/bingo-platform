package com.equar.bingo.backend.feature.auth.controller;

import com.equar.bingo.backend.feature.auth.dto.AdminLoginRequest;
import com.equar.bingo.backend.feature.auth.dto.AuthResponse;
import com.equar.bingo.backend.feature.auth.service.AdminAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public AuthResponse login(@Valid @RequestBody AdminLoginRequest request) {
        return adminAuthService.login(request);
    }
}