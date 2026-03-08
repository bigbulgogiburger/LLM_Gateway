package com.example.aigateway.domain.guardrail.rule;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.domain.guardrail.result.RuleResult;
import java.util.Optional;

public interface GuardrailRule {
    Optional<RuleResult> evaluate(AiGatewayCommand command);
}
