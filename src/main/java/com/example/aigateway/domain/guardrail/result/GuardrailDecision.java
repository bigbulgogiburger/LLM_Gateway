package com.example.aigateway.domain.guardrail.result;

import java.util.List;

public record GuardrailDecision(
        boolean passed,
        List<RuleResult> results
) {
    public static GuardrailDecision passed(List<RuleResult> results) {
        return new GuardrailDecision(true, results);
    }

    public static GuardrailDecision blocked(List<RuleResult> results) {
        return new GuardrailDecision(false, results);
    }

    public boolean isBlocked() {
        return !passed;
    }
}
