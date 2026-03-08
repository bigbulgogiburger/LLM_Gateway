package com.example.aigateway.infrastructure.guardrail;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.application.dto.ModerationAssessment;
import com.example.aigateway.domain.guardrail.result.AiGuardrailVerdict;
import com.example.aigateway.domain.guardrail.service.ModerationService;
import com.example.aigateway.infrastructure.config.ModerationProperties;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedModerationService implements ModerationService {

    private final ModerationProperties properties;

    public RuleBasedModerationService(ModerationProperties properties) {
        this.properties = properties;
    }

    @Override
    public ModerationAssessment assessInput(AiGatewayCommand command) {
        return assess("INPUT", command.prompt(), properties.input());
    }

    @Override
    public ModerationAssessment assessOutput(String content, AiGatewayCommand command) {
        return assess("OUTPUT", content, properties.output());
    }

    private ModerationAssessment assess(String phase, String content, ModerationProperties.PhasePolicy policy) {
        if (policy == null || !policy.enabled()) {
            return ModerationAssessment.safe(phase);
        }
        String normalizedContent = content == null ? "" : content.toLowerCase(Locale.ROOT);
        List<ModerationProperties.ModerationRule> rules = policy.rules() == null ? List.of() : policy.rules();
        return rules.stream()
                .filter(rule -> rule.pattern() != null && normalizedContent.contains(rule.pattern().toLowerCase(Locale.ROOT)))
                .map(rule -> new ModerationAssessment(
                        phase,
                        rule.category(),
                        AiGuardrailVerdict.valueOf(rule.verdict()),
                        rule.reason(),
                        rule.score()
                ))
                .findFirst()
                .orElseGet(() -> ModerationAssessment.safe(phase));
    }
}
