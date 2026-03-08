package com.example.aigateway.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.application.dto.ProviderResult;
import com.example.aigateway.application.dto.ProviderStreamEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenAiLlmProviderTest {

    @Test
    @DisplayName("OpenAI provider는 API client에 위임한다")
    void delegatesToApiClient() {
        OpenAiApiClient client = new OpenAiApiClient() {
            @Override
            public ProviderResult generate(AiGatewayCommand command) {
                return ProviderResult.text("openai:" + command.prompt());
            }

            @Override
            public void stream(AiGatewayCommand command, java.util.function.Consumer<ProviderStreamEvent> eventConsumer) {
                eventConsumer.accept(ProviderStreamEvent.textDelta("openai-stream", "resp-1"));
                eventConsumer.accept(ProviderStreamEvent.done("resp-1"));
            }
        };
        OpenAiLlmProvider provider = new OpenAiLlmProvider(client);

        ProviderResult result = provider.generate(new AiGatewayCommand("req-1", "tenant-test", "client-test", "user-1", "USER", "openai", "요약해줘"));
        List<ProviderStreamEvent> events = new ArrayList<>();
        provider.stream(new AiGatewayCommand("req-1", "tenant-test", "client-test", "user-1", "USER", "openai", "요약해줘"), events::add);

        assertThat(provider.name()).isEqualTo("openai");
        assertThat(result.content()).isEqualTo("openai:요약해줘");
        assertThat(provider.capabilities().supportsStreaming()).isTrue();
        assertThat(events).extracting(ProviderStreamEvent::type).containsExactly("text_delta", "done");
    }
}
