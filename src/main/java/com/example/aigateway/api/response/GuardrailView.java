package com.example.aigateway.api.response;

import java.util.List;

public record GuardrailView(
        boolean passed,
        List<RuleResultView> ruleResults,
        ModerationView inputModeration,
        AiGuardrailView aiResult,
        ModerationView outputModeration,
        OutputGuardrailView output
) {
}
