package com.example.aigateway.application.dto;

public record ProviderStreamEvent(
        String type,
        String delta,
        ProviderToolCall toolCall,
        String responseId
) {
    public static ProviderStreamEvent textDelta(String delta, String responseId) {
        return new ProviderStreamEvent("text_delta", delta, null, responseId);
    }

    public static ProviderStreamEvent toolCall(ProviderToolCall toolCall, String responseId) {
        return new ProviderStreamEvent("tool_call", null, toolCall, responseId);
    }

    public static ProviderStreamEvent done(String responseId) {
        return new ProviderStreamEvent("done", null, null, responseId);
    }
}
