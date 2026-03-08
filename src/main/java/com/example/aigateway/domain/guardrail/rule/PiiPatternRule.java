package com.example.aigateway.domain.guardrail.rule;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.domain.guardrail.result.GuardrailResultCode;
import com.example.aigateway.domain.guardrail.result.RuleResult;
import com.example.aigateway.domain.guardrail.service.GuardrailPolicyResolver;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PiiPatternRule implements GuardrailRule {

    private final GuardrailPolicyResolver policyResolver;

    public PiiPatternRule(GuardrailPolicyResolver policyResolver) {
        this.policyResolver = policyResolver;
    }

    @Override
    public Optional<RuleResult> evaluate(AiGatewayCommand command) {
        return policyResolver.resolve(command.tenantId()).input().piiPatterns().stream()
                .filter(pattern -> pattern.compiled().matcher(command.prompt()).find())
                .findFirst()
                .map(pattern -> new RuleResult(
                        GuardrailResultCode.PII_DETECTED.name(),
                        pattern.description() + " 패턴이 포함되어 있습니다.",
                        pattern.action()
                ));
    }
}
