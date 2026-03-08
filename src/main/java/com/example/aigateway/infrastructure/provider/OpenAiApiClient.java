package com.example.aigateway.infrastructure.provider;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.application.dto.ProviderResult;
import com.example.aigateway.application.dto.ProviderStreamEvent;
import com.example.aigateway.application.dto.ToolExecutionResult;
import java.util.List;
import java.util.function.Consumer;

public interface OpenAiApiClient {
    ProviderResult generate(AiGatewayCommand command);

    ProviderResult continueWithToolOutputs(
            AiGatewayCommand command,
            ProviderResult previousResult,
            List<ToolExecutionResult> toolResults
    );

    void stream(AiGatewayCommand command, Consumer<ProviderStreamEvent> eventConsumer);

    void streamWithToolOutputs(
            AiGatewayCommand command,
            ProviderResult previousResult,
            List<ToolExecutionResult> toolResults,
            Consumer<ProviderStreamEvent> eventConsumer
    );
}
