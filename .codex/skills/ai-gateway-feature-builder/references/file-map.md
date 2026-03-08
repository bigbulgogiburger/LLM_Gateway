# File Map

## Core Request Flow

- `src/main/java/com/example/aigateway/api/controller/AiChatController.java`
- `src/main/java/com/example/aigateway/api/request/AiChatRequest.java`
- `src/main/java/com/example/aigateway/application/service/AiGatewayService.java`
- `src/main/java/com/example/aigateway/application/dto/AiGatewayCommand.java`
- `src/main/java/com/example/aigateway/domain/provider/LlmProvider.java`
- `src/main/java/com/example/aigateway/api/response/AiChatResponse.java`

## Admin and Operations

- `src/main/java/com/example/aigateway/api/controller/AdminController.java`
- `src/main/java/com/example/aigateway/application/service/TenantPolicyAdminService.java`
- `src/main/java/com/example/aigateway/application/service/AuditSearchService.java`
- `src/main/java/com/example/aigateway/application/service/GatewayMetricsService.java`

## Common Tests

- `src/test/java/com/example/aigateway/api/controller/AiChatControllerTest.java`
- `src/test/java/com/example/aigateway/application/service/AiGatewayServiceTest.java`
- `src/test/java/com/example/aigateway/api/controller/AdminControllerTest.java`
