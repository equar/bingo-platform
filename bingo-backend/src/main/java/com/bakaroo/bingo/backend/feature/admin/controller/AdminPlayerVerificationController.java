package com.bakaroo.bingo.backend.feature.admin.controller;

import com.bakaroo.bingo.backend.common.api.PageResponse;
import com.bakaroo.bingo.backend.feature.admin.dto.PlayerVerificationQueueItemResponse;
import com.bakaroo.bingo.backend.feature.admin.dto.PlayerVerificationReviewResponse;
import com.bakaroo.bingo.backend.feature.admin.dto.PlayerVerificationUpdateRequest;
import com.bakaroo.bingo.backend.feature.admin.service.AdminPlayerVerificationService;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.user.entity.PlayerVerificationStatus;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPlayerVerificationController {

    private final AdminPlayerVerificationService service;

    public AdminPlayerVerificationController(AdminPlayerVerificationService service) {
        this.service = service;
    }

    @GetMapping("/player-verifications")
    public PageResponse<PlayerVerificationQueueItemResponse> queue(
            @RequestParam(required = false) PlayerVerificationStatus status,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = { "createdAt", "id" }, direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(service.queue(status, query, pageable));
    }

    @GetMapping("/players/{playerId}/verification/reviews")
    public List<PlayerVerificationReviewResponse> history(@PathVariable Long playerId) {
        return service.history(playerId);
    }

    @PostMapping("/players/{playerId}/verification/reviews")
    public PlayerVerificationReviewResponse recordReview(
            @PathVariable Long playerId,
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody PlayerVerificationUpdateRequest request) {
        return service.recordPlayerVerification(playerId, principal.getUser().getId(), request);
    }
}
