package com.equar.bingo.backend.feature.game.controller;

import com.equar.bingo.backend.common.api.PageResponse;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.game.dto.BingoRoomRequest;
import com.equar.bingo.backend.feature.game.dto.BingoRoomResponse;
import com.equar.bingo.backend.feature.game.dto.DrawEventRequest;
import com.equar.bingo.backend.feature.game.dto.DrawEventResponse;
import com.equar.bingo.backend.feature.game.dto.GameSessionRequest;
import com.equar.bingo.backend.feature.game.dto.GameSessionResponse;
import com.equar.bingo.backend.feature.game.dto.GameSessionStateUpdateRequest;
import com.equar.bingo.backend.feature.game.dto.PrizeEntitlementResponse;
import com.equar.bingo.backend.feature.game.dto.PrizeEntitlementSettlementRequest;
import com.equar.bingo.backend.feature.game.dto.WinClaimResponse;
import com.equar.bingo.backend.feature.game.dto.WinClaimReviewRequest;
import com.equar.bingo.backend.feature.game.entity.BingoPatternType;
import com.equar.bingo.backend.feature.game.entity.PrizeEntitlement;
import com.equar.bingo.backend.feature.game.entity.PrizeEntitlementStatus;
import com.equar.bingo.backend.feature.game.entity.WinClaimStatus;
import com.equar.bingo.backend.feature.game.repository.PrizeEntitlementRepository;
import com.equar.bingo.backend.feature.game.service.BingoGameService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
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

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Game Admin")
public class AdminGameController {

    private final BingoGameService bingoGameService;
    private final PrizeEntitlementRepository prizeEntitlementRepository;

    public AdminGameController(BingoGameService bingoGameService,
            PrizeEntitlementRepository prizeEntitlementRepository) {
        this.bingoGameService = bingoGameService;
        this.prizeEntitlementRepository = prizeEntitlementRepository;
    }

    @GetMapping("/rooms")
    public PageResponse<BingoRoomResponse> rooms(@RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean active, @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(bingoGameService.listAdminRooms(query, active, pageable));
    }

    @PostMapping("/rooms")
    public BingoRoomResponse createRoom(@Valid @RequestBody BingoRoomRequest request) {
        return bingoGameService.createRoom(request);
    }

    @PutMapping("/rooms/{id}")
    public BingoRoomResponse updateRoom(@PathVariable Long id, @Valid @RequestBody BingoRoomRequest request) {
        return bingoGameService.updateRoom(id, request);
    }

    @GetMapping("/sessions/{id}")
    public GameSessionResponse session(@PathVariable Long id) {
        return bingoGameService.getSession(id);
    }

    @PostMapping("/rooms/{id}/sessions")
    public GameSessionResponse createSession(@PathVariable Long id, @Valid @RequestBody GameSessionRequest request) {
        return bingoGameService.createSession(id, request);
    }

    @PutMapping("/sessions/{id}/state")
    public GameSessionResponse updateState(@PathVariable Long id,
            @Valid @RequestBody GameSessionStateUpdateRequest request) {
        return bingoGameService.updateSessionState(id, request);
    }

    @PostMapping("/sessions/{id}/draws")
    public DrawEventResponse draw(@PathVariable Long id,
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody DrawEventRequest request) {
        return bingoGameService.recordDraw(id, request, principal);
    }

    @GetMapping("/win-claims")
    public PageResponse<WinClaimResponse> winClaims(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) WinClaimStatus status,
            @RequestParam(required = false) BingoPatternType pattern,
            @RequestParam(required = false) Boolean serverValidationPassed,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(
                bingoGameService.getAdminWinClaims(query, status, pattern, serverValidationPassed, pageable));
    }

    @PostMapping("/win-claims/{id}/approve")
    public WinClaimResponse approveWinClaim(@PathVariable Long id,
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody WinClaimReviewRequest request) {
        return bingoGameService.reviewWinClaim(id, principal, request, true);
    }

    @PostMapping("/win-claims/{id}/reject")
    public WinClaimResponse rejectWinClaim(@PathVariable Long id,
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody WinClaimReviewRequest request) {
        return bingoGameService.reviewWinClaim(id, principal, request, false);
    }

    @GetMapping("/prize-entitlements")
    public PageResponse<PrizeEntitlementResponse> prizeEntitlements(@RequestParam(required = false) String query,
            @RequestParam(required = false) PrizeEntitlementStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
        Long numericQuery = null;
        if (normalizedQuery != null) {
            try {
                numericQuery = Long.valueOf(normalizedQuery);
            } catch (NumberFormatException ignored) {
                numericQuery = null;
            }
        }
        return PageResponse.from(prizeEntitlementRepository
                .searchAdmin(normalizedQuery, numericQuery, status, pageable)
                .map(this::toPrizeEntitlementResponse));
    }

    @PostMapping("/prize-entitlements/{id}/settle")
    public PrizeEntitlementResponse settle(@PathVariable Long id,
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody PrizeEntitlementSettlementRequest request) {
        return bingoGameService.settleEntitlement(id, principal, request);
    }

    private PrizeEntitlementResponse toPrizeEntitlementResponse(PrizeEntitlement entitlement) {
        return new PrizeEntitlementResponse(
                entitlement.getId(),
                entitlement.getWinClaim().getId(),
                entitlement.getSession().getId(),
                entitlement.getTicket().getId(),
                entitlement.getClaimant().getId(),
                entitlement.getPrizeType(),
                entitlement.getSettlementPath(),
                entitlement.getStatus(),
                entitlement.getPrizeAmount(),
                entitlement.getCurrency(),
                entitlement.getOperationId(),
                entitlement.getCreatedAt(),
                entitlement.getSettledAt(),
                entitlement.getSettledBy() == null ? null : entitlement.getSettledBy().getId(),
                entitlement.getSettlementReference(),
                entitlement.getSettlementNote());
    }
}