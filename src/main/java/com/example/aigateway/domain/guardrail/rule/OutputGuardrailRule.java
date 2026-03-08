package com.example.aigateway.domain.guardrail.rule;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.domain.guardrail.result.OutputRuleEvaluation;
import java.util.Optional;

public interface OutputGuardrailRule {
    Optional<OutputRuleEvaluation> evaluate(String content, AiGatewayCommand command);
}
