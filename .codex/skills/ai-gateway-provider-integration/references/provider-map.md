# Provider Map

## Shared Contracts

- `src/main/java/com/example/aigateway/domain/provider/LlmProvider.java`
- `src/main/java/com/example/aigateway/domain/provider/ProviderCapabilities.java`
- `src/main/java/com/example/aigateway/domain/provider/ProviderRouter.java`
- `src/main/java/com/example/aigateway/application/dto/ProviderResult.java`
- `src/main/java/com/example/aigateway/application/dto/ProviderToolCall.java`
- `src/main/java/com/example/aigateway/application/dto/ProviderStreamEvent.java`

## Provider Implementations

- `src/main/java/com/example/aigateway/infrastructure/provider/OpenAiLlmProvider.java`
- `src/main/java/com/example/aigateway/infrastructure/provider/RestClientOpenAiApiClient.java`
- `src/main/java/com/example/aigateway/infrastructure/provider/MockLlmProvider.java`

## Test Targets

- `src/test/java/com/example/aigateway/infrastructure/provider/OpenAiLlmProviderTest.java`
- `src/test/java/com/example/aigateway/domain/provider/ProviderRouterTest.java`
- `src/test/java/com/example/aigateway/api/controller/AdminControllerTest.java`
