package com.equar.bingo.backend.feature.auth.dto;

import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record AdminUserUpdateRequest(
                @NotBlank @Size(max = 120) String displayName,
                @NotBlank @Email @Size(max = 255) String email,
                @NotBlank @Pattern(regexp = "\\d{10}", message = "Phone number must be 10 digits") String phoneNumber,
                @Size(min = 8, max = 72) String password,
                @NotNull AccountStatus status,
                @NotEmpty Set<UserRole> roles) {
}