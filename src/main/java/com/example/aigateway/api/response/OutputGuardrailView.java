package com.example.aigateway.api.response;

import java.util.List;

public record OutputGuardrailView(
        boolean passed,
        boolean modified,
        List<RuleResultView> results
) {
}
