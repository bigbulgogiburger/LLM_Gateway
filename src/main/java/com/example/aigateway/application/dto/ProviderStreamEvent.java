package com.example.aigateway.application.dto;

public record ProviderStreamEvent(
        String type,
        String delta,
        ProviderToolCall toolCall,
        String responseId,
        String model,
        ProviderUsage usage
) {
    public static ProviderStreamEvent textDelta(String delta, String responseId) {
        return new ProviderStreamEvent("text_delta", delta, null, responseId, null, null);
    }

    public static ProviderStreamEvent toolCall(ProviderToolCall toolCall, String responseId) {
        return new ProviderStreamEvent("tool_call", null, toolCall, responseId, null, null);
    }

    public static ProviderStreamEvent done(String responseId) {
        return new ProviderStreamEvent("done", null, null, responseId, null, null);
    }

    public static ProviderStreamEvent done(String responseId, String model, ProviderUsage usage) {
        return new ProviderStreamEvent("done", null, null, responseId, model, usage);
    }
}
