package com.example.aigateway.domain.provider;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.application.dto.ProviderResult;
import com.example.aigateway.application.dto.ProviderStreamEvent;
import java.util.function.Consumer;

public interface LlmProvider {
    String name();

    ProviderCapabilities capabilities();

    ProviderResult generate(AiGatewayCommand command);

    default void stream(AiGatewayCommand command, Consumer<ProviderStreamEvent> eventConsumer) {
        ProviderResult result = generate(command);
        if (result.content() != null && !result.content().isBlank()) {
            eventConsumer.accept(ProviderStreamEvent.textDelta(result.content(), null));
        }
        result.toolCalls().forEach(toolCall -> eventConsumer.accept(ProviderStreamEvent.toolCall(toolCall, null)));
        eventConsumer.accept(ProviderStreamEvent.done(null));
    }
}
