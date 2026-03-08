package com.example.aigateway.domain.guardrail.rule;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.domain.guardrail.result.GuardrailResultCode;
import com.example.aigateway.domain.guardrail.result.RuleResult;
import com.example.aigateway.domain.guardrail.service.GuardrailPolicyResolver;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RolePolicyRule implements GuardrailRule {

    private final GuardrailPolicyResolver policyResolver;

    public RolePolicyRule(GuardrailPolicyResolver policyResolver) {
        this.policyResolver = policyResolver;
    }

    @Override
    public Optional<RuleResult> evaluate(AiGatewayCommand command) {
        if (!"OPERATOR".equalsIgnoreCase(command.role())) {
            return Optional.empty();
        }
        String normalizedPrompt = command.prompt().toLowerCase(Locale.ROOT);
        return policyResolver.resolve(command.tenantId()).input().operatorRestrictedKeywords().stream()
                .filter(keyword -> normalizedPrompt.contains(keyword.toLowerCase(Locale.ROOT)))
                .findFirst()
                .map(keyword -> new RuleResult(
                        GuardrailResultCode.ROLE_POLICY_VIOLATION.name(),
                        "OPERATOR 역할에서는 허용되지 않은 요청입니다: " + keyword,
                        "BLOCK"
                ));
    }
}
