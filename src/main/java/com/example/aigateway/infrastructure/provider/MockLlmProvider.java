package com.example.aigateway.infrastructure.provider;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.application.dto.ProviderResult;
import com.example.aigateway.application.dto.ProviderStreamEvent;
import com.example.aigateway.application.dto.ProviderToolCall;
import com.example.aigateway.application.dto.ProviderUsage;
import com.example.aigateway.application.dto.ToolExecutionResult;
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
            return new ProviderResult(
                    "",
                    command.tools().stream()
                            .map(this::toToolCall)
                            .toList(),
                    "mock-response",
                    "mock-gateway",
                    estimateUsage(command.prompt(), "")
            );
        }
        if (command.responseFormat() != null) {
            return ProviderResult.text("""
                    {"provider":"mock","role":"%s","content":"%s"}
                    """.formatted(command.role(), escapeJson(command.prompt())).trim(), "mock-response", "mock-gateway",
                    estimateUsage(command.prompt(), command.prompt()));
        }
        if (!command.messages().isEmpty()) {
            String content = "Mock provider 응답: [" + command.role() + "] " + command.messages().stream()
                    .map(message -> message.role() + ": " + message.content())
                    .collect(Collectors.joining(" | "));
            return ProviderResult.text(content, "mock-response", "mock-gateway", estimateUsage(command.prompt(), content));
        }
        String content = "Mock provider 응답: [" + command.role() + "] " + command.prompt();
        return ProviderResult.text(content, "mock-response", "mock-gateway", estimateUsage(command.prompt(), content));
    }

    @Override
    public ProviderResult continueWithToolOutputs(
            AiGatewayCommand command,
            ProviderResult previousResult,
            List<ToolExecutionResult> toolResults
    ) {
        String combined = toolResults.stream()
                .map(result -> result.toolName() + "=" + result.output())
                .collect(Collectors.joining(", "));
        String content = "Mock provider 최종 응답: " + combined;
        return ProviderResult.text(content, "mock-followup", "mock-gateway", estimateUsage(command.prompt(), content));
    }

    @Override
    public void stream(AiGatewayCommand command, Consumer<ProviderStreamEvent> eventConsumer) {
        ProviderResult result = generate(command);
        if (!result.toolCalls().isEmpty()) {
            result.toolCalls().forEach(toolCall -> eventConsumer.accept(ProviderStreamEvent.toolCall(toolCall, "mock-response")));
            eventConsumer.accept(ProviderStreamEvent.done("mock-response", result.model(), result.usage()));
            return;
        }
        String content = result.content() == null ? "" : result.content();
        for (int start = 0; start < content.length(); start += 24) {
            int end = Math.min(content.length(), start + 24);
            eventConsumer.accept(ProviderStreamEvent.textDelta(content.substring(start, end), result.responseId()));
        }
        eventConsumer.accept(ProviderStreamEvent.done(result.responseId(), result.model(), result.usage()));
    }

    @Override
    public void streamWithToolOutputs(
            AiGatewayCommand command,
            ProviderResult previousResult,
            List<ToolExecutionResult> toolResults,
            Consumer<ProviderStreamEvent> eventConsumer
    ) {
        ProviderResult nextResult = continueWithToolOutputs(command, previousResult, toolResults);
        String content = nextResult.content();
        for (int start = 0; start < content.length(); start += 24) {
            int end = Math.min(content.length(), start + 24);
            eventConsumer.accept(ProviderStreamEvent.textDelta(content.substring(start, end), nextResult.responseId()));
        }
        eventConsumer.accept(ProviderStreamEvent.done(nextResult.responseId(), nextResult.model(), nextResult.usage()));
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private ProviderToolCall toToolCall(AiGatewayCommand.ToolDefinition tool) {
        String arguments = switch (tool.name()) {
            case "lookup_weather" -> "{\"city\":\"Seoul\"}";
            case "lookup_time" -> "{\"timezone\":\"Asia/Seoul\"}";
            default -> "{\"mock\":true}";
        };
        String suffix = tool.name().replace('_', '-');
        return new ProviderToolCall("mock-tool-" + suffix, "call-" + suffix, tool.name(), arguments);
    }

    private ProviderUsage estimateUsage(String prompt, String content) {
        int inputTokens = estimateTokens(prompt);
        int outputTokens = estimateTokens(content);
        return ProviderUsage.estimated(inputTokens, outputTokens, 0.0d);
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0d));
    }
}
