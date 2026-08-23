# Async Analysis and WebSocket Consultation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist normalized Kakao conversations, complete the asynchronous PRQC pipeline, expose imported chat history, and add session-authenticated WebSocket consultation while retaining REST/SSE.

**Architecture:** Both TXT and CSV uploads become immutable `ConversationMessage` rows. Analysis and consultation use separate serializers over those rows: NDJSON gzip for PRQC and `Date,User,Message` CSV plus participant metadata for consultation AI. REST, SSE, and WebSocket share one consultation command/event service.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC, Spring WebSocket, Spring Security/Session JDBC, Spring Data JPA, Flyway, Apache Commons CSV, JUnit 5, AssertJ, MockMvc, WebSocket test client, OpenAPI 3.1.

**Spec:** `docs/superpowers/specs/2026-08-19-async-analysis-websocket-chat-design.md`

## Global Constraints

- Preserve the public REST/SSE behavior in `backend/docs/API_SPEC.md` while adding WebSocket.
- Send PRQC input as `NORMALIZED_NDJSON_GZIP`; send consultation context as `Date,User,Message` CSV.
- Treat `강명진` as `SELF` and `이진우` as `OTHER` for the supplied sample.
- Keep check-in values out of PRQC and overall score calculations.
- Never log raw conversation content, generated CSV, local storage keys, or AI credentials.
- Use a failing test before every production behavior change.
- Do not overwrite unrelated existing changes in `backend/docs/API_SPEC.md`, `backend/docs/API_ENDPOINT_CATALOG.md`, or `backend/docs/generate_api_catalog.rb`.

---

### Task 1: Conversation persistence schema and domain

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__add_normalized_conversation_messages.sql`
- Create: `backend/src/main/java/com/relationshiptemperature/api/conversation/domain/ConversationParticipantRole.java`
- Create: `backend/src/main/java/com/relationshiptemperature/api/conversation/domain/ConversationMessage.java`
- Create: `backend/src/main/java/com/relationshiptemperature/api/conversation/repository/ConversationMessageRepository.java`
- Modify: `backend/src/main/java/com/relationshiptemperature/api/conversation/domain/ConversationFile.java`
- Test: `backend/src/test/java/com/relationshiptemperature/api/conversation/infrastructure/ConversationMessageMigrationTest.java`
- Test: `backend/src/test/java/com/relationshiptemperature/api/conversation/domain/ConversationMessageTest.java`

**Interfaces:**
- Produces: `ConversationMessage(UUID conversationFileId, UUID relationshipId, int sequenceNumber, Instant sentAt, String senderName, ConversationParticipantRole participantRole, String content)`.
- Produces: `List<ConversationMessage> findAllByConversationFileIdOrderBySequenceNumberAsc(UUID fileId)`.
- Produces: `Optional<ConversationFile> findFirstByRelationshipIdAndValidationStatusOrderByCreatedAtDesc(UUID relationshipId, ConversationFileStatus status)`.

- [ ] **Step 1: Write migration and domain tests that fail because the table, participant fields, entity, and constraints do not exist.** Assert `conversation_files.self_participant_name`, `other_participant_name`, `conversation_messages` ordering, `(conversation_file_id, sequence_number)` uniqueness, and nonblank domain fields.
- [ ] **Step 2: Run `cd backend && ./gradlew test --tests '*ConversationMessageMigrationTest' --tests '*ConversationMessageTest'` and verify the expected missing-schema/class failure.**
- [ ] **Step 3: Add V5 and minimal entities/repositories.** V5 adds participant columns, creates `conversation_messages(id, conversation_file_id, relationship_id, sequence_number, sent_at, sender_name, participant_role, content, created_at, updated_at)`, foreign keys with cascade deletion, a unique sequence constraint, and relationship/file indexes.
- [ ] **Step 4: Run the focused tests and `cd backend && ./gradlew test`; verify all pass.**
- [ ] **Step 5: Commit with `git commit -m "feat: persist normalized conversation messages"`.**

### Task 2: CSV and Kakao TXT parsing

**Files:**
- Modify: `backend/build.gradle`
- Replace: `backend/src/main/java/com/relationshiptemperature/api/conversation/application/KakaoConversationParser.java`
- Create: `backend/src/main/java/com/relationshiptemperature/api/conversation/application/ParsedConversation.java`
- Create: `backend/src/main/java/com/relationshiptemperature/api/conversation/infrastructure/KakaoCsvConversationParser.java`
- Replace: `backend/src/main/java/com/relationshiptemperature/api/conversation/infrastructure/BasicKakaoConversationParser.java`
- Create: `backend/src/main/java/com/relationshiptemperature/api/conversation/infrastructure/ConversationParserRouter.java`
- Test: `backend/src/test/java/com/relationshiptemperature/api/conversation/infrastructure/KakaoCsvConversationParserTest.java`
- Modify: `backend/src/test/java/com/relationshiptemperature/api/conversation/infrastructure/BasicKakaoConversationParserTest.java`

**Interfaces:**
- Produces: `ParsedConversation parse(InputStream input, String selfParticipantName)` with `List<ParsedMessage> messages`, `selfParticipantName`, and `otherParticipantName`.
- Produces: `ParsedMessage(int sequenceNumber, Instant sentAt, String senderName, ConversationParticipantRole role, String content)`.
- Produces: `ParsedConversation parse(String extension, InputStream input, String selfParticipantName)` on `ConversationParserRouter`.

- [ ] **Step 1: Add failing CSV tests using an in-test fixture with BOM, header, the supplied two participants, quoted comma, escaped quote, and embedded newline.** Assert six base messages, `강명진=SELF`, `이진우=OTHER`, Asia/Seoul-to-UTC conversion, and exact content preservation.
- [ ] **Step 2: Add failing TXT tests for `2026년 8월 19일 수요일` and `오후 7:23 강명진 사진 txt파일은 이거야`, including multiline continuation.** Assert the same normalized representation and reject missing self, group chat, invalid header/date, and empty content.
- [ ] **Step 3: Run `cd backend && ./gradlew test --tests '*ConversationParser*Test' --tests '*BasicKakaoConversationParserTest'` and verify parser assertions fail.**
- [ ] **Step 4: Add `org.apache.commons:commons-csv`, implement the router and parsers with `Asia/Seoul` as the Kakao export zone.** Strip BOM only from the first header and use Commons CSV rather than `String.split`.
- [ ] **Step 5: Run focused and full tests; verify pass without warnings.**
- [ ] **Step 6: Commit with `git commit -m "feat: parse kakao csv and txt conversations"`.**

### Task 3: Upload normalized messages and enforce ownership/duplicates

**Files:**
- Modify: `backend/src/main/java/com/relationshiptemperature/api/conversation/application/ConversationFileService.java`
- Modify: `backend/src/main/java/com/relationshiptemperature/api/conversation/repository/ConversationFileRepository.java`
- Modify: `backend/src/main/java/com/relationshiptemperature/api/conversation/web/ConversationFileController.java`
- Modify: `backend/src/main/java/com/relationshiptemperature/api/config/AppProperties.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/com/relationshiptemperature/api/common/error/ErrorCode.java`
- Test: `backend/src/test/java/com/relationshiptemperature/api/conversation/web/ConversationFileControllerIntegrationTest.java`

**Interfaces:**
- Changes upload multipart fields to `file`, `source=KAKAO_TALK`, and required `selfParticipantName`.
- Produces response fields `selfParticipantName` and `otherParticipantName` in addition to the existing file contract.

- [ ] **Step 1: Write failing integration tests for CSV upload, TXT upload, persisted normalized rows, participant response fields, unsupported extension, duplicate relationship/hash, and self-name mismatch.**
- [ ] **Step 2: Run the focused controller test and verify failure occurs because CSV/self participant support is absent.**
- [ ] **Step 3: Implement upload orchestration.** Save raw bytes, route by extension, persist file metadata and normalized rows in one transaction, configure `[txt,csv]`, and delete stored raw bytes on parse/persistence failure.
- [ ] **Step 4: Implement duplicate lookup `findByRelationshipIdAndSha256(...)` and map invalid/group/duplicate cases to explicit API error codes without leaking content.**
- [ ] **Step 5: Run focused and full tests.**
- [ ] **Step 6: Commit with `git commit -m "feat: normalize uploaded conversation history"`.**

### Task 4: PRQC NDJSON gzip reference generation

**Files:**
- Create: `backend/src/main/java/com/relationshiptemperature/api/analysis/infrastructure/NormalizedConversationNdjsonWriter.java`
- Create: `backend/src/main/java/com/relationshiptemperature/api/analysis/infrastructure/LocalConversationReferenceProvider.java`
- Modify: `backend/src/main/java/com/relationshiptemperature/api/analysis/infrastructure/NotConfiguredConversationReferenceProvider.java`
- Modify: `backend/src/main/java/com/relationshiptemperature/api/analysis/application/ConversationReferenceProvider.java`
- Test: `backend/src/test/java/com/relationshiptemperature/api/analysis/infrastructure/NormalizedConversationNdjsonWriterTest.java`
- Test: `backend/src/test/java/com/relationshiptemperature/api/analysis/infrastructure/LocalConversationReferenceProviderTest.java`

**Interfaces:**
- Produces: `byte[] writeGzip(List<ConversationMessage> messages)` containing one JSON object per message with `messageId`, `sender`, `sentAt`, `text`.
- Produces a `ConversationReference` with `format=NORMALIZED_NDJSON_GZIP`, `formatVersion=conversation-ndjson-1.0.0`, `contentEncoding=gzip`, exact compressed byte size and SHA-256.

- [ ] **Step 1: Write failing tests that decompress the result and assert exact ordered NDJSON, JSON escaping, SELF/OTHER roles, size, and SHA-256.**
- [ ] **Step 2: Run focused tests and verify the missing writer/provider failure.**
- [ ] **Step 3: Implement deterministic UTF-8 NDJSON gzip serialization and a configured reference provider that reads normalized DB rows.** Use a controlled local download endpoint only in local/stub mode; retain the production Object Storage boundary for HTTP AI mode.
- [ ] **Step 4: Run focused and full tests.**
- [ ] **Step 5: Commit with `git commit -m "feat: build normalized prqc conversation references"`.**

### Task 5: Complete analysis Job stages, progress, retry, and result validation

**Files:**
- Modify: `backend/src/main/java/com/relationshiptemperature/api/analysis/application/AnalysisJobRunner.java`
- Modify: `backend/src/main/java/com/relationshiptemperature/api/analysis/domain/AnalysisJob.java`
- Modify: `backend/src/main/java/com/relationshiptemperature/api/analysis/web/AnalysisController.java`
- Create: `backend/src/test/java/com/relationshiptemperature/api/analysis/application/AnalysisJobRunnerTest.java`
- Create: `backend/src/test/java/com/relationshiptemperature/api/analysis/web/AnalysisControllerIntegrationTest.java`

**Interfaces:**
- Preserves `POST /api/v1/relationships/{relationshipId}/analyses` and `GET /api/v1/analysis-jobs/{jobId}`.
- Produces all five persisted stages and an estimated remaining time of `30` while queued, `null` when indeterminate/failed, and `0` when succeeded.

- [ ] **Step 1: Write failing tests for five-stage ordering, maximum 90 before AI result, 95 during report save, success/100/report ID, retry on 429/503/504, no retry on 4xx, and failure metadata.** Inject a delay strategy so tests do not sleep.
- [ ] **Step 2: Write failing endpoint tests for ownership, duplicate active Job, initial `estimatedSecondsRemaining=30`, successful response, and HTTP-200 failed Job response.**
- [ ] **Step 3: Run the focused tests and verify expected behavioral failures.**
- [ ] **Step 4: Implement the minimal stage/retry/response changes, keeping check-in out of `AiAnalysisClient.AnalysisRequest`.**
- [ ] **Step 5: Run focused and full tests.**
- [ ] **Step 6: Commit with `git commit -m "feat: complete asynchronous analysis lifecycle"`.**

### Task 6: Consultation CSV context and AI request

**Files:**
- Create: `backend/src/main/java/com/relationshiptemperature/api/consultation/application/ConsultationContext.java`
- Create: `backend/src/main/java/com/relationshiptemperature/api/consultation/infrastructure/ConsultationCsvWriter.java`
- Modify: `backend/src/main/java/com/relationshiptemperature/api/consultation/application/ChatAiClient.java`
- Modify: `backend/src/main/java/com/relationshiptemperature/api/consultation/infrastructure/StubChatAiClient.java`
- Create: `backend/src/main/java/com/relationshiptemperature/api/consultation/infrastructure/HttpChatAiClient.java`
- Modify: `backend/src/main/java/com/relationshiptemperature/api/config/AppProperties.java`
- Test: `backend/src/test/java/com/relationshiptemperature/api/consultation/infrastructure/ConsultationCsvWriterTest.java`
- Test: `backend/src/test/java/com/relationshiptemperature/api/consultation/infrastructure/HttpChatAiClientTest.java`

**Interfaces:**
- Changes AI call to `ChatAnswer answer(ChatRequest request)`.
- `ChatRequest` contains `UUID reportId`, `String selfParticipantName`, `String otherParticipantName`, `byte[] conversationCsv`, `List<ChatTurn> history`, and `String userMessage`.
- CSV output is UTF-8 with `Date,User,Message` and RFC 4180 quoting.

- [ ] **Step 1: Write failing serializer tests asserting exact headers, original participant names, chronological rows, Korean text, commas, quotes, and newlines.**
- [ ] **Step 2: Write failing HTTP client tests asserting participant metadata, report context, prior consultation turns, and CSV multipart/resource payload are sent without logging raw content.**
- [ ] **Step 3: Run focused tests and verify missing request/serializer behavior.**
- [ ] **Step 4: Implement the request model, CSV writer, stub adaptation, and property-conditional HTTP client with configured timeout/token.**
- [ ] **Step 5: Run focused and full tests.**
- [ ] **Step 6: Commit with `git commit -m "feat: send csv context to consultation ai"`.**

### Task 7: Unified imported and consultation history

**Files:**
- Modify: `backend/src/main/java/com/relationshiptemperature/api/consultation/application/ConsultationService.java`
- Modify: `backend/src/main/java/com/relationshiptemperature/api/consultation/application/ChatStreamService.java`
- Modify: `backend/src/main/java/com/relationshiptemperature/api/consultation/web/ConsultationController.java`
- Create: `backend/src/main/java/com/relationshiptemperature/api/consultation/application/ConsultationTimelineService.java`
- Create: `backend/src/test/java/com/relationshiptemperature/api/consultation/web/ConsultationControllerIntegrationTest.java`

**Interfaces:**
- Produces `TimelineMessage(id, source, role, senderName, content, status, sentAt, safetyNotice)` where source is `IMPORTED_CONVERSATION` or `CONSULTATION`.
- `GET /consultations/{id}/messages` returns imported SELF/OTHER rows followed by USER/ASSISTANT rows in chronological order.

- [ ] **Step 1: Write failing integration tests that create a relationship, normalized human messages, a report, and a consultation, then assert unified ordered history and source/role/sender fields.**
- [ ] **Step 2: Add failing service tests asserting the AI request includes imported CSV and all earlier consultation turns but does not duplicate the newly submitted message.**
- [ ] **Step 3: Run focused tests and verify history/context failures.**
- [ ] **Step 4: Implement the timeline query/DTO and update the async answer path to build `ChatRequest` from owned persisted data.**
- [ ] **Step 5: Run focused tests plus existing SSE tests and the full suite.**
- [ ] **Step 6: Commit with `git commit -m "feat: expose imported conversation in consultation history"`.**

### Task 8: Shared consultation event broadcaster and idempotent commands

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__add_chat_message_idempotency.sql`
- Modify: `backend/src/main/java/com/relationshiptemperature/api/consultation/domain/ChatMessage.java`
- Modify: `backend/src/main/java/com/relationshiptemperature/api/consultation/repository/ChatMessageRepository.java`
- Create: `backend/src/main/java/com/relationshiptemperature/api/consultation/application/ConsultationEvent.java`
- Create: `backend/src/main/java/com/relationshiptemperature/api/consultation/application/ConsultationEventBroadcaster.java`
- Modify: `backend/src/main/java/com/relationshiptemperature/api/consultation/application/ConsultationService.java`
- Modify: `backend/src/main/java/com/relationshiptemperature/api/consultation/application/ChatStreamService.java`
- Test: `backend/src/test/java/com/relationshiptemperature/api/consultation/application/ConsultationServiceTest.java`

**Interfaces:**
- Produces `send(UUID userId, UUID consultationId, String clientMessageId, String content)` shared by REST and WebSocket.
- Produces broadcaster subscription APIs for SSE and WebSocket adapters and events `message.accepted`, `assistant.started|delta|completed|failed`.

- [ ] **Step 1: Write failing tests for duplicate `clientMessageId`, one active GENERATING assistant per room, one persistence operation, and identical broadcaster events for REST/WebSocket subscribers.**
- [ ] **Step 2: Run focused tests and verify duplicate/concurrency behavior fails.**
- [ ] **Step 3: Add V6 unique `(consultation_id, client_message_id)` constraint and implement the shared transactional command plus broadcaster.** Use repository/DB guards rather than an in-memory-only lock.
- [ ] **Step 4: Refactor SSE to adapt broadcaster events while preserving documented SSE names and terminal recovery through GET.**
- [ ] **Step 5: Run focused and full tests.**
- [ ] **Step 6: Commit with `git commit -m "refactor: share consultation message events"`.**

### Task 9: Session-authenticated raw WebSocket endpoint

**Files:**
- Modify: `backend/build.gradle`
- Create: `backend/src/main/java/com/relationshiptemperature/api/config/WebSocketConfig.java`
- Create: `backend/src/main/java/com/relationshiptemperature/api/consultation/web/ConsultationWebSocketHandler.java`
- Create: `backend/src/main/java/com/relationshiptemperature/api/consultation/web/ConsultationHandshakeInterceptor.java`
- Create: `backend/src/main/java/com/relationshiptemperature/api/consultation/web/ConsultationWebSocketMessage.java`
- Modify: `backend/src/main/java/com/relationshiptemperature/api/config/SecurityConfig.java`
- Test: `backend/src/test/java/com/relationshiptemperature/api/consultation/web/ConsultationWebSocketIntegrationTest.java`

**Interfaces:**
- Adds `/ws/v1/consultations/{consultationId}` using raw JSON WebSocket.
- Accepts `{ "type":"user.message", "clientMessageId":"<uuid>", "content":"..." }` and `{ "type":"ping" }`.
- Emits `history`, `message.accepted`, `assistant.started`, `assistant.delta`, `assistant.completed`, `assistant.failed`, `pong`, and `error` envelopes.

- [ ] **Step 1: Add Spring WebSocket dependencies and write failing integration tests for unauthenticated rejection, wrong-owner rejection, allowed-origin connection, `history`, ping/pong, user message persistence, duplicate client ID, and AI completion event.**
- [ ] **Step 2: Run the focused test and verify the endpoint is unavailable.**
- [ ] **Step 3: Implement handshake authentication/ownership, JSON validation, handler session lifecycle, broadcaster subscription cleanup, and shared service invocation.**
- [ ] **Step 4: Ensure CSRF remains enforced for REST mutations while WebSocket upgrade relies on authenticated session plus strict Origin validation.**
- [ ] **Step 5: Run focused and full tests.**
- [ ] **Step 6: Commit with `git commit -m "feat: add websocket consultation transport"`.**

### Task 10: Public API and OpenAPI documentation

**Files:**
- Modify: `backend/docs/API_SPEC.md`
- Modify: `backend/docs/openapi.yaml`
- Modify if generated output requires it: `backend/docs/API_ENDPOINT_CATALOG.md`
- Modify if generator input requires it: `backend/docs/generate_api_catalog.rb`
- Test: `backend/src/test/java/com/relationshiptemperature/api/docs/OpenApiContractTest.java`

**Interfaces:**
- Documents upload `selfParticipantName`, `.csv` support, participant response fields, unified timeline message schema, REST `clientMessageId`, and WebSocket event envelopes.
- Keeps `AI_INTERNAL_API_SPEC.md` and `openapi-ai-internal.yaml` on `NORMALIZED_NDJSON_GZIP`.

- [ ] **Step 1: Write a failing OpenAPI parse/contract test asserting the changed multipart fields, message schemas, existing REST/SSE paths, and the `x-websocket` contract on `/ws/v1/consultations/{consultationId}`.**
- [ ] **Step 2: Run the contract test and verify missing schema failures.**
- [ ] **Step 3: Carefully merge documentation changes into the user's existing modified files, preserving the catalog preamble and unrelated edits.** Update examples with `강명진` only where a participant example is required; do not embed the supplied private message bodies.
- [ ] **Step 4: Run `ruby backend/docs/generate_api_catalog.rb` only if it is confirmed to be the intended generator and review its diff before retaining output.**
- [ ] **Step 5: Run the focused contract test, parse both OpenAPI YAML files, and inspect `git diff --check`.**
- [ ] **Step 6: Commit only the intended documentation changes with `git commit -m "docs: specify websocket consultation and csv uploads"`.**

### Task 11: End-to-end verification and privacy audit

**Files:**
- Modify only files required to fix failures discovered by the commands below.

**Interfaces:**
- Verifies the complete upload → check-in → async analysis → report → consultation → WebSocket flow.

- [ ] **Step 1: Run `cd backend && ./gradlew clean test` and require zero failures.**
- [ ] **Step 2: Run targeted integration tests for conversation upload, analysis Job, consultation REST/SSE, and WebSocket once more to capture focused evidence.**
- [ ] **Step 3: Run `git diff --check` and search production code/log statements for raw message, CSV payload, storage key, token, or URL logging.**
- [ ] **Step 4: Inspect `git status --short` and ensure pre-existing unrelated user files are not accidentally staged or overwritten.**
- [ ] **Step 5: Commit any verification-only corrections with `git commit -m "test: verify async analysis and websocket consultation"`.**

