package com.example.aigateway.infrastructure.provider;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.application.dto.ProviderResult;
import com.example.aigateway.application.dto.ProviderStreamEvent;
import com.example.aigateway.domain.provider.LlmProvider;
import com.example.aigateway.domain.provider.ProviderCapabilities;
import java.util.function.Consumer;
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
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities(true, true, true, true, false);
    }

    @Override
    public ProviderResult generate(AiGatewayCommand command) {
        return openAiApiClient.generate(command);
    }

    @Override
    public void stream(AiGatewayCommand command, Consumer<ProviderStreamEvent> eventConsumer) {
        openAiApiClient.stream(command, eventConsumer);
    }
}
