# Security Map

## Authentication and Authorization

- `src/main/java/com/example/aigateway/infrastructure/security/ApiKeyAuthenticationFilter.java`
- `src/main/java/com/example/aigateway/domain/security/GatewayPrincipal.java`
- `src/main/java/com/example/aigateway/infrastructure/security/SecurityConfig.java`
- `src/main/java/com/example/aigateway/infrastructure/config/GatewaySecurityProperties.java`

## Guardrails and Moderation

- `src/main/java/com/example/aigateway/domain/guardrail/service`
- `src/main/java/com/example/aigateway/infrastructure/guardrail`
- `src/main/java/com/example/aigateway/infrastructure/config/GuardrailProperties.java`
- `src/main/java/com/example/aigateway/infrastructure/config/ModerationProperties.java`

## High-Value Regression Tests

- `src/test/java/com/example/aigateway/api/controller/AdminControllerTest.java`
- `src/test/java/com/example/aigateway/api/controller/AiChatControllerTest.java`
- `src/test/java/com/example/aigateway/infrastructure/security/ApiKeyClientRegistryTest.java`
- `src/test/java/com/example/aigateway/infrastructure/security/RedisClientRateLimiterTest.java`
