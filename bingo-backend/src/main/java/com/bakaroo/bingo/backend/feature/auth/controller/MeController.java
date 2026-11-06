package com.bakaroo.bingo.backend.feature.auth.controller;

import com.bakaroo.bingo.backend.feature.auth.dto.MeResponse;
import com.bakaroo.bingo.backend.feature.auth.dto.AuthResponse;
import com.bakaroo.bingo.backend.feature.auth.dto.UpdateMyPreferencesRequest;
import com.bakaroo.bingo.backend.feature.auth.dto.UpdateMyProfileRequest;
import com.bakaroo.bingo.backend.feature.auth.service.AuthService;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MeController {

    private final AuthService authService;

    public MeController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal BingoUserPrincipal principal) {
        return authService.me(principal.getUser().getId());
    }

    @PutMapping("/me/preferences")
    public MeResponse updateMyPreferences(
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody UpdateMyPreferencesRequest request) {
        return authService.updateMyPreferences(principal.getUser().getId(), request);
    }

    @PutMapping("/me/profile")
    public AuthResponse updateMyProfile(
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody UpdateMyProfileRequest request) {
        return authService.updateMyProfile(principal.getUser().getId(), request);
    }
}
