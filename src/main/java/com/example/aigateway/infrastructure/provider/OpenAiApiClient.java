package com.example.aigateway.infrastructure.provider;

import com.example.aigateway.application.dto.AiGatewayCommand;

public interface OpenAiApiClient {
    String generate(AiGatewayCommand command);
}
