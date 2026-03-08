package com.example.aigateway.infrastructure.guardrail;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.domain.guardrail.result.GuardrailResultCode;
import com.example.aigateway.domain.guardrail.result.OutputRuleEvaluation;
import com.example.aigateway.domain.guardrail.result.RuleResult;
import com.example.aigateway.domain.guardrail.rule.OutputGuardrailRule;
import com.example.aigateway.domain.guardrail.service.GuardrailPolicyResolver;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class OutputPiiMaskingRule implements OutputGuardrailRule {

    private final GuardrailPolicyResolver policyResolver;

    public OutputPiiMaskingRule(GuardrailPolicyResolver policyResolver) {
        this.policyResolver = policyResolver;
    }

    @Override
    public Optional<OutputRuleEvaluation> evaluate(String content, AiGatewayCommand command) {
        var policy = policyResolver.resolve(command.tenantId()).output();
        if (!policy.enabled() || !policy.maskPii()) {
            return Optional.empty();
        }

        String masked = content;
        for (var pattern : policy.piiPatterns()) {
            masked = pattern.compiled().matcher(masked).replaceAll("[REDACTED]");
        }

        if (masked.equals(content)) {
            return Optional.empty();
        }

        return Optional.of(new OutputRuleEvaluation(
                new RuleResult(
                        GuardrailResultCode.OUTPUT_PII_MASKED.name(),
                        "출력 내 민감정보가 마스킹되었습니다.",
                        "MASK"
                ),
                masked
        ));
    }
}
