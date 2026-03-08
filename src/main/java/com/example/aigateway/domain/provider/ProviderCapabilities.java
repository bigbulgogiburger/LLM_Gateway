package com.example.aigateway.domain.provider;

public record ProviderCapabilities(
        boolean supportsMessages,
        boolean supportsStructuredOutputs,
        boolean supportsStreaming,
        boolean supportsToolUse,
        boolean supportsModeration
) {
}
