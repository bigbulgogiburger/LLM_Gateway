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
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    public void validatePrincipalScope(GatewayPrincipal principal, String toolName) {
        ExecutableTool tool = toolName == null ? null : toolsByName.get(toolName.toLowerCase());
        if (tool == null) {
            throw new GatewayException(
                    GatewayErrorCodes.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "등록되지 않은 tool 입니다: " + toolName
            );
        }
        if (!isAllowed(principal.role(), tool.allowedRoles())) {
            throw new GatewayException(
                    GatewayErrorCodes.FORBIDDEN,
                    HttpStatus.FORBIDDEN,
                    "현재 role로는 요청된 tool을 실행할 수 없습니다: " + tool.name()
            );
        }
        if (!isAllowed(principal.clientId(), tool.allowedClients())) {
            throw new GatewayException(
                    GatewayErrorCodes.FORBIDDEN,
                    HttpStatus.FORBIDDEN,
                    "현재 client로는 요청된 tool을 실행할 수 없습니다: " + tool.name()
            );
        }
        if (!isAllowed(principal.tenantId(), tool.allowedTenants())) {
            throw new GatewayException(
                    GatewayErrorCodes.FORBIDDEN,
                    HttpStatus.FORBIDDEN,
                    "현재 tenant로는 요청된 tool을 실행할 수 없습니다: " + tool.name()
            );
        }
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
        validatePrincipalScope(principal, toolCall.name());
        try {
            JsonNode arguments = toolCall.arguments() == null || toolCall.arguments().isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(toolCall.arguments());
            toolSchemaValidator.validateArguments(tool.name(), tool.inputSchema(), arguments);
            long startedAt = System.nanoTime();
            ToolAttemptOutcome attemptOutcome = executeWithRetry(tool, arguments);
            long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            SerializedToolOutput serializedOutput = serializeOutput(attemptOutcome.output());
            return new ToolExecutionResult(
                    toolCall.callId(),
                    toolCall.name(),
                    summarizeArguments(arguments),
                    serializedOutput.payload(),
                    durationMillis,
                    attemptOutcome.attempts(),
                    serializedOutput.truncated()
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

    private ToolAttemptOutcome executeWithRetry(ExecutableTool tool, JsonNode arguments) {
        int maxAttempts = Math.max(1, properties.retryAttempts() + 1);
        int attempts = 0;
        while (attempts < maxAttempts) {
            attempts++;
            try {
                return new ToolAttemptOutcome(executeWithTimeout(tool, arguments), attempts);
            } catch (GatewayException exception) {
                if (exception.status() == HttpStatus.BAD_REQUEST || exception.status() == HttpStatus.FORBIDDEN || !tool.isRetryable(exception)) {
                    throw exception;
                }
                if (attempts >= maxAttempts) {
                    throw exception;
                }
            } catch (CompletionException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                if (!tool.isRetryable(cause) || attempts >= maxAttempts) {
                    throw exception;
                }
            } catch (RuntimeException exception) {
                if (!tool.isRetryable(exception) || attempts >= maxAttempts) {
                    throw exception;
                }
            }
        }
        throw new GatewayException(
                GatewayErrorCodes.PROVIDER_EXECUTION_FAILED,
                HttpStatus.BAD_GATEWAY,
                "tool 실행에 실패했습니다: " + tool.name()
        );
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

    private SerializedToolOutput serializeOutput(JsonNode output) throws Exception {
        String serialized = objectMapper.writeValueAsString(output);
        if (serialized.length() <= properties.outputPreviewMaxChars()) {
            return new SerializedToolOutput(serialized, false);
        }
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("truncated", true);
        envelope.put("originalLength", serialized.length());
        envelope.put("contentPreview", serialized.substring(0, properties.outputPreviewMaxChars()) + "...");
        return new SerializedToolOutput(objectMapper.writeValueAsString(envelope), true);
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

    private boolean isAllowed(String actual, List<String> allowedValues) {
        if (allowedValues == null || allowedValues.isEmpty()) {
            return true;
        }
        if (actual == null || actual.isBlank()) {
            return false;
        }
        return allowedValues.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(actual));
    }

    private record ToolAttemptOutcome(
            JsonNode output,
            int attempts
    ) {
    }

    private record SerializedToolOutput(
            String payload,
            boolean truncated
    ) {
    }
}
