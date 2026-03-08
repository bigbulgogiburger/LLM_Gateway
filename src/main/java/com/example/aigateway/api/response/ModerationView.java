package com.example.aigateway.api.response;

public record ModerationView(
        String phase,
        String category,
        String verdict,
        String reason,
        Double score
) {
}
