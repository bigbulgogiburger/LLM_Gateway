package com.example.aigateway.api.request;

import jakarta.validation.constraints.NotBlank;

public record AiChatRequest(
        @NotBlank String userId,
        String role,
        @NotBlank String provider,
        @NotBlank String prompt
) {
}
