package com.example.aigateway.domain.guardrail.rule;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.domain.guardrail.result.GuardrailResultCode;
import com.example.aigateway.domain.guardrail.result.RuleResult;
import com.example.aigateway.domain.guardrail.service.GuardrailPolicyResolver;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ForbiddenKeywordRule implements GuardrailRule {

    private final GuardrailPolicyResolver policyResolver;

    public ForbiddenKeywordRule(GuardrailPolicyResolver policyResolver) {
        this.policyResolver = policyResolver;
    }

    @Override
    public Optional<RuleResult> evaluate(AiGatewayCommand command) {
        String normalizedPrompt = command.prompt().toLowerCase(Locale.ROOT);
        return policyResolver.resolve(command.tenantId()).input().forbiddenKeywords().stream()
                .filter(keyword -> normalizedPrompt.contains(keyword.toLowerCase(Locale.ROOT)))
                .findFirst()
                .map(keyword -> new RuleResult(
                        GuardrailResultCode.FORBIDDEN_KEYWORD.name(),
                        "금지된 키워드가 포함되어 있습니다: " + keyword,
                        "BLOCK"
                ));
    }
}
