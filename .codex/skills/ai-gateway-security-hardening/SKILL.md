---
name: ai-gateway-security-hardening
description: Harden security, guardrails, and policy enforcement in this repository. Use when the task affects API key auth, tenant scope, admin access, rate limiting, actuator exposure, moderation, guardrail rules, error responses, or any fail-closed behavior in the gateway.
---

# AI Gateway Security Hardening

Treat every change as a trust-boundary change. Default to deny and verify negative cases explicitly.

## Workflow

1. Identify the boundary being changed.
   - Authentication and principal shaping
   - Tenant or admin authorization
   - Guardrail or moderation policy
   - Rate limit, quota, or actuator exposure
2. Trace the request through `ApiKeyAuthenticationFilter`, `GatewayPrincipal`, `SecurityConfig`, and the affected service.
3. Make the configuration surface explicit in `GatewaySecurityProperties`, guardrail properties, or moderation properties.
4. Ensure the failure mode is deterministic and JSON-encoded through the existing error handlers.

## Hardening Rules

- Keep admin operations tenant-scoped unless the change clearly requires global admin behavior.
- Prefer hashed secrets and constant-time comparison patterns.
- Protect new operational endpoints the same way as existing admin or actuator endpoints.
- Separate input blocking, moderation blocking, and output blocking so audit logs stay explainable.
- Do not widen `allowedProviders` or `allowedTools` implicitly.

## Testing

- Add negative tests first: unauthorized, forbidden, wrong tenant, blocked content, over-limit.
- Run:
  - `./gradlew test --tests '*AdminControllerTest' --tests '*AiChatControllerTest'`
  - `./gradlew test --tests '*ApiKeyClientRegistryTest' --tests '*RedisClientRateLimiterTest'`
- If guardrail logic changes, run the matching rule tests in `src/test/java/com/example/aigateway/domain/guardrail/rule`.

## References

- Read `references/security-map.md` for the main boundary files and regression checklist.
