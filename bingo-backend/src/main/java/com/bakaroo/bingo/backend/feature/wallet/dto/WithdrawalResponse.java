package com.bakaroo.bingo.backend.feature.wallet.dto;
import com.bakaroo.bingo.backend.feature.wallet.entity.WithdrawalRequest;
import java.math.BigDecimal; import java.time.Instant;
public record WithdrawalResponse(Long id,String operationId,Long playerId,String playerDisplayName,String playerPhoneNumber,BigDecimal amount,String currency,String destinationType,String accountName,String phoneNumber,String bankName,String accountNumber,String status,Instant createdAt,Long reviewedBy,Instant reviewedAt,String transferReference,String reviewNote,String paymentEvidence){
 public static WithdrawalResponse from(WithdrawalRequest r){return new WithdrawalResponse(r.getId(),r.getOperationId(),r.getPlayer().getId(),r.getPlayer().getDisplayName(),r.getPlayer().getNormalizedPhone(),r.getAmount(),r.getCurrency(),r.getDestinationType(),r.getAccountName(),r.getPhoneNumber(),r.getBankName(),r.getAccountNumber(),r.getStatus().name(),r.getCreatedAt(),r.getReviewedBy()==null?null:r.getReviewedBy().getId(),r.getReviewedAt(),r.getTransferReference(),r.getReviewNote(),r.getPaymentEvidence());}
}
