# 비동기 관계 분석 및 WebSocket 상담 설계

## 목표

카카오톡 `.txt` 및 `Date,User,Message` CSV 대화를 관계별 공통 메시지로 보존하고, 이를 기반으로 비동기 PRQC 분석과 AI 상담을 제공한다. 기존 REST/SSE 상담 계약은 유지하면서 세션 인증 기반 WebSocket을 추가한다.

## 범위와 계약

- 공개 API의 기준은 `backend/docs/API_SPEC.md`이며 공개 OpenAPI 계약은 `backend/docs/openapi.yaml`에 반영한다.
- PRQC 분석 AI에는 기존 내부 계약대로 `NORMALIZED_NDJSON_GZIP`을 전달한다.
- 상담 AI에는 `Date,User,Message` CSV와 `selfParticipantName`, 상대 참여자 이름, 최신 리포트 및 해당 상담의 메시지 이력을 전달한다.
- 체크인은 분석 Job 및 리포트와 연결하지만 PRQC 또는 종합점수 입력으로 사용하지 않는다.
- 기존 REST 메시지 전송과 SSE 스트림은 유지하고 WebSocket은 추가 전송 수단으로 제공한다.

## 대화 수집과 정규화

업로드 API는 `.txt`와 `.csv`를 허용하며 `selfParticipantName`을 필수 입력으로 받는다. CSV는 UTF-8 또는 UTF-8 BOM, RFC 4180 인용 규칙, `Date,User,Message` 헤더, `yyyy-MM-dd HH:mm:ss` 날짜를 지원한다. TXT 파서는 카카오 내보내기 날짜 구분선과 메시지 행을 해석한다.

두 입력은 다음 공통 메시지로 변환한다.

- 원본 파일 및 관계 ID
- 파일 안에서 안정적인 순번
- 발신 시각
- 발신자 표시 이름
- 참여자 구분 `SELF` 또는 `OTHER`
- 메시지 본문

파일의 고유 발신자는 정확히 두 명이어야 하고 `selfParticipantName`이 그중 하나여야 한다. 나머지 한 명을 상대 참여자로 결정한다. 제공된 샘플에서는 `강명진`이 `SELF`, `이진우`가 `OTHER`다. 파싱된 메시지는 원본 파일과 별도 테이블에 저장하여 원본 보존 기간이 끝난 뒤에도 리포트와 상담 이력을 복구할 수 있게 한다.

중복 업로드는 관계 ID와 파일 SHA-256을 기준으로 거부한다. CSV 필드 안의 쉼표, 큰따옴표 및 줄바꿈은 CSV 라이브러리로 처리하고 수동 문자열 분할은 사용하지 않는다.

## 비동기 분석 파이프라인

`POST /api/v1/relationships/{relationshipId}/analyses`는 소유권, 유효한 대화 파일, 체크인, 활성 Job 부재를 검증한 뒤 Job을 `QUEUED`로 저장하고 `202 Accepted`를 반환한다. 커밋 후 전용 실행기에서 Worker를 시작한다.

Worker는 다음 상태를 영속화한다.

1. `LOADING_CONVERSATION`: 정규화 메시지 조회 및 NDJSON gzip 준비
2. `ANALYZING_MESSAGE_PATTERNS`: AI 분석 호출 시작
3. `ANALYZING_EMOTIONAL_FLOW`: AI 응답 대기 중 표시 단계
4. `CALCULATING_PRQC`: AI 결과 검증
5. `CALCULATING_RELATIONSHIP_SCORE`: 관계 유형별 가중치 적용 및 리포트 저장

AI 결과 전에는 진행률이 90을 넘지 않고, 리포트 저장 전에는 95를 넘지 않는다. Job과 리포트가 모두 완료된 경우에만 `SUCCEEDED/100`이 된다. 실패는 Job에 공개 가능한 코드, 메시지, 재시도 가능 여부를 저장하며 Job 조회는 HTTP 200 응답 본문으로 실패 상태를 알린다. AI의 429, 503, 504는 2초와 5초 지연으로 최대 세 번 시도한다.

PRQC AI 입력은 저장된 공통 메시지를 `sender=SELF|OTHER`, ISO 8601 `sentAt`, `text`를 갖는 NDJSON으로 직렬화한 뒤 gzip으로 압축한다. 백엔드가 관계 유형별 버전 가중치로 `overall.score`를 계산하고 리포트 및 근거를 저장한다.

## 상담 이력과 AI 컨텍스트

상담방은 최신 완료 리포트와 관계를 연결한다. 메시지 조회는 관계의 최신 유효 대화 파일에 속한 과거 `SELF/OTHER` 메시지와 상담방의 `USER/ASSISTANT` 메시지를 시간순으로 합친다. 응답에는 메시지 출처를 구별할 수 있도록 `source=IMPORTED_CONVERSATION|CONSULTATION`과 표시 역할을 포함한다.

상담 AI 요청을 만들 때 공통 메시지를 다음 헤더의 UTF-8 CSV로 직렬화한다.

```csv
Date,User,Message
```

날짜는 `yyyy-MM-dd HH:mm:ss`, 사용자 이름은 원래 표시 이름을 사용한다. 요청 메타데이터에는 `selfParticipantName`, `otherParticipantName`, 리포트 ID와 점수/근거, 현재 상담방의 이전 사용자·AI 메시지, 새 사용자 메시지를 포함한다. CSV 원문이나 메시지 본문은 로그에 남기지 않는다.

REST와 WebSocket은 하나의 상담 메시지 처리 서비스를 공유한다. 이 서비스는 사용자 메시지와 `GENERATING` 상태의 AI 메시지를 같은 트랜잭션에서 저장하고, 커밋 후 AI 생성을 시작한다. 동일 상담방에는 동시에 하나의 AI 응답만 허용한다.

## WebSocket 계약

WebSocket 경로는 `/ws/v1/consultations/{consultationId}`다. HTTP 업그레이드 시 기존 `rt_session` 쿠키로 인증하고 상담방 소유권을 검증한다. 허용된 프론트엔드 Origin만 연결할 수 있다.

클라이언트 이벤트:

- `user.message`: `clientMessageId`와 1~4000자의 `content`
- `ping`: 연결 상태 확인

서버 이벤트:

- `history`: 연결 시 과거 사람 간 대화와 상담 메시지 목록
- `message.accepted`: 영속화된 사용자 메시지와 생성 중 AI 메시지
- `assistant.started`: AI 생성 시작
- `assistant.delta`: 생성된 텍스트 조각
- `assistant.completed`: 영속화된 최종 AI 메시지
- `assistant.failed`: 실패 코드와 재시도 가능 여부
- `pong`: ping 응답
- `error`: 검증, 인증, 충돌 오류

`clientMessageId`는 상담방 안에서 멱등 키로 사용하여 재연결 후 같은 메시지가 중복 저장되지 않게 한다. WebSocket이 끊겨도 `GET /consultations/{consultationId}/messages`로 최종 이력을 복구할 수 있다. SSE 구독자와 WebSocket 세션은 동일한 AI 생성 이벤트를 받아 두 전송 방식의 결과가 일치한다.

## 데이터 변경

- 정규화된 업로드 메시지와 참여자 정보를 저장할 테이블을 추가한다.
- `conversation_files`에 본인 및 상대 참여자 이름을 저장한다.
- 상담 메시지에 선택적인 `client_message_id`를 추가하고 상담방 내 고유 제약을 둔다.
- 원본 파일 삭제 작업은 정규화 메시지를 삭제하지 않는다. 관계 또는 대화 파일을 명시적으로 삭제할 때는 관련 정규화 메시지를 함께 삭제한다.

## 오류와 보안

- 잘못된 CSV/TXT, 2인 초과 대화, 본인 이름 불일치, 빈 메시지는 4xx 오류로 반환한다.
- 인증되지 않은 WebSocket 업그레이드와 타인 상담방 접근을 거부한다.
- 파일 경로, Presigned URL, 원문, AI 공급자 내부 오류를 공개 응답이나 로그에 포함하지 않는다.
- WebSocket과 REST 모두 동일한 길이 제한, 동시 생성 제한, 사용자별 호출 제한 지점을 공유한다.
- 상담 AI 실패 시 사용자 메시지는 유지하고 AI 메시지만 `FAILED`로 전환한다.

## 문서 변경

`backend/docs/API_SPEC.md`에는 CSV 업로드 필드, 통합 상담 이력, WebSocket 경로와 이벤트 계약을 추가한다. `backend/docs/openapi.yaml`에는 HTTP 업로드·상담 응답 스키마 변경을 반영한다. OpenAPI는 WebSocket 프레임을 완전히 표현하지 못하므로 업그레이드 경로와 이벤트 스키마를 설명 및 확장 필드로 문서화한다. PRQC 내부 계약은 NDJSON gzip을 유지한다.

## 검증 전략

- CSV BOM, 인용 쉼표, 큰따옴표, 줄바꿈, 시간 및 SELF/OTHER 매핑 단위 테스트
- 카카오 TXT 파싱과 공통 메시지 변환 테스트
- 공통 메시지의 PRQC NDJSON gzip 및 상담 CSV 직렬화 분리 테스트
- 분석 Job 정상 완료, 단계 전이, 재시도, 실패, 중복 실행 테스트
- 종합점수 정책 및 리포트 저장 통합 테스트
- 통합 상담 이력 정렬 및 AI 컨텍스트 구성 테스트
- REST/SSE 회귀 테스트와 WebSocket 인증·소유권·멱등성·영속화·이벤트 통합 테스트
- 전체 Gradle 테스트와 OpenAPI 구문 검증
