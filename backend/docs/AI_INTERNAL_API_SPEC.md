# 관계온도 백엔드-AI 내부 API 명세

> 버전: v1.0 (MVP 합의안)  
> 기준일: 2026-08-19  
> Base URL: `/internal/v1`  
> 기계 판독용 계약: [`openapi-ai-internal.yaml`](./openapi-ai-internal.yaml)

## 1. 목적과 범위

이 문서는 관계온도 백엔드 Worker가 AI 서버에 대화 분석을 요청하는 내부 계약을 정의한다. 프론트엔드는 이 API를 직접 호출하지 않는다.

MVP 내부 API는 PRQC 분석과 AI 상담 답변 엔드포인트를 제공한다.

```http
POST /internal/v1/prqc-analyses
POST /internal/v1/consultation-answers
```

AI 서버의 책임은 다음으로 제한한다.

- 정규화된 1:1 대화 데이터 다운로드 또는 수신
- 대화 데이터 검증
- PRQC 6개 구성요소 점수 산출
- 구성요소별 관찰 근거와 정량 지표 생성
- 모델 및 프롬프트 버전 반환
- 백엔드가 전달한 관계 리포트와 최근 상담 이력을 근거로 상담 답변 생성
- 답변에 사용한 근거 참조와 안전 제안을 구조화해 반환

AI 서버는 다음을 담당하지 않는다.

- 프론트엔드용 AnalysisJob 생성·진행률 관리
- 웹훅 또는 콜백 전송
- canonical `overall.score` 계산
- 관계 상태 라벨 결정
- 공개 리포트 저장

## 2. 호출 구조

```text
Frontend
  └─ 공개 분석 API 호출
       ↓
Backend API
  ├─ AnalysisJob 생성
  └─ Queue 등록 후 202 반환
       ↓
Backend Worker
  ├─ 카카오 원문 검증·파싱
  ├─ 정규화 NDJSON gzip 생성
  ├─ 내부 AI API 동기 호출
  ├─ 관계 유형별 가중치로 overall.score 계산
  └─ 리포트 및 Job 저장
       ↓
AI Server
  └─ PRQC 6요소와 근거를 한 번에 반환
```

AI 호출의 권장 타임아웃은 60~120초다. 현재 예상 처리 시간은 20~30초다. AI 서버는 단계별 진행률을 반환하지 않으며 백엔드는 호출 대기 중 공개 Job의 예상 진행률을 최대 90으로 제한한다.

## 3. 인증과 공통 헤더

Private subnet에서도 서비스 인증을 적용한다.

```http
Authorization: Bearer {service-token}
X-Request-Id: req_01J5P8K9W8G0H7P9T0W1K2J3M4
Idempotency-Key: 0198c8a7-3000-7000-8000-000000000001
```

| 헤더 | 필수 | 설명 |
|---|---|---|
| `Authorization` | Y | AI 내부 서비스 전용 Bearer 토큰 |
| `X-Request-Id` | Y | 분산 추적 ID |
| `Idempotency-Key` | Y | 공개 AnalysisJob ID와 동일한 UUID |

같은 `Idempotency-Key`와 같은 요청 본문은 24시간 동안 동일한 성공 결과를 반환한다. 같은 키로 다른 요청 본문을 보내면 `409 IDEMPOTENCY_KEY_REUSED`를 반환한다.

AI 서버에는 사용자 세션 쿠키, CSRF 토큰, 카카오 OAuth 토큰을 전달하지 않는다.

## 4. 대화 데이터 전달 방식

### 4.1 권장 방식: Object Storage 참조

백엔드는 카카오톡 `.txt` 원문을 직접 전달하지 않고 다음 순서로 처리한다.

1. 원본 크기, MIME, 인코딩, 카카오 내보내기 형식을 검증한다.
2. 1:1 메시지를 정규화 NDJSON으로 변환한다.
3. 정규화 결과를 gzip으로 압축한다.
4. 암호화된 Object Storage에 저장한다.
5. AI 서버에 다운로드 전용 Presigned URL을 전달한다.

정규화 NDJSON 예시:

```json
{"messageId":"m1","sender":"SELF","sentAt":"2026-08-17T10:20:00+09:00","text":"오늘 저녁에 시간 괜찮아?"}
{"messageId":"m2","sender":"OTHER","sentAt":"2026-08-17T12:04:00+09:00","text":"조금 늦게 끝날 것 같아"}
```

Presigned URL 규칙:

- 다운로드 전용
- 만료시간 5~15분
- 사용자 이름과 원본 파일명을 URL에 포함하지 않음
- AI 서버가 `sha256`과 `sizeBytes`를 검증
- 리다이렉트는 기본적으로 허용하지 않거나 allowlist 호스트로만 제한
- URL과 다운로드 자격증명을 로그에 기록하지 않음

### 4.2 대안 방식: multipart 스트리밍

Object Storage가 없는 MVP 환경에서는 같은 엔드포인트에 `multipart/form-data`로 `conversation.ndjson.gz`를 전송할 수 있다.

50MB급 원문 또는 정규화 대화를 JSON 문자열이나 Base64로 포함하지 않는다. 백엔드 로컬 파일 경로도 다른 컨테이너·호스트에서 유효하지 않으므로 전달하지 않는다.

## 5. PRQC 분석

### 5.1 Object Storage 참조 요청

`POST /internal/v1/prqc-analyses`

```json
{
  "analysisId": "0198c8a7-3000-7000-8000-000000000001",
  "relationshipType": "FRIEND",
  "conversation": {
    "url": "https://storage.example.com/private/conversation?...",
    "format": "NORMALIZED_NDJSON_GZIP",
    "formatVersion": "conversation-ndjson-1.0.0",
    "contentEncoding": "gzip",
    "sizeBytes": 4821941,
    "sha256": "a878d8f81f41f32c7d1a4748f35e92318f367689632be2f3c9d662e705c4ec9d"
  }
}
```

요청 규칙:

- `analysisId`는 `Idempotency-Key`와 같아야 한다.
- `relationshipType`은 공개 API enum과 동일하다.
- `conversation.url`은 HTTPS만 허용한다.
- AI 서버는 URL 다운로드 전에 호스트 allowlist 또는 서명된 스토리지 도메인을 검증한다.
- 다운로드 데이터의 크기와 SHA-256이 요청값과 일치해야 한다.
- 위 JSON 요청에도 `context`로 사용자·관계·체크인 정보를 함께 포함한다.

### 5.2 분석 컨텍스트

AI는 대화 파일만으로 단정하지 않고 관계의 변화를 해석할 수 있도록, 백엔드는 현재
분석 스냅샷과 이전 분석 이력을 함께 전달한다. `history`는 **이전 체크인 입력 시각
(`inputAt`) 오름차순**이며, 현재 분석보다 이전 주차의 성공한 분석만 포함한다.

```json
{
  "user": {
    "userId": "0198c8a7-3000-7000-8000-000000000002",
    "displayName": "우",
    "timezone": "Asia/Seoul"
  },
  "relationship": {
    "relationshipId": "0198c8a7-3000-7000-8000-000000000003",
    "name": "민지",
    "relationshipType": "FRIEND",
    "status": "ANALYZING"
  },
  "current": {
    "conversationFileId": "0198c8a7-3000-7000-8000-000000000004",
    "checkIn": {
      "checkInId": "0198c8a7-3000-7000-8000-000000000005",
      "weekStart": "2026-08-17",
      "inputAt": "2026-08-17T01:00:00Z",
      "answers": [
        { "questionCode": "RELATIONSHIP_FEELING", "score": 6 },
        { "questionCode": "CONVERSATION_COMFORT", "score": 4 }
      ]
    }
  },
  "history": [
    {
      "inputAt": "2026-08-10T01:00:00Z",
      "conversation": {
        "conversationFileId": "0198c8a7-3000-7000-8000-000000000006",
        "messages": [
          { "sender": "SELF", "sentAt": "2026-08-09T12:00:00Z", "text": "지난 대화 내용" }
        ]
      },
      "checkIn": {
        "checkInId": "0198c8a7-3000-7000-8000-000000000007",
        "weekStart": "2026-08-10",
        "inputAt": "2026-08-10T01:00:00Z",
        "answers": [{ "questionCode": "RELATIONSHIP_FEELING", "score": 4 }]
      },
      "analysis": {
        "reportId": "0198c8a7-3000-7000-8000-000000000008",
        "analyzedAt": "2026-08-10T01:02:00Z",
        "overallScore": 58,
        "scoreChange": -7,
        "prqc": { "satisfaction": 55, "commitment": 56, "intimacy": 57, "trust": 58, "passion": 59, "love": 60 },
        "evidences": [{ "component": "trust", "score": 58, "summary": "응답 간격이 길어졌어요.", "metric": null }]
      }
    }
  ]
}
```

- 현재 대화의 전체 내용은 중복하지 않고 multipart의 `file` (`conversation.ndjson.gz`)에 담는다. `current.conversationFileId`는 이 파일의 식별자다.
- `history[].conversation.messages`에는 해당 이전 분석에 사용된 정규화 대화(발신자 역할, 발신 시각, 본문)가 담긴다.
- `history[].analysis.evidences[].summary`가 현재 저장돼 있는 이전 AI 분석 글이다. 별도의 리포트 서술문은 아직 저장하지 않는다.
- `answers.score`는 1~7 정수이며, 현재 제공 질문은 `RELATIONSHIP_FEELING`, `CONVERSATION_COMFORT`다.
- AI는 이 정보를 PRQC와 관찰 근거를 해석하는 보조 맥락으로만 사용한다. canonical `overall.score`는 여전히 백엔드가 계산한다.
- 카카오 식별자, OAuth/세션 토큰, 프로필 이미지 URL은 전달하지 않는다.

### 5.3 multipart 요청

```text
analysisId: 0198c8a7-3000-7000-8000-000000000001
relationshipType: FRIEND
format: NORMALIZED_NDJSON_GZIP
formatVersion: conversation-ndjson-1.0.0
sha256: a878d8f81f41f32c7d1a4748f35e92318f367689632be2f3c9d662e705c4ec9d
context: {"user":{...},"relationship":{...},"current":{"conversationFileId":"...","checkIn":{...}},"history":[...]}
file: conversation.ndjson.gz
```

`context` 파트의 Content-Type은 `application/json`이다. 따라서 전체 요청은 JSON이 아니라 **정규화 대화 파일 + JSON 컨텍스트로 구성된 multipart/form-data**다.

### 5.4 성공 응답

응답: `200 OK`

```json
{
  "analysisId": "0198c8a7-3000-7000-8000-000000000001",
  "modelVersion": "prqc-2026-08-19.1",
  "promptVersion": "relationship-evidence-1.0.0",
  "processedMessageCount": 8421,
  "components": {
    "satisfaction": 55,
    "commitment": 45,
    "intimacy": 68,
    "trust": 72,
    "passion": 40,
    "love": 58
  },
  "evidences": [
    {
      "component": "passion",
      "score": 40,
      "summary": "최근 한 달간 대화 빈도가 주 평균 3.2회에서 1.1회로 줄어든 것이 관찰됐어요.",
      "metric": {
        "name": "weeklyConversationCount",
        "currentValue": 1.1,
        "previousValue": 3.2,
        "unit": "회/주",
        "period": "최근 4주와 이전 4주 비교"
      }
    }
  ],
  "warnings": [],
  "selfReportComparison": "사용자는 체크인에서 대화를 편안하게 느낀다고 답했지만, 최근 대화에서는 한쪽의 만남 제안이 줄어든 신호가 함께 관찰됐어요.",
  "completedAt": "2026-08-19T06:22:24Z"
}
```

응답 규칙:

- PRQC 6개 필드는 모두 필수이며 `0~100` 정수다.
- 각 근거의 `score`는 해당 `component` 점수와 일치해야 한다.
- `selfReportComparison`은 체크인 자기보고와 대화에서 관찰된 신호의 일치·차이를 비진단적이고 단정하지 않는 표현으로 설명한다.
- `summary`는 확정적 인과관계나 진단이 아니라 관찰된 패턴을 완곡하게 설명한다.
- 원문 메시지 전체나 민감한 제3자 정보를 응답에 포함하지 않는다.
- AI 서버는 canonical `overall.score`를 반환하지 않는다.
- AI 평가용 종합점수가 필요하면 운영 응답이 아닌 별도 평가 파이프라인에서 관리한다.

## 6. 백엔드 종합점수 계산

AI 응답을 받은 백엔드는 PRQC 6개 구성요소에 관계 유형별 가중치를 적용해 canonical `overall.score`를 계산한다.

```text
overall.score = round(
  satisfaction × weight.satisfaction +
  commitment × weight.commitment +
  intimacy × weight.intimacy +
  trust × weight.trust +
  passion × weight.passion +
  love × weight.love
)
```

- 관계 유형별 가중치 합은 1이어야 한다.
- 점수는 `0~100` 정수로 제한한다.
- 백엔드는 사용한 정책 버전을 `scoringPolicyVersion`으로 리포트에 저장한다.
- AI `modelVersion`과 백엔드 `scoringPolicyVersion`은 독립적으로 관리한다.
- 체크인 응답은 AI의 PRQC 해석에 전달되지만, MVP에서는 위 canonical 산식에 직접 포함하지 않는다.

권장 버전 형식:

```text
modelVersion: prqc-YYYY-MM-DD.N
promptVersion: relationship-evidence-MAJOR.MINOR.PATCH
scoringPolicyVersion: relationship-temperature-MAJOR.MINOR.PATCH
```

## 7. 오류 계약

```json
{
  "error": {
    "code": "AI_PROVIDER_UNAVAILABLE",
    "message": "일시적으로 분석 모델을 호출할 수 없습니다.",
    "retryable": true,
    "requestId": "req_01J5P8K9W8G0H7P9T0W1K2J3M4",
    "details": null
  }
}
```

| HTTP | 대표 코드 | 의미 | 백엔드 재시도 |
|---:|---|---|---|
| 400 | `INVALID_REQUEST` | 필수 필드, 해시, 형식값 오류 | X |
| 401 | `AUTH_REQUIRED` | 서비스 토큰 없음 | X, 운영 알림 |
| 403 | `FORBIDDEN` | 서비스 토큰 권한 없음 | X, 운영 알림 |
| 404 | `CONVERSATION_NOT_ACCESSIBLE` | Presigned URL 조회 실패 | 조건부 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 같은 키에 다른 요청 | X |
| 422 | `INSUFFICIENT_MESSAGES` | 분석 가능한 메시지 부족 | X |
| 422 | `INVALID_CONVERSATION_DATA` | 정규화 데이터 파싱 불가 | X |
| 429 | `AI_RATE_LIMITED` | 내부/외부 모델 호출 제한 | O |
| 500 | `AI_INTERNAL_ERROR` | 예상하지 못한 오류 | 조건부 |
| 503 | `AI_PROVIDER_UNAVAILABLE` | 모델 공급자 일시 장애 | O |
| 504 | `AI_TIMEOUT` | 분석 시간 초과 | O |

백엔드 Worker 재시도 권장값:

```text
1차 실패 → 약 2초 후 재시도
2차 실패 → 약 5초 후 재시도
3차 실패 → 공개 AnalysisJob을 FAILED로 변경
```

Jitter를 적용하고 `Retry-After`가 있으면 해당 값을 우선한다. 동일 `analysisId`와 `Idempotency-Key`를 유지한다.

AI 내부 오류가 최종 실패하면 백엔드는 민감한 내부 메시지를 제거하고 공개 Job 오류로 매핑한다.

```json
{
  "status": "FAILED",
  "failure": {
    "code": "ANALYSIS_UNAVAILABLE",
    "message": "일시적으로 분석할 수 없어요. 잠시 후 다시 시도해 주세요.",
    "retryable": true
  }
}
```

## 8. 로깅과 개인정보

- 로그에는 `analysisId`, `requestId`, 상태코드, 처리시간, 메시지 개수, 모델 버전만 기록한다.
- Presigned URL, 서비스 토큰, 원문 메시지, 메시지 샘플은 기록하지 않는다.
- 오류 추적 도구에 요청 본문이나 다운로드 데이터를 자동 첨부하지 않는다.
- 임시 다운로드 파일은 분석 완료 또는 실패 직후 삭제한다.
- AI 공급자에 대화 데이터를 전달한다면 저장·학습 사용 여부와 보존정책을 별도로 확인한다.
- 삭제 요청 시 원본, 정규화 파일, 임베딩, 캐시, 모델 중간 산출물에 삭제를 전파한다.

## 9. 완료 조건

AI 내부 API는 다음 조건을 만족하면 MVP 완료로 본다.

1. JSON Object Storage 참조와 multipart 중 최소 한 가지 입력 방식 지원
2. PRQC 6요소의 `0~100` 점수 반환
3. 최소 1개 이상의 구조화 근거 또는 근거가 없는 이유를 warning으로 반환
4. `modelVersion`, `processedMessageCount`, `completedAt` 반환
5. 동일 멱등성 키 재요청 시 중복 분석 방지
6. 429/503/504의 `retryable=true` 오류 계약 준수
7. 원문·URL·토큰 비로깅 검증
8. 50MB 업로드에서 생성될 수 있는 정규화 데이터 처리 성능 검증

## 10. AI 상담 답변

`POST /internal/v1/consultation-answers`

백엔드는 PostgreSQL의 관계 리포트·관찰 근거와 MongoDB의 최근 완료 메시지 최대 20개를 조합해 AI 서버에 전달한다. 카카오 원문, 사용자 세션, OAuth 토큰은 전달하지 않는다.

요청 예시:

```json
{
  "reportId": "0198c8a7-4000-7000-8000-000000000001",
  "overallScore": 68,
  "scoreChange": -4,
  "prqc": {
    "satisfaction": 65,
    "commitment": 70,
    "intimacy": 72,
    "trust": 60,
    "passion": 66,
    "love": 75
  },
  "evidences": [
    {
      "evidenceId": "0198c8a7-4100-7000-8000-000000000001",
      "component": "trust",
      "score": 60,
      "summary": "최근 응답 간격이 이전 기간보다 길어진 패턴이 관찰됐어요."
    }
  ],
  "recentMessages": [
    {"role": "USER", "content": "답장이 늦으면 불안해요."},
    {"role": "ASSISTANT", "content": "어떤 순간에 불안이 커지는지 살펴볼까요?"}
  ],
  "userMessage": "제가 먼저 연락해도 괜찮을까요?"
}
```

성공 응답:

```json
{
  "content": "먼저 연락하는 행동 자체보다 원하는 바를 부담 없이 표현하는 방식이 중요할 수 있어요.",
  "evidenceRefs": [
    {
      "evidenceId": "0198c8a7-4100-7000-8000-000000000001",
      "label": "최근 응답 간격 변화"
    }
  ],
  "safetyNotice": {
    "type": "SUPPORT_RECOMMENDATION",
    "title": "마음을 돌보는 제안",
    "message": "불안이 일상에 지속적인 영향을 준다면 전문가와 이야기해 보세요.",
    "resourceQuery": {
      "category": "MENTAL_HEALTH_COUNSELING",
      "region": "KR"
    }
  }
}
```

응답 규칙:

- `content`는 비어 있을 수 없으며 공개 API의 최대 메시지 길이인 20,000자를 넘지 않는다.
- `evidenceRefs[].evidenceId`는 요청에 포함된 근거 ID만 참조한다.
- 관계를 진단하거나 상대방의 의도를 단정하지 않는다.
- 자해·폭력·학대 등 위험 신호가 있으면 `CRISIS_SUPPORT` 안전 제안을 반환한다.
- 위험 신호가 없으면 `safetyNotice`는 `null`일 수 있다.
- 백엔드는 최종 응답을 MongoDB에 저장하고 클라이언트에는 `assistant.delta`와 `assistant.completed` SSE 이벤트로 전달한다.
