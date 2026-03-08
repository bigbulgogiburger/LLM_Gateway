# Tool Flow

## Request and Policy

- `src/main/java/com/example/aigateway/api/request/AiChatRequest.java`
- `src/main/java/com/example/aigateway/application/dto/AiGatewayCommand.java`
- `src/main/java/com/example/aigateway/application/service/ToolPolicyService.java`
- `src/main/java/com/example/aigateway/domain/security/GatewayPrincipal.java`

## Execution Loop

- `src/main/java/com/example/aigateway/application/service/AiGatewayService.java`
- `src/main/java/com/example/aigateway/application/service/ToolExecutionService.java`
- `src/main/java/com/example/aigateway/application/dto/ToolExecutionResult.java`
- `src/main/java/com/example/aigateway/domain/tool/ExecutableTool.java`

## Implementations and Provider Follow-Up

- `src/main/java/com/example/aigateway/infrastructure/tool/LookupWeatherTool.java`
- `src/main/java/com/example/aigateway/infrastructure/tool/LookupTimeTool.java`
- `src/main/java/com/example/aigateway/infrastructure/provider/RestClientOpenAiApiClient.java`
- `src/main/java/com/example/aigateway/infrastructure/provider/MockLlmProvider.java`
