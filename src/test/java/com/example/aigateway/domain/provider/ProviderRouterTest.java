package com.example.aigateway.domain.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.aigateway.infrastructure.provider.MockLlmProvider;
import com.example.aigateway.infrastructure.provider.OpenAiLlmProvider;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProviderRouterTest {

    private final ProviderRouter providerRouter = new ProviderRouter(List.of(
            new MockLlmProvider(),
            new OpenAiLlmProvider(command -> "ok")
    ));

    @Test
    @DisplayName("provider 이름으로 적절한 어댑터를 선택한다")
    void routesKnownProvider() {
        assertThat(providerRouter.route("mock")).isInstanceOf(MockLlmProvider.class);
        assertThat(providerRouter.route("openai")).isInstanceOf(OpenAiLlmProvider.class);
    }

    @Test
    @DisplayName("지원하지 않는 provider면 예외를 던진다")
    void throwsForUnknownProvider() {
        assertThatThrownBy(() -> providerRouter.route("anthropic"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는 provider");
    }
}
