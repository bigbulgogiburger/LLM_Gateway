package com.example.aigateway.infrastructure.provider;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.application.dto.ProviderResult;
import com.example.aigateway.application.dto.ProviderStreamEvent;
import java.util.function.Consumer;

public interface OpenAiApiClient {
    ProviderResult generate(AiGatewayCommand command);

    void stream(AiGatewayCommand command, Consumer<ProviderStreamEvent> eventConsumer);
}
