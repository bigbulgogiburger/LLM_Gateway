package com.example.aigateway.infrastructure.config;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.guardrail")
public record GuardrailProperties(
        InputPolicy input,
        AiPolicy ai,
        OutputPolicy output
) {
    public record InputPolicy(
            int maxPromptLength,
            List<String> forbiddenKeywords,
            List<String> operatorRestrictedKeywords,
            List<PiiPatternPolicy> piiPatterns
    ) {
    }

    public record AiPolicy(
            boolean enabled,
            List<AiRiskPolicy> policies
    ) {
    }

    public record OutputPolicy(
            boolean enabled,
            boolean maskPii,
            List<String> blockedKeywords,
            List<PiiPatternPolicy> piiPatterns,
            List<AiRiskPolicy> aiPolicies
    ) {
    }

    public record AiRiskPolicy(
            String pattern,
            String verdict,
            String reason,
            double score
    ) {
    }

    public record PiiPatternPolicy(
            String description,
            String regex,
            String action
    ) {
        public Pattern compiled() {
            return Pattern.compile(regex);
        }
    }
}
