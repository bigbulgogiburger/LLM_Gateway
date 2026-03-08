package com.example.aigateway.application.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record AiGatewayCommand(
        String requestId,
        String tenantId,
        String clientId,
        String userId,
        String role,
        String provider,
        String prompt,
        List<Message> messages,
        ResponseFormat responseFormat,
        boolean stream,
        List<ToolDefinition> tools,
        ToolChoice toolChoice
) {

    public AiGatewayCommand(
            String requestId,
            String tenantId,
            String clientId,
            String userId,
            String role,
            String provider,
            String prompt
    ) {
        this(requestId, tenantId, clientId, userId, role, provider, prompt, List.of(), null);
    }

    public AiGatewayCommand(
            String requestId,
            String tenantId,
            String clientId,
            String userId,
            String role,
            String provider,
            String prompt,
            List<Message> messages,
            ResponseFormat responseFormat
    ) {
        this(requestId, tenantId, clientId, userId, role, provider, prompt, messages, responseFormat, false, List.of(), null);
    }

    public record Message(String role, String content) {
    }

    public record ResponseFormat(String type, String schemaName, Boolean strict, JsonNode schema) {
    }

    public record ToolDefinition(String type, String name, String description, JsonNode inputSchema) {
    }

    public record ToolChoice(String type, String name) {
    }
}
