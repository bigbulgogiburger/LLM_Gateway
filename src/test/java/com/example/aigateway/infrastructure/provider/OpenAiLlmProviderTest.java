package com.example.aigateway.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aigateway.application.dto.AiGatewayCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenAiLlmProviderTest {

    @Test
    @DisplayName("OpenAI provider는 API client에 위임한다")
    void delegatesToApiClient() {
        OpenAiApiClient client = command -> "openai:" + command.prompt();
        OpenAiLlmProvider provider = new OpenAiLlmProvider(client);

        String content = provider.generate(new AiGatewayCommand("req-1", "tenant-test", "client-test", "user-1", "USER", "openai", "요약해줘"));

        assertThat(provider.name()).isEqualTo("openai");
        assertThat(content).isEqualTo("openai:요약해줘");
    }
}
