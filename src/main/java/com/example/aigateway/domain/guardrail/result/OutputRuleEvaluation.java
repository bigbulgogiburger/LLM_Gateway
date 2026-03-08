package com.example.aigateway.domain.guardrail.result;

public record OutputRuleEvaluation(
        RuleResult result,
        String content
) {
    public boolean blocks() {
        return "BLOCK".equalsIgnoreCase(result.action()) || "REVIEW".equalsIgnoreCase(result.action());
    }

    public boolean modifies() {
        return "MASK".equalsIgnoreCase(result.action());
    }
}
