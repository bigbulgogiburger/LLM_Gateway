package com.example.aigateway.infrastructure.provider;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.application.dto.ProviderResult;
import com.example.aigateway.application.dto.ProviderStreamEvent;
import com.example.aigateway.application.dto.ProviderToolCall;
import com.example.aigateway.common.exception.GatewayErrorCodes;
import com.example.aigateway.common.exception.GatewayException;
import com.example.aigateway.infrastructure.config.OpenAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RestClientOpenAiApiClient implements OpenAiApiClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientOpenAiApiClient.class);

    private final HttpClient httpClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public RestClientOpenAiApiClient(HttpClient openAiHttpClient, OpenAiProperties properties, ObjectMapper objectMapper) {
        this.httpClient = openAiHttpClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProviderResult generate(AiGatewayCommand command) {
        ensureConfigured();
        try {
            HttpRequest request = baseRequestBuilder()
                    .uri(URI.create(normalizeBaseUrl() + "/responses"))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(buildRequestBody(command, false))))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode body = parseResponseBody(response);
            return extractProviderResult(body);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("OpenAI responses API invocation failed", exception);
            throw new GatewayException(GatewayErrorCodes.PROVIDER_EXECUTION_FAILED, HttpStatus.BAD_GATEWAY,
                    "OpenAI 호출에 실패했습니다.");
        }
    }

    @Override
    public void stream(AiGatewayCommand command, Consumer<ProviderStreamEvent> eventConsumer) {
        ensureConfigured();
        try {
            HttpRequest request = baseRequestBuilder()
                    .uri(URI.create(normalizeBaseUrl() + "/responses"))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(buildRequestBody(command, true))))
                    .build();
            HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new GatewayException(
                        GatewayErrorCodes.PROVIDER_EXECUTION_FAILED,
                        HttpStatus.BAD_GATEWAY,
                        "OpenAI streaming 호출에 실패했습니다."
                );
            }
            readStream(response.body(), eventConsumer);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("OpenAI responses streaming invocation failed", exception);
            throw new GatewayException(GatewayErrorCodes.PROVIDER_EXECUTION_FAILED, HttpStatus.BAD_GATEWAY,
                    "OpenAI streaming 호출에 실패했습니다.");
        }
    }

    private void readStream(java.io.InputStream inputStream, Consumer<ProviderStreamEvent> eventConsumer) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            String eventType = null;
            StringBuilder data = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    dispatchEvent(eventType, data.toString(), eventConsumer);
                    eventType = null;
                    data.setLength(0);
                    continue;
                }
                if (line.startsWith("event:")) {
                    eventType = line.substring(6).trim();
                    continue;
                }
                if (line.startsWith("data:")) {
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(line.substring(5).trim());
                }
            }
            if (data.length() > 0) {
                dispatchEvent(eventType, data.toString(), eventConsumer);
            }
        }
    }

    private void dispatchEvent(String eventType, String data, Consumer<ProviderStreamEvent> eventConsumer) throws IOException {
        if (!StringUtils.hasText(data) || "[DONE]".equals(data)) {
            return;
        }
        JsonNode node = objectMapper.readTree(data);
        String type = StringUtils.hasText(eventType) ? eventType : node.path("type").asText();
        String responseId = node.path("response_id").asText(null);
        if ("response.output_text.delta".equals(type)) {
            eventConsumer.accept(ProviderStreamEvent.textDelta(node.path("delta").asText(""), responseId));
            return;
        }
        if ("response.output_item.done".equals(type)) {
            JsonNode item = node.path("item");
            if ("function_call".equals(item.path("type").asText())) {
                eventConsumer.accept(ProviderStreamEvent.toolCall(new ProviderToolCall(
                        item.path("id").asText(null),
                        item.path("call_id").asText(null),
                        item.path("name").asText(null),
                        item.path("arguments").asText("{}")
                ), responseId));
            }
            return;
        }
        if ("response.completed".equals(type)) {
            eventConsumer.accept(ProviderStreamEvent.done(responseId));
            return;
        }
        if ("response.error".equals(type)) {
            throw new GatewayException(
                    GatewayErrorCodes.PROVIDER_EXECUTION_FAILED,
                    HttpStatus.BAD_GATEWAY,
                    "OpenAI streaming 응답에 오류가 포함되어 있습니다."
            );
        }
    }

    private ProviderResult extractProviderResult(JsonNode body) {
        List<ProviderToolCall> toolCalls = new ArrayList<>();
        JsonNode output = body.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                if ("function_call".equals(item.path("type").asText())) {
                    toolCalls.add(new ProviderToolCall(
                            item.path("id").asText(null),
                            item.path("call_id").asText(null),
                            item.path("name").asText(null),
                            item.path("arguments").asText("{}")
                    ));
                }
            }
        }
        String text = body.path("output_text").asText("");
        if (!StringUtils.hasText(text)) {
            text = extractTextFromOutput(output);
        }
        if (!StringUtils.hasText(text) && toolCalls.isEmpty()) {
            throw new GatewayException(
                    GatewayErrorCodes.PROVIDER_EXECUTION_FAILED,
                    HttpStatus.BAD_GATEWAY,
                    "OpenAI 응답에 생성 결과가 없습니다."
            );
        }
        return new ProviderResult(text == null ? "" : text.trim(), toolCalls);
    }

    private String extractTextFromOutput(JsonNode output) {
        if (!output.isArray()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode part : content) {
                if ("output_text".equals(part.path("type").asText()) || "text".equals(part.path("type").asText())) {
                    if (text.length() > 0) {
                        text.append('\n');
                    }
                    text.append(part.path("text").asText(""));
                }
            }
        }
        return text.toString();
    }

    private Map<String, Object> buildRequestBody(AiGatewayCommand command, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("input", buildInput(command));
        body.put("max_output_tokens", properties.maxCompletionTokens());
        if (command.responseFormat() != null) {
            body.put("text", Map.of("format", buildResponseFormat(command.responseFormat())));
        }
        if (command.tools() != null && !command.tools().isEmpty()) {
            body.put("tools", buildTools(command.tools()));
        }
        if (command.toolChoice() != null) {
            body.put("tool_choice", buildToolChoice(command.toolChoice()));
        }
        if (stream || command.stream()) {
            body.put("stream", true);
        }
        return body;
    }

    private List<Map<String, Object>> buildInput(AiGatewayCommand command) {
        List<Map<String, Object>> input = new ArrayList<>();
        input.add(Map.of(
                "role", "system",
                "content", properties.developerMessage() + responseFormatInstruction(command.responseFormat())
        ));
        if (command.messages() == null || command.messages().isEmpty()) {
            input.add(Map.of("role", "user", "content", command.prompt()));
            return input;
        }
        command.messages().forEach(message -> input.add(Map.of(
                "role", normalizeRole(message.role()),
                "content", message.content()
        )));
        return input;
    }

    private List<Map<String, Object>> buildTools(List<AiGatewayCommand.ToolDefinition> tools) {
        return tools.stream()
                .map(tool -> {
                    Map<String, Object> definition = new LinkedHashMap<>();
                    definition.put("type", tool.type());
                    definition.put("name", tool.name());
                    if (StringUtils.hasText(tool.description())) {
                        definition.put("description", tool.description());
                    }
                    if (tool.inputSchema() != null) {
                        definition.put("parameters", tool.inputSchema());
                    }
                    return definition;
                })
                .toList();
    }

    private Object buildToolChoice(AiGatewayCommand.ToolChoice toolChoice) {
        if (!"function".equalsIgnoreCase(toolChoice.type())) {
            return toolChoice.type();
        }
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("type", "function");
        choice.put("name", toolChoice.name());
        return choice;
    }

    private Object buildResponseFormat(AiGatewayCommand.ResponseFormat responseFormat) {
        if ("json_schema".equalsIgnoreCase(responseFormat.type())) {
            return Map.of(
                    "type", "json_schema",
                    "name", StringUtils.hasText(responseFormat.schemaName()) ? responseFormat.schemaName() : "gateway_response",
                    "schema", responseFormat.schema(),
                    "strict", responseFormat.strict() == null || responseFormat.strict()
            );
        }
        if ("json_object".equalsIgnoreCase(responseFormat.type())) {
            return Map.of("type", "json_object");
        }
        throw new GatewayException(
                GatewayErrorCodes.VALIDATION_ERROR,
                HttpStatus.BAD_REQUEST,
                "지원하지 않는 responseFormat.type 입니다."
        );
    }

    private HttpRequest.Builder baseRequestBuilder() {
        return HttpRequest.newBuilder()
                .timeout(Duration.ofMillis(properties.readTimeoutMillis()))
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json");
    }

    private JsonNode parseResponseBody(HttpResponse<String> response) throws IOException {
        if (response.statusCode() >= 400) {
            log.warn("OpenAI responses API returned error status {}: {}", response.statusCode(), response.body());
            throw new GatewayException(GatewayErrorCodes.PROVIDER_EXECUTION_FAILED, HttpStatus.BAD_GATEWAY,
                    "OpenAI 호출에 실패했습니다.");
        }
        return objectMapper.readTree(response.body());
    }

    private void ensureConfigured() {
        if (!properties.enabled()) {
            throw new GatewayException(GatewayErrorCodes.PROVIDER_EXECUTION_FAILED, HttpStatus.BAD_GATEWAY,
                    "OpenAI provider 가 비활성화되어 있습니다.");
        }
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new GatewayException(GatewayErrorCodes.PROVIDER_EXECUTION_FAILED, HttpStatus.BAD_GATEWAY,
                    "OPENAI_API_KEY 가 설정되어 있지 않습니다.");
        }
    }

    private String normalizeBaseUrl() {
        return properties.baseUrl().endsWith("/") ? properties.baseUrl().substring(0, properties.baseUrl().length() - 1) : properties.baseUrl();
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "user";
        }
        if ("developer".equalsIgnoreCase(role)) {
            return "system";
        }
        return role.toLowerCase();
    }

    private String responseFormatInstruction(AiGatewayCommand.ResponseFormat responseFormat) {
        if (responseFormat == null || !StringUtils.hasText(responseFormat.type())) {
            return "";
        }
        if ("json_object".equalsIgnoreCase(responseFormat.type())) {
            return "\nReturn valid JSON only.";
        }
        return "";
    }
}
