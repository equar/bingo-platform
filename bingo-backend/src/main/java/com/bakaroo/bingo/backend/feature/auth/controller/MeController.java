package com.bakaroo.bingo.backend.feature.auth.controller;

import com.bakaroo.bingo.backend.feature.auth.dto.MeResponse;
import com.bakaroo.bingo.backend.feature.auth.service.AuthService;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
        return authService.me(principal.getUsername());
    }
}