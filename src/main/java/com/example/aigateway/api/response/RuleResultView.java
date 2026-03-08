package com.example.aigateway.api.response;

public record RuleResultView(
        String code,
        String reason,
        String action
) {
}
