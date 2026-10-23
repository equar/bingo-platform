package com.equar.bingo.backend.feature.agent.controller;

import com.equar.bingo.backend.feature.agent.dto.FloatBankAccountRequest;
import com.equar.bingo.backend.feature.agent.dto.FloatBankAccountResponse;
import com.equar.bingo.backend.feature.agent.service.FloatBankAccountService;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class FloatBankAccountController {
    private final FloatBankAccountService service;

    public FloatBankAccountController(FloatBankAccountService service) { this.service = service; }

    @GetMapping("/agent/float-bank-accounts")
    @PreAuthorize("hasRole('AGENT')")
    public List<FloatBankAccountResponse> available() { return service.activeAccounts(); }

    @GetMapping("/admin/float-bank-accounts")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE_VERIFIER','FINANCE_APPROVER')")
    public List<FloatBankAccountResponse> all() { return service.allAccounts(); }

    @PostMapping("/admin/float-bank-accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public FloatBankAccountResponse create(@AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody FloatBankAccountRequest request) {
        return service.create(principal, request);
    }

    @PutMapping("/admin/float-bank-accounts/{accountId}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public FloatBankAccountResponse setActive(@AuthenticationPrincipal BingoUserPrincipal principal,
            @PathVariable Long accountId, @RequestParam boolean active) {
        return service.updateActiveStatus(principal, accountId, active);
    }

    @PutMapping("/admin/float-bank-accounts/{accountId}/player-deposit-eligibility")
    @PreAuthorize("hasRole('ADMIN')")
    public FloatBankAccountResponse setPlayerDepositEligibility(
            @AuthenticationPrincipal BingoUserPrincipal principal, @PathVariable Long accountId,
            @RequestParam boolean enabled) {
        return service.updatePlayerDepositEligibility(principal, accountId, enabled);
    }
}
