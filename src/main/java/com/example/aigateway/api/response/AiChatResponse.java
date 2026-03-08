package com.example.aigateway.api.response;

public record AiChatResponse(
        String status,
        String requestId,
        String tenantId,
        String clientId,
        String provider,
        GuardrailView guardrail,
        AiChatData data
) {
}
