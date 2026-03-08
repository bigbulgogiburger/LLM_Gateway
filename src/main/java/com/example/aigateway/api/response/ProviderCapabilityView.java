package com.example.aigateway.api.response;

public record ProviderCapabilityView(
        String provider,
        boolean supportsMessages,
        boolean supportsStructuredOutputs,
        boolean supportsStreaming,
        boolean supportsToolUse,
        boolean supportsModeration
) {
}
