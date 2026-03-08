package com.example.aigateway.domain.guardrail.result;

import java.util.List;

public record OutputGuardrailDecision(
        boolean passed,
        boolean modified,
        String content,
        List<RuleResult> results
) {
    public static OutputGuardrailDecision passed(String content, boolean modified, List<RuleResult> results) {
        return new OutputGuardrailDecision(true, modified, content, results);
    }

    public static OutputGuardrailDecision blocked(List<RuleResult> results) {
        return new OutputGuardrailDecision(false, false, null, results);
    }

    public boolean isBlocked() {
        return !passed;
    }
}
