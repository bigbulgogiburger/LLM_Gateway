package com.example.aigateway.infrastructure.config;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.tenants")
public record GatewayTenantPolicyProperties(
        Map<String, TenantPolicyOverride> policies
) {
    public record TenantPolicyOverride(
            InputPolicyOverride input,
            AiPolicyOverride ai,
            OutputPolicyOverride output
    ) {
    }

    public record InputPolicyOverride(
            Integer maxPromptLength,
            List<String> forbiddenKeywords,
            List<String> operatorRestrictedKeywords,
            List<GuardrailProperties.PiiPatternPolicy> piiPatterns
    ) {
    }

    public record AiPolicyOverride(
            Boolean enabled,
            List<GuardrailProperties.AiRiskPolicy> policies
    ) {
    }

    public record OutputPolicyOverride(
            Boolean enabled,
            Boolean maskPii,
            List<String> blockedKeywords,
            List<GuardrailProperties.PiiPatternPolicy> piiPatterns,
            List<GuardrailProperties.AiRiskPolicy> aiPolicies
    ) {
    }
}
