package com.example.aigateway.domain.guardrail.service;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.domain.guardrail.result.OutputGuardrailDecision;
import com.example.aigateway.domain.guardrail.result.OutputRuleEvaluation;
import com.example.aigateway.domain.guardrail.result.RuleResult;
import com.example.aigateway.domain.guardrail.rule.OutputGuardrailRule;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OutputGuardrailService {

    private final List<OutputGuardrailRule> rules;

    public OutputGuardrailService(List<OutputGuardrailRule> rules) {
        this.rules = rules;
    }

    public OutputGuardrailDecision evaluate(String content, AiGatewayCommand command) {
        String currentContent = content;
        boolean modified = false;
        List<RuleResult> results = new ArrayList<>();

        for (OutputGuardrailRule rule : rules) {
            OutputRuleEvaluation evaluation = rule.evaluate(currentContent, command).orElse(null);
            if (evaluation == null) {
                continue;
            }
            results.add(evaluation.result());
            if (evaluation.blocks()) {
                return OutputGuardrailDecision.blocked(results);
            }
            if (evaluation.modifies()) {
                currentContent = evaluation.content();
                modified = true;
            }
        }

        return OutputGuardrailDecision.passed(currentContent, modified, results);
    }
}
