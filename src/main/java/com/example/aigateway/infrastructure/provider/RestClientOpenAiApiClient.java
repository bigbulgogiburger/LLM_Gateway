package com.example.aigateway.infrastructure.provider;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.common.exception.GatewayErrorCodes;
import com.example.aigateway.common.exception.GatewayException;
import com.example.aigateway.infrastructure.config.OpenAiProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RestClientOpenAiApiClient implements OpenAiApiClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientOpenAiApiClient.class);

    private final RestClient restClient;
    private final OpenAiProperties properties;

    public RestClientOpenAiApiClient(RestClient openAiRestClient, OpenAiProperties properties) {
        this.restClient = openAiRestClient;
        this.properties = properties;
    }

    @Override
    public String generate(AiGatewayCommand command) {
        if (!properties.enabled()) {
            throw new GatewayException(GatewayErrorCodes.PROVIDER_EXECUTION_FAILED, HttpStatus.BAD_GATEWAY,
                    "OpenAI provider 가 비활성화되어 있습니다.");
        }
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new GatewayException(GatewayErrorCodes.PROVIDER_EXECUTION_FAILED, HttpStatus.BAD_GATEWAY,
                    "OPENAI_API_KEY 가 설정되어 있지 않습니다.");
        }

        OpenAiChatCompletionRequest request = new OpenAiChatCompletionRequest(
                properties.model(),
                buildMessages(command),
                properties.maxCompletionTokens(),
                buildResponseFormat(command.responseFormat())
        );

        OpenAiChatCompletionResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                    .body(request)
                    .retrieve()
                    .body(OpenAiChatCompletionResponse.class);
        } catch (RestClientException exception) {
            log.warn("OpenAI API invocation failed", exception);
            throw new GatewayException(GatewayErrorCodes.PROVIDER_EXECUTION_FAILED, HttpStatus.BAD_GATEWAY,
                    "OpenAI 호출에 실패했습니다.");
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new GatewayException(GatewayErrorCodes.PROVIDER_EXECUTION_FAILED, HttpStatus.BAD_GATEWAY,
                    "OpenAI 응답에 생성 결과가 없습니다.");
        }

        OpenAiChoice choice = response.choices().get(0);
        if (choice.message() == null || !StringUtils.hasText(choice.message().content())) {
            throw new GatewayException(GatewayErrorCodes.PROVIDER_EXECUTION_FAILED, HttpStatus.BAD_GATEWAY,
                    "OpenAI 응답 content 가 비어 있습니다.");
        }
        return choice.message().content().trim();
    }

    private record OpenAiChatCompletionRequest(
            String model,
            List<OpenAiMessage> messages,
            Integer max_completion_tokens,
            OpenAiResponseFormat response_format
    ) {
    }

    private record OpenAiMessage(String role, String content) {
    }

    private record OpenAiChatCompletionResponse(List<OpenAiChoice> choices) {
    }

    private record OpenAiChoice(OpenAiAssistantMessage message) {
    }

    private record OpenAiAssistantMessage(String content) {
    }

    private List<OpenAiMessage> buildMessages(AiGatewayCommand command) {
        String developerMessage = properties.developerMessage() + responseFormatInstruction(command.responseFormat());
        if (command.messages() == null || command.messages().isEmpty()) {
            return List.of(
                    new OpenAiMessage("developer", developerMessage),
                    new OpenAiMessage("user", command.prompt())
            );
        }
        List<OpenAiMessage> messages = command.messages().stream()
                .map(message -> new OpenAiMessage(message.role(), message.content()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        messages.add(0, new OpenAiMessage("developer", developerMessage));
        return messages;
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

    private OpenAiResponseFormat buildResponseFormat(AiGatewayCommand.ResponseFormat responseFormat) {
        if (responseFormat == null || !StringUtils.hasText(responseFormat.type())) {
            return null;
        }
        if ("json_schema".equalsIgnoreCase(responseFormat.type())) {
            return new OpenAiResponseFormat(
                    "json_schema",
                    new OpenAiJsonSchema(
                            StringUtils.hasText(responseFormat.schemaName()) ? responseFormat.schemaName() : "gateway_response",
                            responseFormat.strict() == null || responseFormat.strict(),
                            responseFormat.schema()
                    )
            );
        }
        if ("json_object".equalsIgnoreCase(responseFormat.type())) {
            return new OpenAiResponseFormat("json_object", null);
        }
        throw new GatewayException(
                GatewayErrorCodes.VALIDATION_ERROR,
                HttpStatus.BAD_REQUEST,
                "지원하지 않는 responseFormat.type 입니다."
        );
    }

    private record OpenAiResponseFormat(String type, OpenAiJsonSchema json_schema) {
    }

    private record OpenAiJsonSchema(String name, boolean strict, Object schema) {
    }
}
