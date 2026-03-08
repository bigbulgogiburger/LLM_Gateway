package com.example.aigateway.infrastructure.provider;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.domain.provider.LlmProvider;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class MockLlmProvider implements LlmProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public String generate(AiGatewayCommand command) {
        if (command.responseFormat() != null) {
            return """
                    {"provider":"mock","role":"%s","content":"%s"}
                    """.formatted(command.role(), escapeJson(command.prompt())).trim();
        }
        if (!command.messages().isEmpty()) {
            return "Mock provider 응답: [" + command.role() + "] " + command.messages().stream()
                    .map(message -> message.role() + ": " + message.content())
                    .collect(Collectors.joining(" | "));
        }
        return "Mock provider 응답: [" + command.role() + "] " + command.prompt();
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
