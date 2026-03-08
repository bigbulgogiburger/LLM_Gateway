package com.example.aigateway.infrastructure.guardrail;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.domain.guardrail.result.GuardrailResultCode;
import com.example.aigateway.domain.guardrail.result.OutputRuleEvaluation;
import com.example.aigateway.domain.guardrail.result.RuleResult;
import com.example.aigateway.domain.guardrail.rule.OutputGuardrailRule;
import com.example.aigateway.domain.guardrail.service.GuardrailPolicyResolver;
import com.example.aigateway.infrastructure.config.GuardrailProperties;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class OutputAiRiskRule implements OutputGuardrailRule {

    private final GuardrailPolicyResolver policyResolver;

    public OutputAiRiskRule(GuardrailPolicyResolver policyResolver) {
        this.policyResolver = policyResolver;
    }

    @Override
    public Optional<OutputRuleEvaluation> evaluate(String content, AiGatewayCommand command) {
        var policy = policyResolver.resolve(command.tenantId()).output();
        if (!policy.enabled()) {
            return Optional.empty();
        }

        String normalized = content.toLowerCase(Locale.ROOT);
        return policy.aiPolicies().stream()
                .filter(risk -> normalized.contains(risk.pattern().toLowerCase(Locale.ROOT)))
                .findFirst()
                .map(risk -> new OutputRuleEvaluation(
                        new RuleResult(
                                GuardrailResultCode.OUTPUT_POLICY_VIOLATION.name(),
                                risk.reason(),
                                "REVIEW".equalsIgnoreCase(risk.verdict()) ? "REVIEW" : "BLOCK"
                        ),
                        content
                ));
    }
}
