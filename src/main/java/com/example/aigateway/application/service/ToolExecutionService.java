package com.example.aigateway.application.service;

import com.example.aigateway.application.dto.ProviderToolCall;
import com.example.aigateway.application.dto.ToolExecutionResult;
import com.example.aigateway.common.exception.GatewayErrorCodes;
import com.example.aigateway.common.exception.GatewayException;
import com.example.aigateway.domain.security.GatewayPrincipal;
import com.example.aigateway.domain.tool.ExecutableTool;
import com.example.aigateway.infrastructure.config.ToolExecutionProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ToolExecutionService {

    private final ObjectMapper objectMapper;
    private final Map<String, ExecutableTool> toolsByName;
    private final ToolExecutionProperties properties;
    private final ToolSchemaValidator toolSchemaValidator;

    public ToolExecutionService(
            ObjectMapper objectMapper,
            List<ExecutableTool> tools,
            ToolExecutionProperties properties,
            ToolSchemaValidator toolSchemaValidator
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.toolSchemaValidator = toolSchemaValidator;
        this.toolsByName = tools.stream().collect(java.util.stream.Collectors.toMap(
                tool -> tool.name().toLowerCase(),
                Function.identity()
        ));
    }

    public boolean isRegistered(String toolName) {
        return toolName != null && toolsByName.containsKey(toolName.toLowerCase());
    }

    public JsonNode inputSchema(String toolName) {
        ExecutableTool tool = toolName == null ? null : toolsByName.get(toolName.toLowerCase());
        return tool == null ? null : tool.inputSchema();
    }

    public List<ToolExecutionResult> execute(GatewayPrincipal principal, List<ProviderToolCall> toolCalls) {
        return toolCalls.stream().map(toolCall -> executeOne(principal, toolCall)).toList();
    }

    private ToolExecutionResult executeOne(GatewayPrincipal principal, ProviderToolCall toolCall) {
        if (!principal.allowsTool(toolCall.name())) {
            throw new GatewayException(
                    GatewayErrorCodes.FORBIDDEN,
                    HttpStatus.FORBIDDEN,
                    "현재 API Key로는 요청된 tool을 실행할 수 없습니다: " + toolCall.name()
            );
        }
        ExecutableTool tool = toolsByName.get(toolCall.name().toLowerCase());
        if (tool == null) {
            throw new GatewayException(
                    GatewayErrorCodes.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "등록되지 않은 tool 입니다: " + toolCall.name()
            );
        }
        try {
            JsonNode arguments = toolCall.arguments() == null || toolCall.arguments().isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(toolCall.arguments());
            toolSchemaValidator.validateArguments(tool.name(), tool.inputSchema(), arguments);
            long startedAt = System.nanoTime();
            JsonNode output = executeWithTimeout(tool, arguments);
            long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            return new ToolExecutionResult(
                    toolCall.callId(),
                    toolCall.name(),
                    summarizeArguments(arguments),
                    objectMapper.writeValueAsString(output),
                    durationMillis
            );
        } catch (GatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GatewayException(
                    GatewayErrorCodes.PROVIDER_EXECUTION_FAILED,
                    HttpStatus.BAD_GATEWAY,
                    "tool 실행에 실패했습니다: " + toolCall.name()
            );
        }
    }

    private JsonNode executeWithTimeout(ExecutableTool tool, JsonNode arguments) {
        Duration timeout = tool.timeout();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            timeout = Duration.ofMillis(properties.executionTimeoutMillis());
        }
        try {
            return CompletableFuture.supplyAsync(() -> tool.execute(arguments))
                    .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof java.util.concurrent.TimeoutException) {
                throw new GatewayException(
                        GatewayErrorCodes.PROVIDER_EXECUTION_FAILED,
                        HttpStatus.BAD_GATEWAY,
                        "tool 실행 시간이 초과되었습니다: " + tool.name()
                );
            }
            if (exception.getCause() instanceof GatewayException gatewayException) {
                throw gatewayException;
            }
            throw exception;
        }
    }

    private String summarizeArguments(JsonNode arguments) {
        try {
            String json = objectMapper.writeValueAsString(arguments == null ? objectMapper.createObjectNode() : arguments);
            if (json.length() <= 200) {
                return json;
            }
            return json.substring(0, 200) + "...";
        } catch (Exception exception) {
            return "{}";
        }
    }
}
