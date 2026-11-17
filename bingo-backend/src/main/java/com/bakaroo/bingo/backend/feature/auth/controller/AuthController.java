package com.bakaroo.bingo.backend.feature.auth.controller;

import com.bakaroo.bingo.backend.feature.auth.dto.AuthResponse;
import com.bakaroo.bingo.backend.feature.auth.dto.LoginRequest;
import com.bakaroo.bingo.backend.feature.auth.dto.MeResponse;
import com.bakaroo.bingo.backend.feature.auth.dto.RegisterRequest;
import com.bakaroo.bingo.backend.feature.auth.service.AuthService;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation
    @SecurityRequirements
    @ApiResponse(responseCode = "201", description = "Player account created", useReturnTypeSchema = true)
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation
    @SecurityRequirements
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/login/replace-session")
    @Operation(summary = "Sign in and replace the existing player device session")
    @SecurityRequirements
    public AuthResponse replacePlayerSession(@Valid @RequestBody LoginRequest request) {
        return authService.replacePlayerSession(request);
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal BingoUserPrincipal principal) {
        return authService.me(principal.getUser().getId());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal BingoUserPrincipal principal,
            HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : null;
        authService.logout(principal.getUser().getId(), token == null ? null : authService.sessionId(token));
        return ResponseEntity.noContent().build();
    }
}
