package com.bakaroo.bingo.backend.feature.admin.controller;

import com.bakaroo.bingo.backend.common.api.PageResponse;
import com.bakaroo.bingo.backend.feature.game.dto.TicketResponse;
import com.bakaroo.bingo.backend.feature.game.dto.WinClaimResponse;
import com.bakaroo.bingo.backend.feature.game.service.BingoGameService;
import com.bakaroo.bingo.backend.feature.support.dto.SupportTicketSummaryResponse;
import com.bakaroo.bingo.backend.feature.support.service.SupportService;
import com.bakaroo.bingo.backend.feature.wallet.dto.DepositClaimResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletTransactionResponse;
import com.bakaroo.bingo.backend.feature.wallet.service.WalletService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/players/{playerId}")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPlayerActivityController {

    private final WalletService walletService;
    private final SupportService supportService;
    private final BingoGameService bingoGameService;

    public AdminPlayerActivityController(WalletService walletService, SupportService supportService,
            BingoGameService bingoGameService) {
        this.walletService = walletService;
        this.supportService = supportService;
        this.bingoGameService = bingoGameService;
    }

    @GetMapping("/wallet-transactions")
    public PageResponse<WalletTransactionResponse> walletTransactions(@PathVariable Long playerId,
            @PageableDefault(size = 20, sort = { "createdAt",
                    "id" }, direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(walletService.getAdminPlayerTransactions(playerId, pageable));
    }

    @GetMapping("/deposits")
    public PageResponse<DepositClaimResponse> deposits(@PathVariable Long playerId,
            @PageableDefault(size = 20, sort = { "submittedAt",
                    "id" }, direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(walletService.getAdminPlayerDeposits(playerId, pageable));
    }

    @GetMapping("/support-tickets")
    public PageResponse<SupportTicketSummaryResponse> supportTickets(@PathVariable Long playerId,
            @PageableDefault(size = 20, sort = { "createdAt",
                    "id" }, direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(supportService.getAdminPlayerTickets(playerId, pageable));
    }

    @GetMapping("/tickets")
    public PageResponse<TicketResponse> tickets(@PathVariable Long playerId,
            @PageableDefault(size = 20, sort = { "purchasedAt",
                    "id" }, direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(bingoGameService.getAdminPlayerTickets(playerId, pageable));
    }

    @GetMapping("/win-claims")
    public PageResponse<WinClaimResponse> winClaims(@PathVariable Long playerId,
            @PageableDefault(size = 20, sort = { "createdAt",
                    "id" }, direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(bingoGameService.getAdminPlayerWinClaims(playerId, pageable));
    }
}