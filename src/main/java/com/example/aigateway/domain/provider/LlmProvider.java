package com.example.aigateway.domain.provider;

import com.example.aigateway.application.dto.AiGatewayCommand;

public interface LlmProvider {
    String name();

    String generate(AiGatewayCommand command);
}
