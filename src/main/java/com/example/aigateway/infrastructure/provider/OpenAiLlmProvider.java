package com.example.aigateway.infrastructure.provider;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.domain.provider.LlmProvider;
import org.springframework.stereotype.Component;

@Component
public class OpenAiLlmProvider implements LlmProvider {

    private final OpenAiApiClient openAiApiClient;

    public OpenAiLlmProvider(OpenAiApiClient openAiApiClient) {
        this.openAiApiClient = openAiApiClient;
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public String generate(AiGatewayCommand command) {
        return openAiApiClient.generate(command);
    }
}
