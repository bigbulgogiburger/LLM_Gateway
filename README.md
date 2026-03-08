# Enterprise AI Gateway + Guardrails MVP

Spring Boot 3.x, Java 21, Gradle 기반의 AI Gateway MVP다. 요청은 Input Rule Guardrail과 AI Guardrail을 거친 뒤 허용된 경우에만 `mock` 또는 `openai` provider로 전달된다. 생성된 응답은 Output Guardrail을 다시 통과하며, 필요 시 민감정보를 마스킹하거나 출력을 차단한다.
운영 보강으로 API key 인증, tenant-aware 정책, quota, persistent audit, metrics, provider resilience까지 포함한다.

## 실행

```bash
./gradlew bootRun
```

`.env` 예시:

```bash
OPENAI_API_KEY=sk-...
GATEWAY_API_KEY=local-dev-api-key
GATEWAY_ADMIN_API_KEY=local-admin-api-key
GATEWAY_API_KEY_SHA256=
GATEWAY_ADMIN_API_KEY_SHA256=
DATABASE_URL=jdbc:postgresql://localhost:5432/aigateway
DATABASE_DRIVER_CLASS_NAME=org.postgresql.Driver
DATABASE_USERNAME=aigateway
DATABASE_PASSWORD=aigateway
GATEWAY_REDIS_ENABLED=true
```

운영 환경에서는 `GATEWAY_API_KEY` 대신 `GATEWAY_API_KEY_SHA256`, `GATEWAY_ADMIN_API_KEY_SHA256`에 SHA-256 hex 값을 넣는 구성을 권장한다. 로컬 개발 편의를 위해 평문 키도 아직 호환되지만, 해시 설정이 있으면 해시 값을 우선 사용한다.

## 테스트

```bash
./gradlew test
```

## API 예시

```bash
curl -X POST http://localhost:8090/api/ai/chat \
  -H "X-API-Key: local-dev-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-001",
    "role": "OPERATOR",
    "provider": "openai",
    "prompt": "이번 주 수리 현황을 요약해줘"
  }'
```

멀티턴 대화는 `prompt` 대신 `messages` 배열로도 보낼 수 있다.

```bash
curl -X POST http://localhost:8090/api/ai/chat \
  -H "X-API-Key: local-dev-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-001",
    "provider": "mock",
    "messages": [
      {"role": "user", "content": "이번 주 수리 현황을 요약해줘"},
      {"role": "assistant", "content": "지난주에는 12건이었습니다."},
      {"role": "user", "content": "이번 주만 다시 정리해줘"}
    ]
  }'
```

구조화된 JSON 응답이 필요하면 `responseFormat`을 함께 보낼 수 있다.

```bash
curl -X POST http://localhost:8090/api/ai/chat \
  -H "X-API-Key: local-dev-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-001",
    "provider": "openai",
    "prompt": "이번 주 수리 현황을 JSON으로 요약해줘",
    "responseFormat": {
      "type": "json_schema",
      "schemaName": "weekly_summary",
      "strict": true,
      "schema": {
        "type": "object",
        "properties": {
          "summary": {"type": "string"},
          "riskLevel": {"type": "string"}
        },
        "required": ["summary", "riskLevel"],
        "additionalProperties": false
      }
    }
  }'
```

## 주요 구조

- `Input Rule Guardrail`: 금지어, PII, 프롬프트 길이, 역할 정책
- `Moderation Layer`: 입력/출력 고위험 패턴을 별도 계층에서 차단
- `AI Guardrail`: YAML 정책 기반 위험 문구 판정
- `Provider Router`: `mock`, `openai` 라우팅
- `OpenAI Provider`: `.env` 또는 환경 변수의 `OPENAI_API_KEY`를 사용해 실제 OpenAI Chat Completions 호출
- `Output Guardrail`: 민감 키워드 차단, PII 마스킹
- `API Key Security`: 클라이언트/테넌트/역할 매핑, provider 허용 범위 제어
- `Rate Limit`: API key 단위 in-memory requests/minute 제한
- `Quota`: API key 단위 일일 요청/토큰 quota 제한
- `Redis-ready Distributed Limit`: Redis 활성화 시 rate limit/quota를 분산 저장소로 전환
- `Provider Resilience`: timeout, retry, circuit breaker 기반 보호
- `Persistent Audit`: audit event를 DB에 저장하고 search projection도 함께 유지
- `Audit Search`: PostgreSQL에서는 full-text search, 기본 H2/비-PostgreSQL에서는 substring 검색으로 폴백
- `PostgreSQL-ready`: env 전환만으로 H2 대신 PostgreSQL 사용 가능
- `Observability`: actuator metrics, gateway outcome/latency meter 제공
- `Admin API`: tenant 정책 override 영속화 및 audit search 지원
- `Policy Versioning`: tenant 정책 변경 이력 조회와 version rollback 지원
- `Audit Logging`: requestId, tenantId, clientId, userId, role, provider, 상태, input/output guardrail 결과, 처리 시간 기록

## 확장 포인트

- Claude Provider 추가
- 테넌트별 정책 분리
- 정책 저장소 외부화
- MCP/RAG 연동용 서비스 추가

## 정책 외부화

주요 정책은 `src/main/resources/application.yml`에 있다.

- `gateway.guardrail.input`: 입력 규칙 정책
- `gateway.guardrail.ai`: AI Guardrail 위험 문구 정책
- `gateway.guardrail.output`: 출력 차단/마스킹 정책
- `gateway.providers.openai`: OpenAI 모델/기본 메시지/API 키 설정
- `gateway.tenants.policies`: tenant별 guardrail override
- `gateway.security.clients`: client별 API key, provider 허용 범위, quota
- `gateway.redis`: Redis 기반 distributed limit 활성화

런타임 override는 YAML 위에 덮어쓰는 형태로 DB에 저장된다. `GuardrailPolicyResolver`는 `DB override -> YAML tenant policy -> global default` 순서로 정책을 해석한다.

## 운영 엔드포인트

- `GET /actuator/health` on `http://localhost:8090/actuator/health`
- `GET /actuator/metrics` on `http://localhost:8090/actuator/metrics`

## Admin API

관리자 API는 `X-API-Key: local-admin-api-key` 또는 `GATEWAY_ADMIN_API_KEY` 기준으로 호출한다.

tenant 정책 조회:

```bash
curl http://localhost:8090/api/admin/tenants/tenant-default/policy \
  -H "X-API-Key: local-admin-api-key"
```

tenant 정책 override 저장:

```bash
curl -X POST http://localhost:8090/api/admin/tenants/tenant-default/policy \
  -H "X-API-Key: local-admin-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "maxPromptLength": 300
    }
  }'
```

tenant 정책 변경 이력 조회:

```bash
curl http://localhost:8090/api/admin/tenants/tenant-default/policy/history \
  -H "X-API-Key: local-admin-api-key"
```

tenant 정책 version rollback:

```bash
curl -X POST http://localhost:8090/api/admin/tenants/tenant-default/policy/rollback/1 \
  -H "X-API-Key: local-admin-api-key"
```

audit 검색:

```bash
curl "http://localhost:8090/api/admin/audits/search?q=수리" \
  -H "X-API-Key: local-admin-api-key"
```

PostgreSQL을 사용할 때 audit 검색은 `to_tsvector`와 `websearch_to_tsquery` 기반 full-text search로 동작한다.

## Redis/PostgreSQL 실행

```bash
docker compose up -d
```

Docker-backed integration test는 로컬 Postgres/Redis가 떠 있을 때만 명시적으로 실행한다.

```bash
RUN_DOCKER_INTEGRATION_TESTS=true GRADLE_USER_HOME=/tmp/gradle-home ./gradlew test --no-daemon --console=plain
```

## 문서

- 아키텍처 개요: `docs/architecture.md`
- 60회 Ralph loop 기록: `RALPH_LOOP_LOG.md`
