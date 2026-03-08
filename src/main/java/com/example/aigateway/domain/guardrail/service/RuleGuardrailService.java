package com.example.aigateway.domain.guardrail.service;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.domain.guardrail.result.GuardrailDecision;
import com.example.aigateway.domain.guardrail.result.RuleResult;
import com.example.aigateway.domain.guardrail.rule.GuardrailRule;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class RuleGuardrailService {

    private final List<GuardrailRule> guardrailRules;

    public RuleGuardrailService(List<GuardrailRule> guardrailRules) {
        this.guardrailRules = guardrailRules;
    }

    public GuardrailDecision evaluate(AiGatewayCommand command) {
        List<RuleResult> results = guardrailRules.stream()
                .map(rule -> rule.evaluate(command))
                .flatMap(Optional::stream)
                .toList();
        if (results.isEmpty()) {
            return GuardrailDecision.passed(List.of());
        }
        return GuardrailDecision.blocked(results);
    }
}
