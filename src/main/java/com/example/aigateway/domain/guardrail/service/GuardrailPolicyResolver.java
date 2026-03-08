package com.example.aigateway.domain.guardrail.service;

import com.example.aigateway.domain.guardrail.result.ResolvedGuardrailPolicy;
import com.example.aigateway.application.service.TenantPolicyAdminService;
import com.example.aigateway.infrastructure.config.GatewayTenantPolicyProperties;
import com.example.aigateway.infrastructure.config.GuardrailProperties;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GuardrailPolicyResolver {

    private final GuardrailProperties defaults;
    private final GatewayTenantPolicyProperties tenantPolicies;
    private final TenantPolicyAdminService adminService;

    public GuardrailPolicyResolver(
            GuardrailProperties defaults,
            GatewayTenantPolicyProperties tenantPolicies,
            TenantPolicyAdminService adminService
    ) {
        this.defaults = defaults;
        this.tenantPolicies = tenantPolicies;
        this.adminService = adminService;
    }

    public ResolvedGuardrailPolicy resolve(String tenantId) {
        GatewayTenantPolicyProperties.TenantPolicyOverride override = adminService.get(tenantId);
        if (override == null) {
            override = tenantPolicies.policies() == null
                    ? null
                    : tenantPolicies.policies().get(tenantId);
        }

        GuardrailProperties.InputPolicy defaultInput = defaults.input();
        GuardrailProperties.AiPolicy defaultAi = defaults.ai();
        GuardrailProperties.OutputPolicy defaultOutput = defaults.output();

        return new ResolvedGuardrailPolicy(
                new ResolvedGuardrailPolicy.InputPolicy(
                        override != null && override.input() != null && override.input().maxPromptLength() != null
                                ? override.input().maxPromptLength()
                                : defaultInput.maxPromptLength(),
                        listOrDefault(override != null && override.input() != null ? override.input().forbiddenKeywords() : null, defaultInput.forbiddenKeywords()),
                        listOrDefault(override != null && override.input() != null ? override.input().operatorRestrictedKeywords() : null, defaultInput.operatorRestrictedKeywords()),
                        listOrDefault(override != null && override.input() != null ? override.input().piiPatterns() : null, defaultInput.piiPatterns())
                ),
                new ResolvedGuardrailPolicy.AiPolicy(
                        override != null && override.ai() != null && override.ai().enabled() != null
                                ? override.ai().enabled()
                                : defaultAi.enabled(),
                        listOrDefault(override != null && override.ai() != null ? override.ai().policies() : null, defaultAi.policies())
                ),
                new ResolvedGuardrailPolicy.OutputPolicy(
                        override != null && override.output() != null && override.output().enabled() != null
                                ? override.output().enabled()
                                : defaultOutput.enabled(),
                        override != null && override.output() != null && override.output().maskPii() != null
                                ? override.output().maskPii()
                                : defaultOutput.maskPii(),
                        listOrDefault(override != null && override.output() != null ? override.output().blockedKeywords() : null, defaultOutput.blockedKeywords()),
                        listOrDefault(override != null && override.output() != null ? override.output().piiPatterns() : null, defaultOutput.piiPatterns()),
                        listOrDefault(override != null && override.output() != null ? override.output().aiPolicies() : null, defaultOutput.aiPolicies())
                )
        );
    }

    private <T> List<T> listOrDefault(List<T> override, List<T> defaults) {
        return override == null ? defaults : override;
    }
}
