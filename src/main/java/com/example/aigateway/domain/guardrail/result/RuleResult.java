package com.example.aigateway.domain.guardrail.result;

public record RuleResult(
        String code,
        String reason,
        String action
) {
}
