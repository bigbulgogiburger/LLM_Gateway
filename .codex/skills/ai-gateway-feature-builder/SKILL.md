---
name: ai-gateway-feature-builder
description: Add or extend end-to-end gateway features in this Spring Boot repository. Use when the task changes request or response DTOs, controller behavior, orchestration in AiGatewayService, admin APIs, quota or audit behavior, or any cross-layer feature that must preserve the repository's API, application, domain, and infrastructure layering.
---

# AI Gateway Feature Builder

Implement features through the existing layering instead of patching behavior directly into controllers or provider adapters.

## Workflow

1. Read `AGENTS.md` and `docs/architecture.md`, then inspect the request path in `src/main/java/com/example/aigateway/api`, `application`, and `domain`.
2. Decide which layer owns the change.
   - `api`: HTTP contracts, validation, response shape
   - `application`: orchestration, audit, quota, tool loop
   - `domain`: stable interfaces and policy decisions
   - `infrastructure`: provider adapters, persistence, filters, config binding
3. Preserve the main path: request DTO -> `AiGatewayCommand` -> `AiGatewayService` -> `LlmProvider` -> response DTO.
4. Keep provider-specific logic out of controllers. Add domain or application DTOs first if the change crosses provider boundaries.
5. Update `README.md` when the API contract or config surface changes.

## Implementation Rules

- Add capabilities before behavior when the change depends on provider support.
- Prefer small DTO extensions over leaking raw provider payloads into the API layer.
- Reuse existing error handling with `GatewayException`, `GatewayErrorCodes`, and `GlobalExceptionHandler`.
- Extend `application.yml` and `src/test/resources/application.yml` together when config changes.

## Testing

- Run focused tests first:
  - `./gradlew test --tests '*AiChatControllerTest' --tests '*AiGatewayServiceTest'`
  - Add `*AdminControllerTest'` when admin APIs change.
- Add at least one controller-level test and one service-level test for behavior that crosses layers.
- If streaming or provider contracts change, also run `*OpenAiLlmProviderTest` and `*ProviderRouterTest`.

## References

- Read `references/file-map.md` for the main code paths and usual test targets.
