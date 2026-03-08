package com.example.aigateway.infrastructure.provider;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.application.dto.ProviderResult;
import com.example.aigateway.application.dto.ProviderStreamEvent;
import com.example.aigateway.application.dto.ProviderToolCall;
import com.example.aigateway.domain.provider.LlmProvider;
import com.example.aigateway.domain.provider.ProviderCapabilities;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class MockLlmProvider implements LlmProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities(true, true, true, true, false);
    }

    @Override
    public ProviderResult generate(AiGatewayCommand command) {
        if (command.tools() != null && !command.tools().isEmpty()) {
            AiGatewayCommand.ToolDefinition tool = command.tools().get(0);
            return new ProviderResult(
                    "",
                    List.of(new ProviderToolCall("mock-tool-1", "call-mock-1", tool.name(), "{\"mock\":true}"))
            );
        }
        if (command.responseFormat() != null) {
            return ProviderResult.text("""
                    {"provider":"mock","role":"%s","content":"%s"}
                    """.formatted(command.role(), escapeJson(command.prompt())).trim());
        }
        if (!command.messages().isEmpty()) {
            return ProviderResult.text("Mock provider 응답: [" + command.role() + "] " + command.messages().stream()
                    .map(message -> message.role() + ": " + message.content())
                    .collect(Collectors.joining(" | ")));
        }
        return ProviderResult.text("Mock provider 응답: [" + command.role() + "] " + command.prompt());
    }

    @Override
    public void stream(AiGatewayCommand command, Consumer<ProviderStreamEvent> eventConsumer) {
        ProviderResult result = generate(command);
        if (!result.toolCalls().isEmpty()) {
            result.toolCalls().forEach(toolCall -> eventConsumer.accept(ProviderStreamEvent.toolCall(toolCall, "mock-response")));
            eventConsumer.accept(ProviderStreamEvent.done("mock-response"));
            return;
        }
        String content = result.content() == null ? "" : result.content();
        for (int start = 0; start < content.length(); start += 24) {
            int end = Math.min(content.length(), start + 24);
            eventConsumer.accept(ProviderStreamEvent.textDelta(content.substring(start, end), "mock-response"));
        }
        eventConsumer.accept(ProviderStreamEvent.done("mock-response"));
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
