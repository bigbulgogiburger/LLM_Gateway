# Architecture

## Request Flow

1. `POST /api/ai/chat` 요청 수신
2. Input Rule Guardrail 실행
3. AI Guardrail 실행
4. API Key 기반 client/tenant/provider 권한 검증 및 rate limit 적용
5. Provider Router가 `mock` 또는 `openai` 선택
6. Provider resilience 계층에서 timeout/retry/circuit breaker 적용
7. Provider 응답 생성
8. Output Guardrail 실행
9. 감사 로그 기록 후 응답 반환
10. Admin API를 통해 tenant 정책 override와 audit 검색 수행
11. 필요 시 tenant 정책 version history 조회 및 rollback 수행

## Components

- `api`: REST controller, request/response DTO
- `application`: gateway orchestration service
- `domain.guardrail.rule`: 입력/출력 규칙 인터페이스
- `domain.guardrail.service`: 입력/출력/AI Guardrail 집계 서비스
- `domain.provider`: provider 추상화와 router
- `domain.security`: 인증된 Gateway principal
- `infrastructure.provider`: Mock/OpenAI 구현
- `infrastructure.security`: API key auth filter, requestId filter, rate limit
- `infrastructure.config`: YAML properties, `.env` 로더, OpenAI RestClient 설정
- `infrastructure.audit`: JPA audit persistence, audit search projection
- `infrastructure.policy`: tenant policy override persistence
- `application.dto`: admin history/search 응답 DTO

## Policy Strategy

- 입력 정책, AI 위험 문구, 출력 차단 키워드, 출력 PII 마스킹 규칙을 모두 `application.yml`에서 관리한다.
- tenant별 런타임 override는 DB에 영속되며 YAML 정책보다 우선한다.
- override 변경은 version history 테이블에 append-only로 남고, rollback도 새 version으로 기록된다.
- `.env`의 `OPENAI_API_KEY`는 Spring 환경에 주입되어 `gateway.providers.openai.api-key`로 바인딩된다.

## Provider Strategy

- `LlmProvider` 인터페이스로 provider를 교체 가능하게 유지한다.
- `OpenAiLlmProvider`는 실제 OpenAI API 호출을 담당하는 `OpenAiApiClient`에 위임한다.
- 다른 상용 provider는 동일한 패턴으로 추가할 수 있다.

## Operational Hardening

- API key별로 `tenantId`, `role`, `allowedProviders`, `requestsPerMinute`를 설정한다.
- Redis 활성화 시 API key 기준 rate limit/quota는 분산 카운터로 동작한다.
- audit search는 PostgreSQL에서 full-text search, 그 외 DB에서는 substring 검색으로 폴백한다.
- provider 호출은 timeout, retry, circuit breaker 계층을 거친다.
- 오류 응답은 requestId와 business error code를 포함한다.
- Docker compose 기반 실기동 검증은 환경 변수 `RUN_DOCKER_INTEGRATION_TESTS=true`로 별도 실행한다.
