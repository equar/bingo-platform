package com.equar.bingo.backend.feature.game.controller;

import com.equar.bingo.backend.common.api.PageResponse;
import com.equar.bingo.backend.common.idempotency.IdempotentResult;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.game.dto.BingoRoomResponse;
import com.equar.bingo.backend.feature.game.dto.DrawEventResponse;
import com.equar.bingo.backend.feature.game.dto.TicketPurchaseResponse;
import com.equar.bingo.backend.feature.game.dto.TicketResponse;
import com.equar.bingo.backend.feature.game.dto.WinClaimRequest;
import com.equar.bingo.backend.feature.game.dto.WinClaimResponse;
import com.equar.bingo.backend.feature.game.service.BingoGameService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Game")
public class PublicGameController {

    private final BingoGameService bingoGameService;

    public PublicGameController(BingoGameService bingoGameService) {
        this.bingoGameService = bingoGameService;
    }

    @GetMapping("/rooms")
    public List<BingoRoomResponse> rooms() {
        return bingoGameService.listRooms();
    }

    @GetMapping("/rooms/{id}")
    public BingoRoomResponse room(@PathVariable Long id) {
        return bingoGameService.getRoom(id);
    }

    @GetMapping("/sessions/{id}")
    public com.equar.bingo.backend.feature.game.dto.GameSessionResponse session(@PathVariable Long id) {
        return bingoGameService.getSession(id);
    }

    @PostMapping("/sessions/{id}/tickets")
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<TicketPurchaseResponse> purchaseTicket(@PathVariable Long id,
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        IdempotentResult<TicketPurchaseResponse> result = bingoGameService.purchaseTicket(id, principal,
                idempotencyKey);
        return ResponseEntity.status(result.statusCode()).body(result.body());
    }

    @GetMapping("/sessions/{id}/my-tickets")
    @PreAuthorize("hasRole('PLAYER')")
    public PageResponse<TicketResponse> myTickets(@PathVariable Long id,
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(bingoGameService.getMyTickets(id, principal, pageable));
    }

    @GetMapping("/sessions/{id}/draws")
    @Operation(summary = "Replay ordered draws", description = "Returns draws with sequence numbers greater than afterSequence, ordered ascending.")
    public List<DrawEventResponse> draws(@PathVariable Long id, @RequestParam(required = false) Integer afterSequence) {
        return bingoGameService.getDrawsAfter(id, afterSequence);
    }

    @PostMapping("/tickets/{id}/win-claims")
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<WinClaimResponse> submitWinClaim(@PathVariable Long id,
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody WinClaimRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        IdempotentResult<WinClaimResponse> result = bingoGameService.submitWinClaim(id, principal, request,
                idempotencyKey);
        return ResponseEntity.status(result.statusCode()).body(result.body());
    }

    @GetMapping("/me/win-claims")
    @PreAuthorize("hasRole('PLAYER')")
    public PageResponse<WinClaimResponse> myWinClaims(@AuthenticationPrincipal BingoUserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(bingoGameService.getMyWinClaims(principal, pageable));
    }
}