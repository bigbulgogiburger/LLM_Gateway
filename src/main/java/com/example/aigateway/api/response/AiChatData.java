package com.example.aigateway.api.response;

import java.util.List;

public record AiChatData(
        String content,
        List<ToolCallView> toolCalls
) {
    public AiChatData(String content) {
        this(content, List.of());
    }
}
