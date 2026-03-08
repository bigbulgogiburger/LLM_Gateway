package com.example.aigateway.domain.guardrail.result;

public enum GuardrailResultCode {
    FORBIDDEN_KEYWORD,
    PII_DETECTED,
    PROMPT_TOO_LONG,
    ROLE_POLICY_VIOLATION,
    AI_GUARDRAIL_BLOCKED,
    OUTPUT_POLICY_VIOLATION,
    OUTPUT_PII_MASKED
}
