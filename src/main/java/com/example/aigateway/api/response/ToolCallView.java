package com.example.aigateway.api.response;

public record ToolCallView(
        String id,
        String callId,
        String name,
        String arguments
) {
}
