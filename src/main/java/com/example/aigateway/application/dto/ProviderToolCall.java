package com.example.aigateway.application.dto;

public record ProviderToolCall(
        String id,
        String callId,
        String name,
        String arguments
) {
}
