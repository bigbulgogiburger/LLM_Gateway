package com.example.aigateway.api.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record AiChatRequest(
        @NotBlank String userId,
        String role,
        @NotBlank String provider,
        String prompt,
        @Valid List<Message> messages,
        @Valid ResponseFormat responseFormat,
        Boolean stream,
        @Valid List<ToolDefinition> tools,
        @Valid ToolChoice toolChoice
) {

    public AiChatRequest(String userId, String role, String provider, String prompt) {
        this(userId, role, provider, prompt, null, null, false, null, null);
    }

    @AssertTrue(message = "prompt 또는 messages 중 하나는 필요합니다.")
    public boolean hasPromptOrMessages() {
        if (prompt != null && !prompt.isBlank()) {
            return true;
        }
        return messages != null && messages.stream()
                .anyMatch(message -> message != null && message.content() != null && !message.content().isBlank());
    }

    public record Message(
            @NotBlank String role,
            @NotBlank String content
    ) {
    }

    public record ResponseFormat(
            @NotBlank String type,
            String schemaName,
            Boolean strict,
            JsonNode schema
    ) {
    }

    public record ToolDefinition(
            @NotBlank String type,
            @NotBlank String name,
            String description,
            JsonNode inputSchema
    ) {
    }

    public record ToolChoice(
            @NotBlank String type,
            String name
    ) {
    }
}
