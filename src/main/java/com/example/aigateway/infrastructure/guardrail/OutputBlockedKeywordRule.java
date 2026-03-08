package com.example.aigateway.infrastructure.guardrail;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.domain.guardrail.result.GuardrailResultCode;
import com.example.aigateway.domain.guardrail.result.OutputRuleEvaluation;
import com.example.aigateway.domain.guardrail.result.RuleResult;
import com.example.aigateway.domain.guardrail.rule.OutputGuardrailRule;
import com.example.aigateway.domain.guardrail.service.GuardrailPolicyResolver;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class OutputBlockedKeywordRule implements OutputGuardrailRule {

    private final GuardrailPolicyResolver policyResolver;

    public OutputBlockedKeywordRule(GuardrailPolicyResolver policyResolver) {
        this.policyResolver = policyResolver;
    }

    @Override
    public Optional<OutputRuleEvaluation> evaluate(String content, AiGatewayCommand command) {
        var policy = policyResolver.resolve(command.tenantId()).output();
        if (!policy.enabled()) {
            return Optional.empty();
        }

        String normalizedContent = content.toLowerCase(Locale.ROOT);
        return policy.blockedKeywords().stream()
                .filter(keyword -> normalizedContent.contains(keyword.toLowerCase(Locale.ROOT)))
                .findFirst()
                .map(keyword -> new OutputRuleEvaluation(
                        new RuleResult(
                                GuardrailResultCode.OUTPUT_POLICY_VIOLATION.name(),
                                "출력에 민감한 키워드가 포함되어 있습니다: " + keyword,
                                "BLOCK"
                        ),
                        content
                ));
    }
}
