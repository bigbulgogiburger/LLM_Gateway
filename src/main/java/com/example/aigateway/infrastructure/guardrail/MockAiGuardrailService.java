package com.example.aigateway.infrastructure.guardrail;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.application.dto.AiGuardrailAssessment;
import com.example.aigateway.domain.guardrail.result.AiGuardrailVerdict;
import com.example.aigateway.domain.guardrail.service.GuardrailPolicyResolver;
import com.example.aigateway.domain.guardrail.service.AiGuardrailService;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class MockAiGuardrailService implements AiGuardrailService {

    private final GuardrailPolicyResolver policyResolver;

    public MockAiGuardrailService(GuardrailPolicyResolver policyResolver) {
        this.policyResolver = policyResolver;
    }

    @Override
    public AiGuardrailAssessment assess(AiGatewayCommand command) {
        var resolvedPolicy = policyResolver.resolve(command.tenantId()).ai();
        if (!resolvedPolicy.enabled()) {
            return AiGuardrailAssessment.safe();
        }
        String normalizedPrompt = command.prompt().toLowerCase(Locale.ROOT);
        return resolvedPolicy.policies().stream()
                .filter(riskPolicy -> normalizedPrompt.contains(riskPolicy.pattern().toLowerCase(Locale.ROOT)))
                .map(riskPolicy -> new AiGuardrailAssessment(
                        AiGuardrailVerdict.valueOf(riskPolicy.verdict()),
                        riskPolicy.reason(),
                        riskPolicy.score()
                ))
                .findFirst()
                .orElseGet(AiGuardrailAssessment::safe);
    }
}
