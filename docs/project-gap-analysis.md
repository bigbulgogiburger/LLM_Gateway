# LLM Gateway 부족한 점 및 개선 제안

작성일: 2026-03-08

이 문서는 현재 코드베이스를 직접 읽은 결과와 2026-03-08 기준 공식 문서/보안 가이드를 대조해, 이 프로젝트의 부족한 점과 개선 방향을 정리한 것이다.

평가 기준:

- 현재 로컬 코드 구현
- OWASP LLM/GenAI 보안 가이드
- OpenAI 공식 API 가이드 및 최신 모델 기능 문서
- Anthropic 공식 프롬프트 누출 방지 가이드

## 한 줄 진단

현재 프로젝트는 "기업형 AI Gateway MVP"로서 구조는 괜찮지만, 실제 프로덕션급 AI Gateway로 보기에는 `보안 경계`, `정책 집행 정교함`, `provider 기능 추상화`, `평가/운영 자동화`, `비동기/스트리밍 처리`가 아직 부족하다.

## 잘한 점

- 입력 가드레일, AI 가드레일, 출력 가드레일을 분리해 파이프라인화했다.
- API key 인증, rate limit, quota, audit, tenant 정책 override, provider resilience까지 기본 골격이 있다.
- `LlmProvider` 추상화가 있어 provider 추가 방향은 나쁘지 않다.
- output masking과 audit search를 붙여 운영 MVP로서의 형태는 갖췄다.

## 우선순위별 핵심 개선 항목

### P0. 바로 손봐야 하는 항목

#### 1. Admin 권한이 tenant 범위를 제대로 강제하지 않는다

현재 `AdminController`는 `/api/admin/tenants/{tenantId}/...` 형태로 아무 tenantId나 path로 받아 처리한다. 인증은 `ROLE_ADMIN`만 확인하고 있고, 요청한 관리자 principal이 해당 tenant를 관리할 수 있는지 별도 검증이 없다.

영향:

- 하나의 admin key가 모든 tenant 정책과 audit에 접근할 수 있다.
- 멀티테넌트 게이트웨이에서 가장 위험한 권한 경계 누락이다.

관련 코드:

- `src/main/java/com/example/aigateway/api/controller/AdminController.java`
- `src/main/java/com/example/aigateway/infrastructure/security/SecurityConfig.java`

개선:

- admin principal에 `manageableTenants` 또는 `scope`를 부여해야 한다.
- path의 `tenantId`가 principal scope에 속하는지 강제 검증해야 한다.
- super-admin과 tenant-admin 권한을 분리해야 한다.

#### 2. Actuator metrics/prometheus가 공개되어 있다

현재 보안 설정은 `/actuator/metrics/**`, `/actuator/prometheus`를 모두 `permitAll()`로 열어 두고 있다.

영향:

- 외부에 트래픽 패턴, provider 사용량, 인프라 상태가 노출될 수 있다.
- 공격자가 rate limit, 장애 상태, 처리량을 역으로 학습할 수 있다.

관련 코드:

- `src/main/java/com/example/aigateway/infrastructure/security/SecurityConfig.java`

개선:

- 최소한 내부망/IP allowlist 또는 admin API key 보호로 바꿔야 한다.
- public health와 private metrics를 분리해야 한다.

#### 3. API key가 평문 비교 + 정적 설정 기반이다

현재 API key는 `application.yml` 바인딩 값으로 메모리에 올라오고, `equals` 기반 선형 탐색으로 찾는다.

영향:

- key rotation, key 폐기, key 만료, key 발급 이력 관리가 없다.
- secret manager 연동이 없다.
- 평문 key 저장/노출 가능성이 높다.

관련 코드:

- `src/main/java/com/example/aigateway/infrastructure/security/ApiKeyClientRegistry.java`
- `src/main/resources/application.yml`

개선:

- API key 원문 대신 hash만 저장하고 constant-time 비교를 사용해야 한다.
- DB 또는 secret manager 기반 key registry로 옮겨야 한다.
- key 상태(active/revoked/expired), last-used-at, rotation metadata를 추가해야 한다.

#### 4. Upstream/provider 오류 메시지를 그대로 일부 노출한다

`RestClientOpenAiApiClient`는 `RestClientException`의 메시지를 그대로 포함해 `GatewayException`으로 재던지고, `GlobalExceptionHandler`는 이 메시지를 사용자 응답에 실어 보낸다.

영향:

- upstream 오류 구조, 네트워크 상태, 내부 예외 문자열이 외부에 노출될 수 있다.
- 공급자 장애나 인증 실패 세부 정보가 누출될 수 있다.

관련 코드:

- `src/main/java/com/example/aigateway/infrastructure/provider/RestClientOpenAiApiClient.java`
- `src/main/java/com/example/aigateway/api/controller/GlobalExceptionHandler.java`

개선:

- 외부 응답에는 정제된 business error만 내려야 한다.
- 상세 원인은 내부 로그/trace/span에만 남겨야 한다.

#### 5. Prompt injection 방어가 정적 문자열 탐지 위주다

현재 AI/input guardrail은 금지어, 정규식, 일부 위험 문구 매칭 중심이다. 이는 직접적 공격 일부만 막고, 간접 프롬프트 인젝션, 인코딩/변형 공격, 문맥 기반 우회에는 약하다.

관련 코드:

- `src/main/resources/application.yml`
- `src/main/java/com/example/aigateway/domain/guardrail/service/RuleGuardrailService.java`
- `src/main/java/com/example/aigateway/infrastructure/guardrail/MockAiGuardrailService.java`

외부 근거:

- OWASP는 `LLM01 Prompt Injection`을 최우선 위험으로 둔다.
- Anthropic 공식 문서는 prompt leak 방지가 완벽할 수 없으며 output screening, post-processing, regular audits를 우선 고려하라고 권고한다.

개선:

- 정적 룰 외에 context-aware classifier 또는 moderation layer를 도입해야 한다.
- 간접 주입 시나리오를 위한 red-team dataset과 정기 eval이 필요하다.
- 도구 호출이 생기면 tool permission boundary와 argument validation을 별도 계층으로 둬야 한다.

### P1. 다음 단계에서 반드시 보강할 항목

#### 6. OpenAI moderation API 같은 전용 safety 모델 연동이 없다

현재는 자체 regex/keyword 정책에 크게 의존한다. OpenAI 공식 문서는 moderation endpoint를 별도 safety 판단 계층으로 제공하며, `omni-moderation-latest`를 신규 앱 기본 선택지로 권장한다.

현재 상태의 한계:

- 유해성 분류가 좁다.
- 다국어/변형 표현에 취약하다.
- 이미지 입력이나 멀티모달 입력을 다룰 수 없다.

개선:

- input/output 양쪽에 전용 moderation provider를 붙여야 한다.
- 현재 rule 기반 정책은 1차 cheap filter로 유지하고, high-risk traffic만 moderation 2차 검사로 보내는 하이브리드 구조가 좋다.

#### 7. 구조화된 출력과 tool/function calling 추상화가 없다

현재 OpenAI 호출은 `/chat/completions`에 `developer` + `user` 메시지 두 개만 보내고, 결과를 단순 문자열로 처리한다.

영향:

- JSON schema 보장 응답을 만들 수 없다.
- downstream 시스템이 신뢰 가능한 machine-readable output을 받기 어렵다.
- 향후 tool use, MCP, retrieval, action execution을 붙이기 어렵다.

관련 코드:

- `src/main/java/com/example/aigateway/infrastructure/provider/RestClientOpenAiApiClient.java`
- `src/main/java/com/example/aigateway/api/request/AiChatRequest.java`

외부 근거:

- OpenAI 공식 문서는 Structured Outputs를 JSON mode의 진화로 설명하며, 가능하면 Structured Outputs 사용을 권장한다.
- 최신 모델 문서는 GPT-5.1, GPT-4o 계열이 streaming, function calling, structured outputs를 지원한다고 명시한다.

개선:

- provider abstraction을 `String in / String out`에서 `messages / tools / response_format / metadata` 구조로 올려야 한다.
- Responses API 중심 설계로 재정렬하는 편이 장기적으로 낫다.

#### 8. 스트리밍, 비동기 장기 작업, 웹훅/폴링이 없다

현재 요청-응답은 동기 단건 처리만 지원한다.

영향:

- 긴 reasoning task에 취약하다.
- UX가 나쁘고 timeout 위험이 크다.
- 대용량 응답/장문 generation의 운영성이 떨어진다.

외부 근거:

- OpenAI 공식 문서는 background mode로 장기 작업을 비동기로 실행하고 polling으로 상태를 확인할 수 있다고 안내한다.
- 최신 모델 문서는 streaming을 공식 기능으로 제공한다.

개선:

- `/api/ai/chat/stream` 같은 SSE endpoint를 추가해야 한다.
- 장기 작업용 async job 테이블과 poll endpoint를 도입해야 한다.
- background mode 또는 내부 queue 기반 async orchestration을 검토해야 한다.

주의:

- OpenAI background mode 문서상 roughly 10분 동안 응답 데이터를 저장하므로 ZDR 요구사항이 있으면 별도 설계가 필요하다.

#### 9. Quota/token 계산이 실제 provider 과금과 정합적이지 않다

현재 토큰 계산은 `문자수 / 4`의 단순 추정치다.

영향:

- 과금 예측이 실제 usage와 어긋난다.
- quota enforcement가 과소/과대 집행될 수 있다.
- 모델별 토큰화 차이를 반영하지 못한다.

관련 코드:

- `src/main/java/com/example/aigateway/application/service/TokenEstimator.java`

개선:

- provider 응답 usage 값을 우선 신뢰해야 한다.
- 요청 전 추정치는 pre-check 용도로만 쓰고, 정산은 provider usage 기준으로 해야 한다.
- 모델별 tokenizer adapter가 필요하다.

#### 10. Retry/circuit breaker 전략이 너무 단순하다

현재 resilience 정책은 provider 전체에 단일 circuit breaker와 단일 retry를 건다. 예외 분류도 거칠다.

영향:

- `429`, 일시적 `5xx`, 네트워크 장애, 잘못된 요청 `4xx`를 구분하지 못한다.
- 잘못된 요청에도 retry가 걸릴 가능성이 있다.
- provider별 장애가 섞이면 breaker가 과도하게 열릴 수 있다.

관련 코드:

- `src/main/java/com/example/aigateway/application/service/ProviderExecutionService.java`

개선:

- provider별 breaker를 분리해야 한다.
- retry 가능한 예외만 선별해야 한다.
- idempotency, timeout budget, bulkhead를 추가해야 한다.

#### 11. 감사 로그는 있지만 거버넌스가 부족하다

현재 audit는 남기지만, retention policy, PII masking policy, encryption at rest, 감사 이벤트 스키마 버전 관리가 없다.

영향:

- 보관 기간과 삭제 정책이 없다.
- prompt summary만 저장하더라도 민감 데이터 잔존 위험이 있다.
- 규제 대응 및 forensic 품질이 제한된다.

관련 코드:

- `src/main/java/com/example/aigateway/infrastructure/audit/JpaAuditLogWriter.java`
- `src/main/java/com/example/aigateway/application/service/AuditSearchService.java`

개선:

- retention/archival/purge 정책을 문서화하고 코드화해야 한다.
- audit payload 분류 체계를 만들어 필드별 masking/encryption 여부를 정해야 한다.
- "누가 어떤 정책으로 왜 차단됐는지"를 더 구조적으로 저장해야 한다.

#### 12. Admin 변경 이력은 있으나 승인 워크플로가 없다

현재 tenant 정책 override는 저장과 rollback은 되지만, 운영 승인 플로우가 없다.

영향:

- 잘못된 정책 변경이 즉시 반영된다.
- 4-eyes principle이나 변경 승인 기록이 없다.

개선:

- draft -> review -> approve -> publish 단계가 필요하다.
- 정책 diff, validation, simulation, dry-run 기능이 있으면 좋다.

### P2. 제품성/확장성 측면에서 아쉬운 항목

#### 13. 멀티턴 대화 상태와 conversation abstraction이 없다

현재 API는 `prompt` 단일 문자열만 받는다.

영향:

- 실서비스에서 필요한 대화 이력 관리가 어렵다.
- system/developer/user/tool 메시지 분리를 못 한다.
- RAG, tool result, moderation metadata를 message graph로 관리할 수 없다.

관련 코드:

- `src/main/java/com/example/aigateway/api/request/AiChatRequest.java`

개선:

- request를 `messages[]`, `attachments[]`, `toolContext`, `responseFormat`, `metadata` 형태로 재설계해야 한다.

#### 14. request 스키마가 일부 혼란스럽다

`AiChatRequest`에는 `role` 필드가 있지만 실제 권한 판단은 principal의 role을 쓴다. 즉, 요청 body의 `role`은 의미가 거의 없거나 오해를 부른다.

영향:

- API contract가 혼란스럽다.
- 클라이언트가 권한을 body로 지정한다고 오해할 수 있다.

개선:

- body의 `role`을 제거하거나, 명확히 "사용자 비즈니스 역할"로 별도 필드명으로 분리해야 한다.

#### 15. 모델/공급자 기능 협상 계층이 없다

provider마다 지원 기능이 다른데, 현재 abstraction은 그 차이를 표현하지 못한다.

영향:

- 특정 provider는 structured output 가능, 어떤 provider는 불가인 상황을 모델링하지 못한다.
- 추후 Anthropic, Bedrock, Azure OpenAI, Vertex AI 추가 시 인터페이스가 급격히 무너질 가능성이 높다.

개선:

- capability model을 도입해야 한다.
- 예: `supportsStreaming`, `supportsTools`, `supportsJsonSchema`, `supportsVision`, `supportsBackgroundJobs`

#### 16. 프로덕션 운영에 필요한 추적성이 약하다

현재 metrics는 있으나 tracing, span correlation, provider call breakdown, per-tenant SLA 측정, queue depth 같은 운영 지표가 부족하다.

개선:

- OpenTelemetry tracing을 붙여 request -> guardrail -> provider -> audit 구간을 trace로 연결해야 한다.
- tenant/provider/model/decision code 기준 대시보드가 필요하다.

#### 17. 테스트 전략이 단위 테스트 위주이고 evaluation 체계가 없다

현재 JUnit 테스트는 있지만, "프롬프트 회귀", "정책 회귀", "탈옥 시도", "간접 주입", "출력 누출"을 다루는 체계적 eval harness가 없다.

외부 근거:

- OpenAI 공식 Evals 문서는 테스트 데이터와 채점 기준(`testing_criteria`)을 갖춘 eval run 기반 반복 개선을 권장한다.

개선:

- guardrail red-team dataset을 별도 관리해야 한다.
- CI에서 eval smoke suite를 돌려 프롬프트/정책 회귀를 잡아야 한다.
- provider mock 기반 deterministic regression suite와 실제 provider canary eval을 분리해야 한다.

#### 18. 공급망/환경 분리 관점의 운영 문서화가 약하다

현재 H2 `ddl-auto: update` 기본값, 로컬 `.env`, 직접 compose 의존 구조는 MVP에는 괜찮지만 운영 기본값으로는 위험하다.

영향:

- 운영과 개발 설정이 쉽게 섞인다.
- schema migration 표준이 없다.

개선:

- Flyway/Liquibase를 도입해야 한다.
- `dev/stage/prod` 프로파일을 분리해야 한다.
- secret manager, config source, DB migration runbook을 문서화해야 한다.

## 외부 공식 가이드와의 갭 요약

### OWASP 기준 갭

현재 프로젝트는 다음 항목에서 특히 갭이 크다.

- `LLM01 Prompt Injection`: 정적 패턴 탐지 위주
- `LLM02 Insecure Output Handling`: 출력 후 차단은 있으나 downstream sink별 정책이 없음
- `LLM04 Model Denial of Service`: 대용량/장기 추론 방어가 약함
- `LLM06 Sensitive Information Disclosure`: prompt leak 및 운영 비밀 노출 방어가 제한적
- `LLM08 Excessive Agency`: 아직 tool/action 계층은 없지만, 추가 시 대비 설계가 없음
- `LLM09 Overreliance`: eval/인간 검토/정확도 관리 프로세스 부족

### OpenAI 공식 기능 기준 갭

현재 OpenAI 연동은 가능하지만, 최신 공식 기능 관점에서는 아래가 빠져 있다.

- moderation endpoint 미활용
- structured outputs 미지원
- function calling/tool calling 미지원
- streaming 미지원
- background mode/async orchestration 미지원
- usage 기반 정확한 정산/비용 추적 미흡

### Anthropic 가이드 기준 갭

Anthropic 문서는 prompt leak 방지가 완벽할 수 없고, output screening, post-processing, audits를 우선 고려하라고 안내한다. 현재 프로젝트는 일부 output screening은 있지만 정기 감사/평가 자동화와 누출 전용 시나리오 테스트가 부족하다.

## 추천 로드맵

### 1단계. 보안 경계 바로 수정

- admin tenant scope 강제
- actuator private화
- API key hash 저장 및 rotation 설계
- provider 오류 메시지 외부 노출 제거

### 2단계. AI Gateway답게 기능 업그레이드

- moderation provider 추가
- structured outputs + response schema 지원
- streaming/SSE 지원
- provider capability abstraction 도입
- 실제 usage 기반 quota/cost accounting

### 3단계. 운영형 플랫폼으로 전환

- eval harness + red-team dataset
- OpenTelemetry tracing
- policy approval workflow
- DB migration/Flyway
- async job execution and polling

## 내가 보기에 가장 중요한 5개

1. Admin의 tenant scope 미검증
2. 공개 actuator metrics
3. 정적 규칙 위주의 prompt injection 방어
4. structured outputs / tool calling / streaming 부재
5. eval 체계 부재

## 참고 소스

코드 기준:

- `src/main/java/com/example/aigateway/api/controller/AdminController.java`
- `src/main/java/com/example/aigateway/infrastructure/security/SecurityConfig.java`
- `src/main/java/com/example/aigateway/infrastructure/security/ApiKeyClientRegistry.java`
- `src/main/java/com/example/aigateway/infrastructure/provider/RestClientOpenAiApiClient.java`
- `src/main/java/com/example/aigateway/application/service/ProviderExecutionService.java`
- `src/main/java/com/example/aigateway/application/service/TokenEstimator.java`
- `src/main/resources/application.yml`

외부 공식 문서:

- OWASP Top 10 for LLM Applications: https://owasp.org/www-project-top-10-for-large-language-model-applications/
- OpenAI Moderation Guide: https://developers.openai.com/api/docs/guides/moderation
- OpenAI Structured Outputs Guide: https://developers.openai.com/api/docs/guides/structured-outputs
- OpenAI Evals Guide: https://developers.openai.com/api/docs/guides/evals
- OpenAI Background Mode Guide: https://developers.openai.com/api/docs/guides/background
- OpenAI GPT-5.1 model docs: https://developers.openai.com/api/docs/models/gpt-5.1
- OpenAI GPT-4o model docs: https://developers.openai.com/api/docs/models/gpt-4o
- Anthropic Reduce Prompt Leak: https://platform.claude.com/docs/en/test-and-evaluate/strengthen-guardrails/reduce-prompt-leak

## 결론

이 프로젝트는 방향은 맞다. 다만 현재 상태는 "잘 만든 데모형 Enterprise AI Gateway"에 가깝고, 실제 운영형 플랫폼으로 가려면 보안 경계, safety 계층, provider feature abstraction, 평가 자동화가 더 필요하다.

특히 `tenant 권한 경계`, `공개 메트릭`, `정적 규칙 중심의 가드레일`, `최신 LLM API 기능 부재`, `eval 부재`는 우선순위를 높게 두는 편이 맞다.
