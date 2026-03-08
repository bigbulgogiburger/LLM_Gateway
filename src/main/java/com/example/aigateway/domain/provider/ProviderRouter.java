package com.example.aigateway.domain.provider;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ProviderRouter {

    private final List<LlmProvider> providers;

    public ProviderRouter(List<LlmProvider> providers) {
        this.providers = providers;
    }

    public LlmProvider route(String providerName) {
        return providers.stream()
                .filter(provider -> provider.name().equalsIgnoreCase(providerName.toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 provider 입니다: " + providerName));
    }
}
