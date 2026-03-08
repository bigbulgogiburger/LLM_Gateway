package com.example.aigateway.application.dto;

import java.util.List;

public record ProviderResult(
        String content,
        List<ProviderToolCall> toolCalls,
        String responseId,
        String model,
        ProviderUsage usage
) {
    public ProviderResult {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static ProviderResult text(String content) {
        return new ProviderResult(content, List.of(), null, null, null);
    }

    public static ProviderResult text(String content, String responseId) {
        return new ProviderResult(content, List.of(), responseId, null, null);
    }

    public static ProviderResult text(String content, String responseId, String model, ProviderUsage usage) {
        return new ProviderResult(content, List.of(), responseId, model, usage);
    }
}
