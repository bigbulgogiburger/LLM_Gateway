# AI Gateway Gap Analysis

## 목적

이 문서는 현재 `LLM Gateway` MVP를 기준으로, 실제 운영 가능한 Enterprise AI Gateway로 발전시키기 위해 보완해야 할 부족한 지점과 추가 개발 항목을 정리한 것이다.

기준 관점:

- LLM Gateway 아키텍처 완성도
- 보안 및 정책 집행 수준
- 운영 안정성
- 확장성
- 관측 가능성
- 테스트 및 품질

## 현재 상태 요약

현재 프로젝트는 다음 범위까지는 구현되어 있다.

- 단일 REST 엔드포인트 기반 AI Gateway
- Input Rule Guardrail
- Mock AI Guardrail
- `mock` / `openai` provider 라우팅
- Output Guardrail의 기본 차단/마스킹
- YAML 기반 정책 외부화의 초안
- 기본 단위 테스트 및 서비스 테스트

이는 MVP로서는 충분하지만, 운영 환경 기준으로는 아직 중요한 공백이 있다.

## 핵심 부족 지점

### 1. 인증 및 인가 계층 부재

현재는 `userId`, `role`을 요청 바디에서 그대로 받는다.

문제:

- 호출자가 누구인지 검증하지 않는다.
- 사용자가 임의로 `role=OPERATOR` 같은 권한 값을 넣을 수 있다.
- Gateway가 신뢰 경계(trust boundary) 역할을 제대로 하지 못한다.

개선 방향:

- API Key, JWT, OAuth2 중 하나 이상 도입
- 인증 정보에서 `userId`, `role`, `tenantId`를 추출하도록 변경
- 요청 바디의 `role`은 제거하거나 무시
- Role Policy는 인증 컨텍스트 기반으로 집행

우선순위: 매우 높음

### 2. 테넌트 격리와 정책 분리 미구현

현재 정책은 전역 `application.yml` 하나에 묶여 있다.

문제:

- 기업형 Gateway의 핵심 요구인 테넌트별 정책 분리 불가
- 고객 A와 B가 다른 금지어, 다른 모델, 다른 길이 제한을 사용할 수 없음
- 비용/쿼터/로그/감사도 테넌트 기준 분리 불가

개선 방향:

- `tenantId` 개념 추가
- `PolicyResolver` 계층 도입
- 정책 조회를 `tenantId + role + provider` 기준으로 수행
- 향후 DB/Config Server/Policy Repo 기반 확장

우선순위: 매우 높음

### 3. Rate Limit / Quota / Budget 제어 부재

현재는 모든 요청이 제한 없이 provider까지 전달된다.

문제:

- OpenAI 비용 폭주 가능
- 특정 사용자 또는 테넌트의 남용 방지 불가
- Gateway의 경제적 보호 기능이 없음

개선 방향:

- 사용자/테넌트 단위 rate limit
- 일/월 단위 token budget
- provider별 usage accounting
- soft limit / hard limit 정책 분리

우선순위: 매우 높음

### 4. OpenAI 연동의 운영 보호장치 부족

현재 OpenAI 호출은 기본 성공 경로 위주다.

문제:

- timeout, retry, circuit breaker, fallback 전략이 없음
- 429, 5xx, network timeout 처리 정책이 부족
- provider SLA 문제 발생 시 Gateway 전체 품질 저하

개선 방향:

- connect/read timeout 명시
- retry 정책 도입
- circuit breaker 도입
- provider 장애 시 fallback provider 또는 graceful degradation
- error taxonomy 정리

우선순위: 매우 높음

### 5. Output Guardrail의 정책 깊이 부족

현재 Output Guardrail은 키워드 차단과 단순 PII 마스킹 수준이다.

문제:

- 모델의 환각(hallucination), 위험 조언, 내부 정책 재노출, 기밀 요약 노출 등을 충분히 다루지 못함
- output이 왜 차단되었는지 정책 레벨 설명이 약함
- 부분 수정, 대체 응답 생성, human review 전환 같은 후속 액션이 없음

개선 방향:

- output risk classification 추가
- `ALLOW / MASK / REWRITE / BLOCK / REVIEW` 액션 모델로 확장
- output에 대한 AI Guardrail 추가
- 차단 시 안전한 대체 응답 템플릿 제공

우선순위: 높음

### 6. Prompt Injection 대응이 정적 패턴 수준에 머물러 있음

현재 AI Guardrail은 문자열 패턴 기반 Mock 판정이다.

문제:

- 실제 공격 패턴은 우회 표현이 많음
- 인코딩/분절/우회 표현에 취약
- 도구 호출, RAG 문서, system prompt leakage 대응이 불충분

개선 방향:

- 실제 LLM 기반 classifier 또는 외부 guardrail engine 도입
- jailbreak/prompt injection 전용 룰셋 확장
- tool calling 전 preflight guardrail
- RAG 검색 결과와 system/developer prompt 분리 보호

우선순위: 높음

### 7. 대화 컨텍스트 모델 부재

현재는 단일 prompt 기반 요청만 처리한다.

문제:

- 실제 서비스는 multi-turn 대화가 일반적
- 이전 맥락 기반 공격이나 누적 정책 위반 탐지가 불가
- 대화 히스토리 크기 제한, 요약, 메모리 전략이 없음

개선 방향:

- messages 배열 기반 요청 모델 지원
- conversation/session ID 추가
- 대화 히스토리 압축/요약 전략
- turn-level audit trail

우선순위: 높음

### 8. 표준화된 오류 체계가 아직 얕음

기본적인 예외 응답은 추가되었지만, Gateway 수준의 명세는 부족하다.

문제:

- blocked, provider_failure, validation_error, policy_error, timeout 등이 명확히 분리되지 않음
- 클라이언트가 자동 처리하기 어려움
- 운영팀과 보안팀이 같은 오류를 다른 의미로 해석할 수 있음

개선 방향:

- Gateway 공통 error code 체계 설계
- HTTP status와 business code를 분리
- provider error normalization
- traceId/requestId 기반 장애 추적 강화

우선순위: 높음

### 9. 감사 로그는 있으나 감사 저장소는 없음

현재는 Slf4j 로그 중심이다.

문제:

- 장기 감사 보관, 검색, 규제 대응이 불가
- 누가 어떤 정책에 의해 차단되었는지 집계가 어려움
- 운영 대시보드나 보안 분석에 직접 쓰기 어려움

개선 방향:

- structured audit event 스키마 정의
- DB, Kafka, Elasticsearch, OTEL exporter 중 하나로 전송
- `tenantId`, `policyId`, `model`, `latency`, `tokenUsage`, `decisionPath` 저장

우선순위: 높음

### 10. Observability가 부족함

현재는 애플리케이션 로그 외 지표와 트레이싱이 없다.

문제:

- provider latency, block rate, output mask rate, cost per tenant 추적 불가
- 장애 원인 분석 속도가 느림

개선 방향:

- Micrometer metrics
- OpenTelemetry tracing
- provider별 latency, success rate, timeout rate 지표
- rule hit count 및 policy hit count 지표

우선순위: 높음

### 11. Provider 추상화는 존재하지만 상용 수준의 기능은 부족

문제:

- streaming 미지원
- function/tool calling 미지원
- model capability matrix 없음
- provider별 파라미터 매핑 계층 없음

개선 방향:

- `ProviderCapabilities` 모델 추가
- streaming 응답 지원
- tool calling / JSON mode / structured output 지원
- provider별 request/response normalizer 도입

우선순위: 중간 이상

### 12. 입력/출력 정책 외부화는 YAML 수준에 머물러 있음

현재는 설정 외부화가 되었지만 정적 파일 중심이다.

문제:

- 운영 중 정책을 실시간 변경하기 어려움
- 승인 워크플로우/버전 관리/롤백이 없음

개선 방향:

- 정책 버전 모델 추가
- 정책 저장소 추상화
- hot reload 또는 관리 API 도입
- 정책 변경 이력과 승인 로그 관리

우선순위: 중간 이상

### 13. 테스트 범위가 운영 시나리오까지 확장되지 않음

현재 테스트는 MVP 기준으로는 충분하나, 상용 수준은 아니다.

부족한 테스트:

- OpenAI API contract test
- timeout/retry/failure simulation test
- output masking/blocking의 controller level test
- large prompt / unicode / malformed json test
- concurrent load test
- policy matrix test

개선 방향:

- WireMock 기반 provider integration test
- performance test 시나리오 추가
- policy regression snapshot test

우선순위: 중간 이상

### 14. 비동기 처리 및 큐 기반 확장 포인트가 없음

문제:

- 장시간 요청, review workflow, human-in-the-loop 처리에 적합하지 않음
- 동기형 REST 호출만으로는 고비용 AI 워크로드 운영이 어려움

개선 방향:

- async job model 추가
- webhook/callback 또는 polling 결과 조회
- REVIEW 판정 시 human approval queue 연동

우선순위: 중간

### 15. 보안 하드닝이 부족함

문제:

- 입력 payload size 제한, IP 제한, CORS 정책, secure headers, audit tamper protection 등이 없음
- secrets rotation 및 key source 전략 미정

개선 방향:

- Spring Security 기반 API 보호
- request size 제한
- secret manager 연동
- audit log integrity 전략

우선순위: 중간

## 추가로 반드시 개발해야 할 항목

### 단기 1순위

1. 인증/인가
2. rate limit / quota / budget
3. provider timeout / retry / circuit breaker
4. 테넌트별 정책 분리
5. provider error normalization

### 단기 2순위

1. output AI guardrail
2. structured audit event 저장
3. observability metrics / tracing
4. OpenAI integration test
5. conversation context 모델

### 중기

1. Claude/Gemini provider 추가
2. streaming response
3. tool calling / MCP 보호 레이어
4. RAG 전/후 Guardrail
5. 정책 관리 UI 또는 Admin API

## 권장 아키텍처 보강

### 1. Policy Layer 분리

추천 구성:

- `PolicyRepository`
- `PolicyResolver`
- `ResolvedPolicy`
- `PolicyVersion`

효과:

- tenant / role / provider별 정책 조합 가능
- 운영 중 정책 교체 및 롤백 가능

### 2. Provider Execution Layer 분리

추천 구성:

- `ProviderRequestNormalizer`
- `ProviderExecutor`
- `ProviderResponseNormalizer`
- `ProviderErrorMapper`

효과:

- provider별 구현 차이를 application 계층에서 제거
- OpenAI/Claude/Gemini 추가 시 변경 비용 축소

### 3. Decision Trace 모델 도입

추천 구성:

- `DecisionTrace`
- `DecisionStep`
- `AppliedPolicy`
- `ProviderExecutionTrace`

효과:

- 왜 차단/허용되었는지 설명 가능
- 감사/운영/보안 분석에 유리

## 추천 로드맵

### Phase 1: 운영 보호 최소선

- 인증/인가
- rate limit
- provider resilience
- structured error code
- audit event schema

### Phase 2: 정책/조직화

- tenant-aware policy
- policy versioning
- observability
- admin configuration flow

### Phase 3: AI Gateway 고도화

- output AI guardrail
- multi-turn conversation
- RAG / MCP guardrail
- tool execution governance

## 결론

현재 구현은 “동작하는 Gateway MVP”로서는 적절하다. 다만 “기업형 AI Gateway”라고 부르기 위해서는 다음이 반드시 추가되어야 한다.

- 인증/인가
- 테넌트별 정책 분리
- 비용 및 트래픽 통제
- provider 장애 대응
- 강화된 observability와 audit

가장 중요한 포인트는, 앞으로의 개발이 단순 기능 추가가 아니라 `정책`, `보안`, `비용`, `운영` 중심으로 이동해야 한다는 점이다.  
즉 다음 단계의 핵심은 “LLM을 호출하는 앱”이 아니라 “조직의 AI 사용을 통제하는 플랫폼”으로 구조를 전환하는 것이다.
