package com.bakaroo.bingo.backend.feature.auth.dto;

import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
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
                @Pattern(regexp = "^$|^\\d{10}$", message = "Phone number must be 10 digits") String phoneNumber,
                @NotNull AccountStatus status,
                @NotEmpty Set<UserRole> roles) {

        /**
         * Retained for source compatibility with older callers. Password changes are no longer
         * accepted by the admin-user update contract.
         */
        public AdminUserUpdateRequest(String displayName, String email, String phoneNumber, String ignoredPassword,
                        AccountStatus status, Set<UserRole> roles) {
                this(displayName, email, phoneNumber, status, roles);
        }
}
