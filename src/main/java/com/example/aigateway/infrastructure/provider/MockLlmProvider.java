package com.example.aigateway.infrastructure.provider;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.domain.provider.LlmProvider;
import org.springframework.stereotype.Component;

@Component
public class MockLlmProvider implements LlmProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public String generate(AiGatewayCommand command) {
        return "Mock provider 응답: [" + command.role() + "] " + command.prompt();
    }
}
