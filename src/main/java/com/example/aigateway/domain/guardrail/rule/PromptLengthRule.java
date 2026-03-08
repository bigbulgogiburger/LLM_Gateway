package com.example.aigateway.domain.guardrail.rule;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.domain.guardrail.result.GuardrailResultCode;
import com.example.aigateway.domain.guardrail.result.RuleResult;
import com.example.aigateway.domain.guardrail.service.GuardrailPolicyResolver;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PromptLengthRule implements GuardrailRule {

    private final GuardrailPolicyResolver policyResolver;

    public PromptLengthRule(GuardrailPolicyResolver policyResolver) {
        this.policyResolver = policyResolver;
    }

    @Override
    public Optional<RuleResult> evaluate(AiGatewayCommand command) {
        int maxPromptLength = policyResolver.resolve(command.tenantId()).input().maxPromptLength();
        if (command.prompt().length() <= maxPromptLength) {
            return Optional.empty();
        }
        return Optional.of(new RuleResult(
                GuardrailResultCode.PROMPT_TOO_LONG.name(),
                "프롬프트 길이 제한을 초과했습니다. 최대 길이: " + maxPromptLength,
                "BLOCK"
        ));
    }
}
