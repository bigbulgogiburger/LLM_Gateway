---
name: ai-gateway-tool-orchestration
description: Implement or refine tool-call orchestration in this repository. Use when the task adds executable tools, tool request schemas, tool policy enforcement, model-issued tool calls, follow-up responses, or SSE behavior for tool execution loops.
---

# AI Gateway Tool Orchestration

Implement tool use as a policy-controlled loop, not as ad hoc controller logic.

## Workflow

1. Start with the contract:
   - `AiChatRequest` and `AiGatewayCommand` for requested tools
   - `ProviderToolCall`, `ProviderResult`, and `ProviderStreamEvent` for provider output
2. Enforce requested-tool policy before provider execution with `ToolPolicyService`.
3. Execute model-issued tool calls through `ToolExecutionService` and `ExecutableTool` implementations in `infrastructure/tool`.
4. Continue the provider conversation with tool outputs instead of synthesizing final text in the service layer.
5. Keep sync and SSE paths behaviorally aligned.

## Tooling Rules

- Require explicit `allowedTools` on the authenticated client for any executable tool.
- Validate tool names and tool-choice references against the requested tool list.
- Keep tool outputs machine-readable JSON so providers can consume them reliably.
- Bound the loop. Do not allow unbounded recursive tool execution.
- Audit or expose executed tool calls in API responses when behavior changes.

## Testing

- Cover three paths:
  - requested tool denied by policy
  - model issues tool call and receives follow-up answer
  - streaming path emits tool-call and final text events
- Run:
  - `./gradlew test --tests '*AiGatewayServiceTest' --tests '*AiChatControllerTest'`
  - `./gradlew test --tests '*OpenAiLlmProviderTest'` when OpenAI follow-up handling changes

## References

- Read `references/tool-flow.md` for the main execution path and likely touch points.
