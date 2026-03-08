package com.example.aigateway.api.response;

public record AiChatStreamEvent(
        String type,
        String requestId,
        String provider,
        String status,
        String content,
        ToolCallView toolCall
) {
}
