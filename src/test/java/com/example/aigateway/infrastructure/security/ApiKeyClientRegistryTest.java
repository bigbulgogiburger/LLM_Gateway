package com.example.aigateway.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aigateway.infrastructure.config.GatewaySecurityProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiKeyClientRegistryTest {

    @Test
    @DisplayName("해시된 API 키 설정으로 클라이언트를 찾을 수 있다")
    void findsClientByHashedApiKey() {
        ApiKeyClientRegistry registry = new ApiKeyClientRegistry(new GatewaySecurityProperties(
                true,
                "X-API-Key",
                List.of(new GatewaySecurityProperties.ClientProperties(
                        "hashed-client",
                        "tenant-default",
                        null,
                        "OPERATOR",
                        60,
                        1000,
                        50000,
                        List.of("mock"),
                        List.of(),
                        "2bcd99491790f5324dd084241b713b576a92b12c497f3b553230d49cc72e15c2",
                        List.of("lookup_weather")
                ))
        ));

        assertThat(registry.findByApiKey("local-dev-api-key"))
                .map(GatewaySecurityProperties.ClientProperties::clientId)
                .contains("hashed-client");
        assertThat(registry.findByApiKey("wrong-key")).isEmpty();
    }

    @Test
    @DisplayName("레거시 평문 API 키 설정도 호환된다")
    void supportsLegacyPlaintextApiKey() {
        ApiKeyClientRegistry registry = new ApiKeyClientRegistry(new GatewaySecurityProperties(
                true,
                "X-API-Key",
                List.of(new GatewaySecurityProperties.ClientProperties(
                        "legacy-client",
                        "tenant-default",
                        "legacy-key",
                        "OPERATOR",
                        60,
                        1000,
                        50000,
                        List.of("mock"),
                        List.of(),
                        null,
                        List.of("lookup_weather")
                ))
        ));

        assertThat(registry.findByApiKey("legacy-key"))
                .map(GatewaySecurityProperties.ClientProperties::clientId)
                .contains("legacy-client");
    }
}
