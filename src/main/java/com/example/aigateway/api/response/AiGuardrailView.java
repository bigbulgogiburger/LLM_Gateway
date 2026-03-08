package com.example.aigateway.api.response;

public record AiGuardrailView(
        String verdict,
        String reason,
        Double score
) {
}
