package com.bakaroo.bingo.backend.feature.wallet.dto;
import jakarta.validation.constraints.*;
public record WithdrawalReviewDto(@Size(max=120) String transferReference,@NotBlank @Size(max=500) String reviewNote){}
