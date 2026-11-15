package com.bakaroo.bingo.backend.feature.wallet.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record WithdrawalRequestDto(@NotNull @DecimalMin(value="0.01") @Digits(integer=17,fraction=2) BigDecimal amount,@NotBlank @Pattern(regexp="ETB",flags=Pattern.Flag.CASE_INSENSITIVE) String currency,@NotBlank @Pattern(regexp="TELEBIRR|BANK") String destinationType,@NotBlank @Size(max=120) String accountName,@Size(max=30) String phoneNumber,@Size(max=100) String bankName,@Size(max=80) String accountNumber){}
