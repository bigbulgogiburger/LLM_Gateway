package com.example.aigateway.domain.provider;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.application.dto.ProviderResult;
import com.example.aigateway.application.dto.ProviderStreamEvent;
import com.example.aigateway.application.dto.ToolExecutionResult;
import java.util.function.Consumer;

public interface LlmProvider {
    String name();

    ProviderCapabilities capabilities();

    ProviderResult generate(AiGatewayCommand command);

    default ProviderResult continueWithToolOutputs(
            AiGatewayCommand command,
            ProviderResult previousResult,
            java.util.List<ToolExecutionResult> toolResults
    ) {
        return previousResult;
    }

    default void stream(AiGatewayCommand command, Consumer<ProviderStreamEvent> eventConsumer) {
        ProviderResult result = generate(command);
        if (result.content() != null && !result.content().isBlank()) {
            eventConsumer.accept(ProviderStreamEvent.textDelta(result.content(), result.responseId()));
        }
        result.toolCalls().forEach(toolCall -> eventConsumer.accept(ProviderStreamEvent.toolCall(toolCall, null)));
        eventConsumer.accept(ProviderStreamEvent.done(result.responseId(), result.model(), result.usage()));
    }

    default void streamWithToolOutputs(
            AiGatewayCommand command,
            ProviderResult previousResult,
            java.util.List<ToolExecutionResult> toolResults,
            Consumer<ProviderStreamEvent> eventConsumer
    ) {
        ProviderResult nextResult = continueWithToolOutputs(command, previousResult, toolResults);
        if (nextResult.content() != null && !nextResult.content().isBlank()) {
            eventConsumer.accept(ProviderStreamEvent.textDelta(nextResult.content(), nextResult.responseId()));
        }
        nextResult.toolCalls().forEach(toolCall -> eventConsumer.accept(ProviderStreamEvent.toolCall(toolCall, nextResult.responseId())));
        eventConsumer.accept(ProviderStreamEvent.done(nextResult.responseId(), nextResult.model(), nextResult.usage()));
    }
}
