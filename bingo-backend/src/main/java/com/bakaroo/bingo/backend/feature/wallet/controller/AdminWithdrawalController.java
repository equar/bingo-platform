package com.bakaroo.bingo.backend.feature.wallet.controller;
import com.bakaroo.bingo.backend.common.api.PageResponse;
import com.bakaroo.bingo.backend.common.idempotency.IdempotentResult;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.wallet.dto.*;
import com.bakaroo.bingo.backend.feature.wallet.entity.WithdrawalStatus;
import com.bakaroo.bingo.backend.feature.wallet.service.WithdrawalService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable; import org.springframework.data.web.PageableDefault; import org.springframework.http.ResponseEntity; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/admin/wallet/withdrawals") @PreAuthorize("hasAnyRole('ADMIN','SUPPORT')")
public class AdminWithdrawalController {
 private final WithdrawalService service; public AdminWithdrawalController(WithdrawalService service){this.service=service;}
 @GetMapping public PageResponse<WithdrawalResponse> list(@RequestParam(required=false) String query,@RequestParam(required=false) WithdrawalStatus status,@PageableDefault(size=20) Pageable page){return PageResponse.from(service.search(query,status,page));}
 @PostMapping("/{id}/complete") public ResponseEntity<WithdrawalResponse> complete(@PathVariable Long id,@AuthenticationPrincipal BingoUserPrincipal p,@Valid @RequestBody WithdrawalReviewDto dto,@RequestHeader("Idempotency-Key") String key){IdempotentResult<WithdrawalResponse> r=service.complete(id,p,dto,key);return ResponseEntity.status(r.statusCode()).body(r.body());}
 @PostMapping("/{id}/reject") public ResponseEntity<WithdrawalResponse> reject(@PathVariable Long id,@AuthenticationPrincipal BingoUserPrincipal p,@Valid @RequestBody WithdrawalReviewDto dto,@RequestHeader("Idempotency-Key") String key){IdempotentResult<WithdrawalResponse> r=service.reject(id,p,dto,key);return ResponseEntity.status(r.statusCode()).body(r.body());}
}
