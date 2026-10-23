package com.equar.bingo.backend.feature.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AgentApplicationRequest(
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Size(max = 30) String phoneNumber,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Pattern(regexp = "^[0-9]{4,8}$", message = "PIN must contain 4 to 8 digits") String pin,
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Size(max = 120) String territory,
        @NotBlank @Size(max = 40) String idDocumentType,
        @NotBlank @Size(max = 80) String idDocumentNumber,
        @Size(max = 200) String idDocumentFrontReference,
        @Size(max = 200) String idDocumentBackReference) {
}
