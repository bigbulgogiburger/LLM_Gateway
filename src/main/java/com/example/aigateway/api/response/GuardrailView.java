package com.example.aigateway.api.response;

import java.util.List;

public record GuardrailView(
        boolean passed,
        List<RuleResultView> ruleResults,
        AiGuardrailView aiResult,
        OutputGuardrailView output
) {
}
