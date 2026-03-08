package com.example.aigateway.application.dto;

import java.util.List;

public record ProviderResult(
        String content,
        List<ProviderToolCall> toolCalls
) {
    public ProviderResult {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static ProviderResult text(String content) {
        return new ProviderResult(content, List.of());
    }
}
