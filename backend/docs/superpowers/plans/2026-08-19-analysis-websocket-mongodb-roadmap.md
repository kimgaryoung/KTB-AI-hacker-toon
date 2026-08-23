# Analysis WebSocket MongoDB Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 카카오톡 파싱 메시지와 체크인을 입력으로 메시지 패턴, 감정 흐름, PRQC 6요소, 종합점수를 계산하고 WebSocket으로 진행 상태를 전달하는 분석 파이프라인을 구현한다.

**Architecture:** PostgreSQL은 사용자 소유권, 관계, 체크인, 분석 Job, 최종 리포트의 기준 데이터로 유지한다. MongoDB에는 파싱 메시지와 재실행 가능한 분석 중간 산출물을 저장한다. 분석 단계 변경은 하나의 `AnalysisProgressPublisher`를 통과시키고, DB 저장 성공 후 사용자 전용 WebSocket 채널로 같은 스냅샷을 전송한다. 기존 `GET /analysis-jobs/{jobId}`는 재접속과 장애 복구용으로 유지한다.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC, Spring WebSocket/STOMP, Spring Data JPA, PostgreSQL, Spring Data MongoDB, JUnit 5, MockMvc, WebSocket STOMP test client

**Spec:** `docs/API_SPEC.md` (분석 시작·Job 조회·리포트 계약), 본 문서의 WebSocket 확장 계약

## Global Constraints

- 상태 흐름은 `QUEUED → RUNNING → SUCCEEDED | FAILED | CANCELED`만 허용한다.
- 진행률은 성공 전 최대 95이며 `SUCCEEDED`일 때만 100이다.
- 진행 상태의 기준 데이터는 PostgreSQL `analysis_jobs`다. WebSocket 메시지는 알림이며 진실의 원천이 아니다.
- 파싱 메시지와 분석 중간 산출물은 MongoDB에 저장한다.
- 체크인 원본은 PostgreSQL에 유지하고 분석 시작 시 불변 스냅샷으로 읽는다.
- 기존 `POST /relationships/{relationshipId}/analyses`와 `GET /analysis-jobs/{jobId}` 계약을 깨지 않는다.
- WebSocket 구독 전에 HTTP 세션으로 인증하고, 본인 Job 채널만 구독할 수 있어야 한다.
- 첫 버전의 감정 분석은 규칙 기반 구현으로 시작하고 AI/ML 구현은 인터페이스 뒤에 둔다.

---

## 권장 개발 순서

### Task 1: 현재 동작을 테스트로 고정

**Files:**
- Create: `src/test/java/com/relationshiptemperature/api/analysis/application/AnalysisServiceTest.java`
- Create: `src/test/java/com/relationshiptemperature/api/analysis/domain/AnalysisJobTest.java`
- Modify: `docs/API_SPEC.md`

**Produces:** 현재 HTTP 분석 시작·조회 계약과 상태 전이 회귀 테스트.

- [ ] 유효한 관계·파일·체크인으로 Job이 `QUEUED`, progress 0으로 생성되는 테스트 작성.
- [ ] 진행 중 Job 중복 생성이 `ANALYSIS_ALREADY_RUNNING`이 되는 테스트 작성.
- [ ] `progress()`가 95를 넘지 않고 `complete()`만 100을 만드는 테스트 작성.
- [ ] 실패 상태가 오류 코드, 메시지, retryable 값을 보존하는 테스트 작성.
- [ ] `./gradlew test --tests '*AnalysisServiceTest' --tests '*AnalysisJobTest'` 실행.
- [ ] 커밋: `test: lock analysis job lifecycle`

### Task 2: WebSocket 공개 계약 확정

**Files:**
- Modify: `docs/API_SPEC.md`
- Modify: `docs/openapi.yaml`
- Create: `docs/ASYNC_API_SPEC.md`

**Produces:** WebSocket 연결·구독 경로와 payload 계약.

- [ ] 연결 endpoint를 `/ws`로 정의하고 기존 `rt_session` 인증을 재사용한다고 명시.
- [ ] 구독 destination을 `/user/queue/analysis-jobs`로 정의.
- [ ] 메시지 payload를 `jobId`, `relationshipId`, `status`, `stage`, `progress`, `reportId`, `failure`, `updatedAt`으로 고정.
- [ ] 최초 구독 직후에는 `GET /analysis-jobs/{jobId}`로 현재 상태를 동기화하도록 프론트 규칙 명시.
- [ ] 재접속, 중복 이벤트, 순서 역전 처리 규칙을 `updatedAt` 기준으로 명시.
- [ ] 커밋: `docs: define analysis websocket contract`

### Task 3: MongoDB 파싱 메시지 저장소 전환

**Files:**
- Create: `src/main/java/com/relationshiptemperature/api/conversation/document/ConversationMessageDocument.java`
- Create: `src/main/java/com/relationshiptemperature/api/conversation/repository/ConversationMessageDocumentRepository.java`
- Modify: `src/main/java/com/relationshiptemperature/api/conversation/application/ConversationFileService.java`
- Modify: `src/main/resources/db/migration/V1__initial_schema.sql`은 수정하지 않음
- Create: `src/test/java/com/relationshiptemperature/api/conversation/repository/ConversationMessageDocumentRepositoryTest.java`

**Produces:** `findAllByConversationFileIdOrderBySequenceNumber(UUID)`와 Mongo 복합 unique index `(conversationFileId, sequenceNumber)`.

- [ ] Mongo Document 직렬화·역직렬화 테스트 작성.
- [ ] 동일 파일/순번 중복 저장 실패 테스트 작성.
- [ ] 업로드 성공 시 Mongo에 순서대로 저장되는 서비스 테스트 작성.
- [ ] 업로드 처리 실패 시 저장된 Mongo 문서를 삭제하는 보상 처리 테스트 작성.
- [ ] 기존 JPA `ConversationMessage` 쓰기를 제거하되, 운영 데이터 마이그레이션 전까지 읽기 fallback은 별도 플래그로 격리.
- [ ] Mongo 저장소 테스트 및 업로드 테스트 실행.
- [ ] 커밋: `feat: store parsed conversation messages in mongodb`

### Task 4: 분석 입력 스냅샷 조립

**Files:**
- Create: `src/main/java/com/relationshiptemperature/api/analysis/application/AnalysisInputLoader.java`
- Create: `src/main/java/com/relationshiptemperature/api/analysis/application/AnalysisInput.java`
- Test: `src/test/java/com/relationshiptemperature/api/analysis/application/AnalysisInputLoaderTest.java`

**Produces:** `AnalysisInput load(AnalysisJob job)`.

- [ ] Mongo 메시지가 sequence 순서로 로드되는 테스트 작성.
- [ ] 체크인 두 문항이 questionCode별 점수 Map으로 로드되는 테스트 작성.
- [ ] 파일·체크인·관계가 서로 다르면 분석을 중단하는 테스트 작성.
- [ ] 빈 대화, 순번 중복, 체크인 누락에 대한 명시적 오류 테스트 작성.
- [ ] 최소 구현 후 단위 테스트 실행.
- [ ] 커밋: `feat: assemble immutable analysis input`

### Task 5: 메시지 패턴 계산기

**Files:**
- Create: `src/main/java/com/relationshiptemperature/api/analysis/pattern/MessagePatternAnalyzer.java`
- Create: `src/main/java/com/relationshiptemperature/api/analysis/pattern/MessagePatternMetrics.java`
- Test: `src/test/java/com/relationshiptemperature/api/analysis/pattern/MessagePatternAnalyzerTest.java`

**Produces:** 메시지 수, 발화 비율, 평균 응답시간, 대화 시작 비율, 시간대별 빈도, 주별 추세.

- [ ] 10~20개 고정 메시지 fixture로 각 지표 기대값 테스트 작성.
- [ ] 연속 발화는 하나의 turn으로 묶는 규칙 테스트 작성.
- [ ] 상대방 응답이 없는 마지막 메시지는 응답시간에서 제외하는 테스트 작성.
- [ ] 시간 계산은 UTC 저장 후 `Asia/Seoul` 기준 집계하도록 구현.
- [ ] 단위 테스트 실행.
- [ ] 커밋: `feat: calculate conversation pattern metrics`

### Task 6: 감정 흐름 분석기

**Files:**
- Create: `src/main/java/com/relationshiptemperature/api/analysis/emotion/EmotionAnalyzer.java`
- Create: `src/main/java/com/relationshiptemperature/api/analysis/emotion/RuleBasedEmotionAnalyzer.java`
- Create: `src/main/java/com/relationshiptemperature/api/analysis/emotion/EmotionFlow.java`
- Test: `src/test/java/com/relationshiptemperature/api/analysis/emotion/RuleBasedEmotionAnalyzerTest.java`

**Produces:** `EmotionFlow analyze(List<ConversationMessageDocument>)`.

- [ ] 긍정·부정·중립 표현 사전과 부정어 반전 규칙을 테스트 fixture로 고정.
- [ ] 메시지별 점수를 주 단위로 집계하는 테스트 작성.
- [ ] 빈 구간은 0점으로 만들지 않고 `null`로 표시하도록 테스트.
- [ ] 향후 AI 구현이 같은 인터페이스를 구현하도록 규칙 기반 분석기 외부 의존성 제거.
- [ ] 단위 테스트 실행.
- [ ] 커밋: `feat: add replaceable emotion flow analyzer`

### Task 7: PRQC 6요소와 종합점수 계산

**Files:**
- Create: `src/main/java/com/relationshiptemperature/api/analysis/scoring/PrqcCalculator.java`
- Create: `src/main/java/com/relationshiptemperature/api/analysis/scoring/PrqcCalculation.java`
- Modify: `src/main/java/com/relationshiptemperature/api/report/application/RelationshipScoringPolicy.java`
- Test: `src/test/java/com/relationshiptemperature/api/analysis/scoring/PrqcCalculatorTest.java`
- Test: `src/test/java/com/relationshiptemperature/api/report/application/RelationshipScoringPolicyTest.java`

**Produces:** satisfaction, commitment, intimacy, trust, passion, love 점수와 근거, canonical overall score.

- [ ] 각 PRQC 요소가 어떤 패턴·감정·체크인 입력을 사용하는지 가중치 표를 문서에 명시.
- [ ] 모든 점수가 0~100으로 clamp되는 경계 테스트 작성.
- [ ] 동일 입력은 항상 동일 결과를 만드는 결정성 테스트 작성.
- [ ] 체크인 반영 여부를 `scoringPolicyVersion`에 포함하고 기존 API 문서와 충돌을 해소.
- [ ] 전체 점수는 오직 `RelationshipScoringPolicy`에서 계산하도록 구현.
- [ ] 커밋: `feat: calculate prqc and overall relationship score`

### Task 8: MongoDB 분석 실행 문서와 단계별 체크포인트

**Files:**
- Create: `src/main/java/com/relationshiptemperature/api/analysis/document/AnalysisRunDocument.java`
- Create: `src/main/java/com/relationshiptemperature/api/analysis/repository/AnalysisRunDocumentRepository.java`
- Test: `src/test/java/com/relationshiptemperature/api/analysis/repository/AnalysisRunDocumentRepositoryTest.java`

**Produces:** Job별 입력 버전, 패턴 결과, 감정 흐름, PRQC 결과를 저장하는 Mongo 문서.

- [ ] `(jobId)` unique index 테스트 작성.
- [ ] 단계 완료마다 결과와 `pipelineVersion`을 저장하는 테스트 작성.
- [ ] 민감한 원문 메시지는 중간 산출물에 복제하지 않는 검증 테스트 작성.
- [ ] 실패 후 마지막 완료 단계부터 재실행할 수 있는 조회 메서드 구현.
- [ ] 커밋: `feat: persist analysis pipeline checkpoints`

### Task 9: 파이프라인 오케스트레이터로 Runner 분해

**Files:**
- Create: `src/main/java/com/relationshiptemperature/api/analysis/application/AnalysisPipeline.java`
- Create: `src/main/java/com/relationshiptemperature/api/analysis/application/AnalysisProgressPublisher.java`
- Modify: `src/main/java/com/relationshiptemperature/api/analysis/application/AnalysisJobRunner.java`
- Test: `src/test/java/com/relationshiptemperature/api/analysis/application/AnalysisPipelineTest.java`

**Produces:** 단계별 실행과 상태 저장을 담당하는 `AnalysisPipeline.execute(UUID jobId)`.

- [ ] 단계 순서 `LOADING_CONVERSATION(10) → ANALYZING_MESSAGE_PATTERNS(35) → ANALYZING_EMOTIONAL_FLOW(60) → CALCULATING_PRQC(85) → CALCULATING_RELATIONSHIP_SCORE(95) → SUCCEEDED(100)` 테스트 작성.
- [ ] 각 단계 DB 저장이 성공한 뒤에만 다음 단계가 실행되는 테스트 작성.
- [ ] 예외 발생 시 Job과 관계 상태가 함께 실패 처리되는 테스트 작성.
- [ ] 리포트 저장과 Job 완료 순서 테스트 작성.
- [ ] 기존 AI HTTP 호출은 별도 구현체로 남겨 로컬 계산 파이프라인과 선택 가능하게 구성.
- [ ] 커밋: `refactor: orchestrate analysis as staged pipeline`

### Task 10: WebSocket 진행 상태 푸시

**Files:**
- Modify: `build.gradle`
- Create: `src/main/java/com/relationshiptemperature/api/config/WebSocketConfig.java`
- Create: `src/main/java/com/relationshiptemperature/api/analysis/web/AnalysisProgressMessage.java`
- Create: `src/main/java/com/relationshiptemperature/api/analysis/infrastructure/WebSocketAnalysisProgressPublisher.java`
- Test: `src/test/java/com/relationshiptemperature/api/analysis/web/AnalysisWebSocketIntegrationTest.java`

**Produces:** 인증 사용자 전용 `/user/queue/analysis-jobs` 상태 메시지.

- [ ] Spring WebSocket 의존성을 추가하고 `/ws` endpoint 테스트 작성.
- [ ] 비로그인 연결 거부 테스트 작성.
- [ ] 다른 사용자의 Job 이벤트를 받지 못하는 소유권 테스트 작성.
- [ ] 각 단계에서 DB의 현재 스냅샷과 동일한 payload가 전송되는 테스트 작성.
- [ ] 완료·실패 이벤트 전송 테스트 작성.
- [ ] 커밋: `feat: push analysis progress over websocket`

### Task 11: 재접속·중복 실행·운영 복구

**Files:**
- Modify: `src/main/java/com/relationshiptemperature/api/analysis/application/AnalysisService.java`
- Create: `src/main/java/com/relationshiptemperature/api/analysis/application/StaleAnalysisJobRecovery.java`
- Test: `src/test/java/com/relationshiptemperature/api/analysis/application/StaleAnalysisJobRecoveryTest.java`

**Produces:** WebSocket 유실과 서버 재시작에도 복구 가능한 분석 Job.

- [ ] WebSocket 재연결 후 GET 조회로 최종 상태를 복구하는 통합 테스트 작성.
- [ ] 오래된 RUNNING Job을 FAILED 또는 재큐잉하는 정책 테스트 작성.
- [ ] 동일 Job 중복 실행 시 Mongo checkpoint와 리포트 unique 제약으로 한 번만 완료되는 테스트 작성.
- [ ] 종료 요청 시 진행 중 executor가 안전하게 중단되는 테스트 작성.
- [ ] 커밋: `feat: recover interrupted analysis jobs`

### Task 12: 전체 통합·성능·보안 검증

**Files:**
- Create: `src/test/java/com/relationshiptemperature/api/analysis/AnalysisPipelineEndToEndTest.java`
- Modify: `README.md`
- Modify: `compose.yaml`

**Produces:** 업로드부터 WebSocket 완료 이벤트와 리포트 조회까지의 실행 가능한 개발 환경.

- [ ] PostgreSQL·MongoDB를 포함한 로컬 compose 실행 절차 작성.
- [ ] 업로드 → 체크인 → 분석 시작 → WebSocket 상태 수신 → 리포트 조회 E2E 테스트 작성.
- [ ] 50MB 파일에서 Mongo batch insert와 분석 메모리 사용량 측정.
- [ ] 로그에 원문 메시지, 토큰, 세션 ID가 남지 않는지 검사.
- [ ] `./gradlew clean test` 실행.
- [ ] OpenAPI와 WebSocket 문서의 payload를 실제 DTO와 대조.
- [ ] 커밋: `test: verify analysis pipeline end to end`

## 마일스톤

1. **M1 데이터 준비:** Task 1~4 — 파싱 결과가 MongoDB에 있고 분석 입력을 재현 가능하게 로드한다.
2. **M2 계산 엔진:** Task 5~8 — 패턴·감정·PRQC·종합점수를 독립적으로 계산하고 중간 결과를 저장한다.
3. **M3 실시간 실행:** Task 9~10 — 단계 파이프라인과 WebSocket 진행 알림이 동작한다.
4. **M4 운영 가능:** Task 11~12 — 재접속·재시작·중복 실행·보안·성능을 검증한다.

## 첫 작업 권장 범위

첫 PR은 **Task 1만** 수행한다. 현재 상태 전이 테스트를 먼저 고정해야 이후 MongoDB 전환과 WebSocket 도입 과정에서 기존 분석 API가 깨졌는지 즉시 판단할 수 있다.
