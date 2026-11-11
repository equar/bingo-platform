package com.bakaroo.bingo.backend.feature.wallet.controller;

import com.bakaroo.bingo.backend.common.api.PageResponse;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.wallet.dto.SignupRewardGrantResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.SignupRewardPolicyStatusRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.SignupRewardSettingsRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.SignupRewardSettingsResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.PromotionalRewardCampaignRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.PromotionalRewardCampaignResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.PromotionalRewardGrantRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.PromotionalRewardGrantResponse;
import com.bakaroo.bingo.backend.feature.wallet.service.SignupRewardService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/v1/admin/wallet/signup-rewards")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT')")
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
    public PageResponse<SignupRewardSettingsResponse> settingsHistory(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean enabled,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(signupRewardService.settingsHistory(query, enabled, pageable));
    }

    @PostMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public SignupRewardSettingsResponse updateSettings(@AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody SignupRewardSettingsRequest request) {
        return signupRewardService.saveSettings(principal, request);
    }

    @PutMapping("/settings/{policyId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public SignupRewardSettingsResponse updatePolicyStatus(@AuthenticationPrincipal BingoUserPrincipal principal,
            @PathVariable Long policyId, @RequestBody SignupRewardPolicyStatusRequest request) {
        return signupRewardService.setPolicyStatus(principal, policyId, request.enabled());
    }

    @GetMapping("/grants")
    public PageResponse<SignupRewardGrantResponse> grants(@RequestParam(required = false) String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(signupRewardService.listGrants(query, pageable));
    }

    @GetMapping("/campaigns")
    public PageResponse<PromotionalRewardCampaignResponse> campaigns(@RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean enabled, @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(signupRewardService.listCampaigns(query, enabled, pageable));
    }

    @PostMapping("/campaigns")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PromotionalRewardCampaignResponse createCampaign(@AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody PromotionalRewardCampaignRequest request) {
        return signupRewardService.createCampaign(principal, request);
    }

    @PostMapping("/grants")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PromotionalRewardGrantResponse awardManualCampaign(@AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody PromotionalRewardGrantRequest request) {
        return signupRewardService.awardManualCampaign(principal, request);
    }

    @GetMapping("/promotional-grants")
    public PageResponse<PromotionalRewardGrantResponse> promotionalGrants(
            @RequestParam(required = false) String query, @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(signupRewardService.listPromotionalGrants(query, pageable));
    }
}
