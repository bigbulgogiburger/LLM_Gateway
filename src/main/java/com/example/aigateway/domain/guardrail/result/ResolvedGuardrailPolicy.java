package com.example.aigateway.domain.guardrail.result;

import com.example.aigateway.infrastructure.config.GuardrailProperties;
import java.util.List;

public record ResolvedGuardrailPolicy(
        InputPolicy input,
        AiPolicy ai,
        OutputPolicy output
) {
    public record InputPolicy(
            int maxPromptLength,
            List<String> forbiddenKeywords,
            List<String> operatorRestrictedKeywords,
            List<GuardrailProperties.PiiPatternPolicy> piiPatterns
    ) {
    }

    public record AiPolicy(
            boolean enabled,
            List<GuardrailProperties.AiRiskPolicy> policies
    ) {
    }

    public record OutputPolicy(
            boolean enabled,
            boolean maskPii,
            List<String> blockedKeywords,
            List<GuardrailProperties.PiiPatternPolicy> piiPatterns,
            List<GuardrailProperties.AiRiskPolicy> aiPolicies
    ) {
    }
}
