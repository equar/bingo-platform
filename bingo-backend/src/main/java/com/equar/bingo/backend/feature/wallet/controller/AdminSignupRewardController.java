package com.equar.bingo.backend.feature.wallet.controller;

import com.equar.bingo.backend.common.api.PageResponse;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.wallet.dto.SignupRewardGrantResponse;
import com.equar.bingo.backend.feature.wallet.dto.SignupRewardSettingsRequest;
import com.equar.bingo.backend.feature.wallet.dto.SignupRewardSettingsResponse;
import com.equar.bingo.backend.feature.wallet.service.SignupRewardService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/wallet/signup-rewards")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSignupRewardController {

    private final SignupRewardService signupRewardService;

    public AdminSignupRewardController(SignupRewardService signupRewardService) {
        this.signupRewardService = signupRewardService;
    }

    @GetMapping("/settings")
    public SignupRewardSettingsResponse currentSettings() {
        return signupRewardService.currentSettings();
    }

    @GetMapping("/settings/history")
    public PageResponse<SignupRewardSettingsResponse> settingsHistory(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(signupRewardService.settingsHistory(pageable));
    }

    @PostMapping("/settings")
    public SignupRewardSettingsResponse updateSettings(@AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody SignupRewardSettingsRequest request) {
        return signupRewardService.saveSettings(principal, request);
    }

    @GetMapping("/grants")
    public PageResponse<SignupRewardGrantResponse> grants(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(signupRewardService.grants(pageable));
    }
}