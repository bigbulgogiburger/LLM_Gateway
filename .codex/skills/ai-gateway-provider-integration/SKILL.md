---
name: ai-gateway-provider-integration
description: Add, refactor, or debug model provider integrations in this repository. Use when the task changes LlmProvider contracts, ProviderCapabilities, OpenAI Responses API handling, streaming, structured outputs, provider routing, or mock-provider parity.
---

# AI Gateway Provider Integration

Change provider behavior through the provider contract first, then adapt each implementation deliberately.

## Workflow

1. Start at `domain/provider/LlmProvider.java` and `ProviderCapabilities.java`.
2. Decide whether the feature is cross-provider.
   - If yes, add or refine capability flags and shared DTOs in `application/dto`.
   - If no, keep the behavior isolated in the provider adapter.
3. Keep `mock` and `openai` behavior aligned enough that service tests can exercise the same contract.
4. Update `ProviderRouter` and admin capability exposure if support changes.

## Provider Rules

- Preserve the `Responses API` orientation in `RestClientOpenAiApiClient`.
- Keep sync, streaming, and tool-call parsing logically consistent.
- Avoid returning raw vendor payloads from `LlmProvider`.
- Surface unsupported behavior through capability checks instead of silent degradation.

## Testing

- Run:
  - `./gradlew test --tests '*OpenAiLlmProviderTest' --tests '*ProviderRouterTest'`
  - `./gradlew test --tests '*AiGatewayServiceTest' --tests '*AiChatControllerTest'`
- Add both sync and streaming coverage when changing OpenAI handling.
- If provider capabilities change, cover admin visibility in `AdminControllerTest`.

## References

- Read `references/provider-map.md` for the main adapter files and expected parity points.
