# Repository Guidelines

## Project Structure & Module Organization

This repository is a Spring Boot 3.x AI gateway written in Java 21 with Gradle.

- `src/main/java/com/example/aigateway`: application code
- `api`: controllers, request/response DTOs
- `application`: orchestration services and DTOs
- `domain`: provider, guardrail, audit, and security abstractions
- `infrastructure`: config, security filters, provider adapters, persistence
- Provider work is split across `domain/provider`, `infrastructure/provider`, and request/response DTOs in `application` and `api`
- `src/main/resources`: runtime configuration such as `application.yml`
- `src/test/java`: unit, slice, and integration-style tests
- `src/test/resources`: test-only config
- `docs/`: architecture notes and gap analysis

## Build, Test, and Development Commands

- `./gradlew bootRun`: run the gateway locally on port `8090`
- `./gradlew test`: run the full test suite
- `./gradlew build`: compile, test, and package the app
- `./gradlew test --tests '*AiChatControllerTest' --tests '*AiGatewayServiceTest'`: run focused API and orchestration tests
- `docker compose up -d`: start local PostgreSQL and Redis dependencies
- `RUN_DOCKER_INTEGRATION_TESTS=true ./gradlew test`: enable Docker-backed integration coverage when local services are available

## Coding Style & Naming Conventions

- Use 4-space indentation and standard Java formatting.
- Keep packages aligned to responsibility: `api`, `application`, `domain`, `infrastructure`.
- Class names use `PascalCase`; methods and fields use `camelCase`; constants use `UPPER_SNAKE_CASE`.
- Prefer clear service names such as `AiGatewayService`, `ProviderExecutionService`, `RuleBasedModerationService`.
- Keep controller DTOs in `api/request` and `api/response`; do not leak infrastructure types into controllers.
- Preserve the current layering: request DTO -> `AiGatewayCommand` -> `LlmProvider` -> provider adapter.
- New provider features should be expressed through `ProviderCapabilities` before adding controller behavior.

## Testing Guidelines

- Tests use JUnit 5, Spring Boot Test, AssertJ, Mockito, and MockMvc.
- Name test classes after the target class, for example `AdminControllerTest`.
- Write `@DisplayName` in Korean or English, but keep it specific to the behavior under test.
- Add focused tests for security boundaries, provider behavior, guardrail decisions, tool calls, and SSE streaming whenever logic changes.
- Prefer targeted runs during development, for example: `./gradlew test --tests '*AiGatewayServiceTest'`.
- If you touch OpenAI integration, cover both sync and streaming paths.

## Commit & Pull Request Guidelines

- Follow the existing commit style: short imperative summaries, e.g. `Harden security and expand gateway request handling`.
- Keep each commit scoped to one coherent change set.
- PRs should include: purpose, key design choices, config changes, and test coverage.
- If API behavior changes, include example requests or response snippets.

## Security & Configuration Tips

- Do not commit real secrets. Use `.env` locally and prefer `*_SHA256` API key settings for non-dev environments.
- Review changes to `application.yml`, security filters, and admin endpoints carefully; most regressions here are high impact.
- OpenAI integration is `Responses API`-oriented. Keep structured output, streaming, and tool-call behavior aligned across `mock` and `openai` providers when possible.
