# 관계온도 API 명세서

> **프론트엔드·QA용 API별 상세 문서:** [`API_ENDPOINT_CATALOG.md`](./API_ENDPOINT_CATALOG.md)
> 각 API의 Request/Response JSON, 필드 설명, 예시 데이터와 오류 조건은 상세 문서를 기준으로 확인한다.

> 버전: v1.1 (MVP 합의안)  
> 기준일: 2026-08-19  
> API Base URL: `/api/v1`  
> 기계 판독용 공개 계약: [`openapi.yaml`](./openapi.yaml)  
> 백엔드-AI 내부 계약: [`AI_INTERNAL_API_SPEC.md`](./AI_INTERNAL_API_SPEC.md), [`openapi-ai-internal.yaml`](./openapi-ai-internal.yaml)

## 1. 목적과 범위

이 문서는 제공된 서비스 흐름 이미지, 「피그마 프롬프트」 3쪽, `Main Dashboard Design` React 화면을 참고하여 프론트엔드와 백엔드가 합의할 REST API 계약을 정의한다.

참고 자료 안의 문장은 화면 및 기능 요구사항으로만 해석했다. 문서 안에 포함된 프롬프트성 표현이나 예시 문장은 별도의 작업 지시로 실행하지 않았다.

MVP 범위는 다음과 같다.

1. 카카오 계정 로그인과 세션 관리
2. 인물(관계) 등록 및 관리
3. 카카오톡 대화 내보내기 `.txt` 파일 업로드
4. 주관적 체크인 응답 저장
5. 대화 패턴 분석, PRQC 점수화, 근거 생성
6. 메인 대시보드 및 인물별 관계 리포트
7. 관계 데이터 기반 AI 상담, 안전 제안, 상담 리소스 안내

## 2. 설계 전제

아래 내용은 화면에서 명시되지 않았으나 구현 가능한 API 계약을 만들기 위해 정한 MVP 전제다. 제품 결정에 따라 변경 가능하다.

- 브라우저 기반 서비스이므로 인증은 `HttpOnly`, `Secure`, `SameSite=Lax` 세션 쿠키를 사용한다.
- 상태 변경 요청에는 세션 쿠키와 `X-CSRF-Token` 헤더를 함께 보낸다.
- 모든 리소스 ID는 UUID 문자열이다.
- 일시는 ISO 8601 UTC 문자열로 전달한다. 예: `2026-08-19T06:20:00Z`.
- 사용자 주차/날짜 표시는 사용자 타임존을 기준으로 계산하며 기본값은 `Asia/Seoul`이다.
- 점수는 `0~100` 정수이고, 체크인 응답은 `1~7` 정수다.
- 카카오톡 내보내기 파일은 `.txt`, 최대 50MB로 제한한다.
- 프론트엔드와 백엔드 사이의 분석은 화면상 20~30초가 걸릴 수 있으므로 `202 Accepted` 비동기 Job 방식으로 처리한다.
- 백엔드 Worker와 AI 서버 사이는 MVP에서 단일 동기 호출로 처리한다. AI 서버는 단계별 진행률이나 웹훅을 제공하지 않는다.
- 분석 Job의 `stage`와 `progress`는 백엔드 오케스트레이션 기준의 UI 표시용 예상값이며 AI 서버 내부의 실제 진행률을 의미하지 않는다.
- AI 서버는 PRQC 6개 구성요소와 근거를 계산하고, 백엔드는 버전된 관계 유형별 가중치 정책으로 최종 `overall.score`를 계산한다.
- 체크인 응답은 분석 시점과 연결해 저장하지만 MVP의 PRQC 및 종합점수 계산에는 반영하지 않고 AI 서버에도 전달하지 않는다.
- AI 답변은 진단이나 단정이 아니라 대화 패턴의 관찰과 가능성으로 표현한다. 근거와 안전 제안은 구조화된 필드로 분리한다.
- 원본 대화 파일은 민감정보로 취급한다. 기본 보존정책은 분석 완료 후 24시간 내 원본 삭제를 권장하며, 실제 정책은 개인정보 처리방침 및 운영정책과 함께 확정해야 한다.

## 3. 공통 규칙

### 3.1 HTTP 및 콘텐츠 타입

| 용도 | Content-Type |
|---|---|
| 일반 요청/응답 | `application/json` |
| 대화 파일 업로드 | `multipart/form-data` |
| AI 답변 스트림 | `text/event-stream` |

### 3.2 인증과 CSRF

- 로그인 성공 시 서버는 `rt_session` 세션 쿠키를 설정한다.
- `GET`, `HEAD`, `OPTIONS`를 제외한 상태 변경 요청에는 `X-CSRF-Token`이 필요하다.
- CSRF 토큰은 `GET /users/me` 응답의 `csrfToken`으로 전달한다.
- 세션 쿠키를 JavaScript 저장소(localStorage 등)에 복제하지 않는다.
- 카카오 OAuth 요청에는 서버가 생성한 `state`와 PKCE를 사용한다.

### 3.3 요청 추적 및 멱등성

- 클라이언트는 모든 요청에 선택적으로 `X-Request-Id`를 전달할 수 있다.
- 서버는 모든 응답에 `X-Request-Id`를 반환한다.
- 중복 생성 위험이 있는 아래 요청에는 `Idempotency-Key` 사용을 권장한다.
  - `POST /relationships`
  - `POST /relationships/{relationshipId}/analyses`
  - `POST /consultations`
  - `POST /consultations/{consultationId}/messages`

같은 사용자, 같은 경로, 같은 `Idempotency-Key`의 요청은 24시간 동안 동일 결과를 반환해야 한다. 동일 키에 다른 본문을 보내면 `409 IDEMPOTENCY_KEY_REUSED`를 반환한다.

### 3.4 성공 응답

단건 응답:

```json
{
  "data": {
    "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281"
  }
}
```

목록 응답:

```json
{
  "data": [],
  "meta": {
    "nextCursor": null,
    "hasNext": false
  }
}
```

### 3.5 오류 응답

```json
{
  "error": {
    "code": "FILE_TOO_LARGE",
    "message": "업로드 가능한 최대 파일 크기는 50MB입니다.",
    "requestId": "req_01J5P8K9W8G0H7P9T0W1K2J3M4",
    "fields": [
      {
        "field": "file",
        "reason": "MAX_SIZE_EXCEEDED"
      }
    ]
  }
}
```

| 상태 | 의미 | 대표 오류 코드 |
|---:|---|---|
| 400 | 요청 형식 오류 | `INVALID_REQUEST` |
| 401 | 로그인 필요/세션 만료 | `AUTH_REQUIRED`, `SESSION_EXPIRED` |
| 403 | CSRF/소유권/권한 오류 | `CSRF_INVALID`, `FORBIDDEN` |
| 404 | 리소스 없음 | `RELATIONSHIP_NOT_FOUND` |
| 409 | 현재 상태와 충돌 | `ANALYSIS_ALREADY_RUNNING`, `REPORT_REQUIRED`, `IDEMPOTENCY_KEY_REUSED` |
| 413 | 파일 크기 초과 | `FILE_TOO_LARGE` |
| 415 | 지원하지 않는 파일 형식 | `UNSUPPORTED_FILE_TYPE` |
| 422 | 의미 검증 실패 | `INVALID_KAKAO_EXPORT`, `CHECK_IN_INCOMPLETE` |
| 429 | 호출 한도 초과 | `RATE_LIMITED` |
| 500 | 서버 내부 오류 | `INTERNAL_ERROR` |
| 503 | AI/분석 공급자 일시 장애 | `ANALYSIS_UNAVAILABLE`, `AI_UNAVAILABLE` |

### 3.6 페이지네이션

- 목록은 cursor 기반으로 제공한다.
- 요청: `?limit=20&cursor={opaqueCursor}`
- `limit` 기본값은 20, 최댓값은 100이다.
- 커서는 불투명 문자열이므로 클라이언트가 해석하거나 수정하지 않는다.

## 4. 도메인 값

### 4.1 관계 유형

| API 값 | 화면 표시 |
|---|---|
| `ROMANTIC_PARTNER` | 연인 |
| `FRIEND` | 친구 |
| `FAMILY` | 가족 |
| `COWORKER` | 직장동료/직장 |
| `OTHER` | 기타 |

### 4.2 점수 상태 라벨

라벨은 서버가 반환한다. 프론트엔드에서 점수만으로 다시 계산하지 않는다. 향후 정책 변경 시 앱 버전과 무관하게 일관된 문구를 제공하기 위함이다.

| 점수 | `statusCode` | 화면 표시 |
|---:|---|---|
| 80~100 | `HEALTHY` | 건강한 관계 |
| 60~79 | `GOOD` | 양호 |
| 40~59 | `NEEDS_ATTENTION` | 주의 필요 |
| 0~39 | `CHANGE_DETECTED` | 변화 감지 |

`worst`, `최악` 등 단정적 라벨은 사용하지 않는다.

### 4.3 PRQC 구성요소

| API 키 | 화면 표시 |
|---|---|
| `satisfaction` | 만족감 |
| `commitment` | 헌신 |
| `intimacy` | 친밀감 |
| `trust` | 신뢰 |
| `passion` | 열정 |
| `love` | 애정 |

### 4.4 분석 작업 상태와 단계

작업 상태:

- `QUEUED`: 대기 중
- `RUNNING`: 분석 중
- `SUCCEEDED`: 완료
- `FAILED`: 실패
- `CANCELED`: 취소됨

진행 단계:

1. `LOADING_CONVERSATION` - 대화 파일 불러오기
2. `ANALYZING_MESSAGE_PATTERNS` - 메시지 패턴 분석
3. `ANALYZING_EMOTIONAL_FLOW` - 감정 흐름 파악
4. `CALCULATING_PRQC` - PRQC 점수 계산
5. `CALCULATING_RELATIONSHIP_SCORE` - 관계 온도 측정

`stage`와 `progress`는 AI 서버가 보고한 실제 내부 진행률이 아니라 백엔드가 파일 준비, AI 호출, 결과 저장 상태를 기준으로 계산한 UI 표시용 예상값이다.

- AI 결과 수신 전에는 `progress`를 최대 90까지만 증가시킨다.
- AI 결과 검증과 리포트 저장 중에는 최대 95까지 표시할 수 있다.
- 리포트가 저장되고 Job이 `SUCCEEDED`가 된 경우에만 `progress=100`을 반환한다.
- `progress=100`이면 `status`는 반드시 `SUCCEEDED`여야 한다.
- 정확한 남은 시간을 추정할 수 없으면 `estimatedSecondsRemaining`은 `null`이다.
- 현재 5개 단계는 프론트엔드 표시 호환성을 위한 매핑이며 AI 내부 파이프라인과 일치한다고 보장하지 않는다.

### 4.5 분석 실행 아키텍처와 책임

```text
Frontend
  └─ POST /relationships/{relationshipId}/analyses
       ↓ 202 Accepted + jobId
Backend API
  ├─ AnalysisJob 생성
  └─ Queue에 작업 등록
       ↓
Backend Worker
  ├─ 대화 파일 조회·검증·정규화
  ├─ AI 서버 동기 호출
  ├─ 관계 유형별 가중치로 overall.score 계산
  └─ 리포트 저장 및 Job 완료
       ↓
Frontend
  └─ GET /analysis-jobs/{jobId} 폴링
```

책임 구분:

| 주체 | 책임 |
|---|---|
| 프론트엔드 | 분석 요청, Job 폴링, 예상 진행률·결과 표시 |
| 백엔드 API | Job 생성, 소유권 검증, 즉시 `202` 반환 |
| 백엔드 Worker | 파일 준비, AI 호출, 재시도, 종합점수 계산, 리포트 저장 |
| AI 서버 | 정규화 대화 분석, PRQC 6요소와 관찰 근거 반환 |

AI 호출 예상 시간이 20~30초인 MVP에서는 Worker가 60~120초 내부 타임아웃으로 동기 호출한다. 분석 시간이 수분 단위로 증가할 경우에만 AI 내부 Job 생성·조회 API 도입을 재검토한다. 웹훅은 서명, 중복 전달, 재시도, 순서 역전 처리가 필요하므로 MVP 범위에서 제외한다.

## 5. 엔드포인트 요약

### 5.1 인증/사용자

| Method | Path | 설명 |
|---|---|---|
| GET | `/auth/kakao/authorize` | 카카오 OAuth 로그인 시작 |
| GET | `/auth/kakao/callback` | 카카오 OAuth 콜백 처리 |
| GET | `/users/me` | 현재 사용자와 CSRF 토큰 조회 |
| POST | `/auth/logout` | 로그아웃 및 세션 폐기 |

### 5.2 대시보드/관계/분석

| Method | Path | 설명 |
|---|---|---|
| GET | `/dashboard` | 주간 대시보드 집계 조회 |
| GET | `/relationships` | 관계 목록/검색/정렬 |
| POST | `/relationships` | 신규 관계 초안 생성 |
| GET | `/relationships/{relationshipId}` | 관계 기본정보 조회 |
| PATCH | `/relationships/{relationshipId}` | 이름/유형 수정 |
| DELETE | `/relationships/{relationshipId}` | 관계와 파생 데이터 삭제 요청 |
| POST | `/relationships/{relationshipId}/conversation-files` | 카카오톡 대화 파일 업로드 |
| GET | `/conversation-files/{fileId}` | 업로드 검증 상태 조회 |
| DELETE | `/conversation-files/{fileId}` | 미사용/업로드 파일 삭제 |
| POST | `/relationships/{relationshipId}/check-ins` | 체크인 응답 저장 |
| GET | `/relationships/{relationshipId}/check-ins` | 관계별·주차별 체크인 이력 조회 |
| POST | `/relationships/{relationshipId}/analyses` | 분석 작업 시작 |
| GET | `/analysis-jobs/{jobId}` | 분석 진행률/결과 조회 |
| GET | `/relationships/{relationshipId}/report` | 인물별 PRQC 리포트 조회 |

### 5.3 AI 상담

| Method | Path | 설명 |
|---|---|---|
| GET | `/consultations` | 최근 상담방 목록 |
| POST | `/consultations` | 새 상담 시작 |
| GET | `/consultations/{consultationId}` | 상담방 상세 조회 |
| DELETE | `/consultations/{consultationId}` | 상담방 삭제 |
| GET | `/consultations/{consultationId}/messages` | 메시지 이력 조회 |
| POST | `/consultations/{consultationId}/messages` | 사용자 메시지 전송 및 AI 생성 시작 |
| GET | `/consultations/{consultationId}/events` | AI 답변 SSE 스트림 |
| GET | `/support-resources` | 검증된 전문상담/지원 리소스 조회 |

## 6. 상세 명세

### 6.1 카카오 로그인 시작

`GET /auth/kakao/authorize?redirectUri={frontendUri}`

- 서버가 OAuth `state`, PKCE verifier/challenge를 생성하고 카카오 인증 화면으로 `302` 리다이렉트한다.
- `redirectUri`는 서버 allowlist에 등록된 프론트엔드 주소만 허용한다.
- 오픈 리다이렉트 방지를 위해 임의 외부 URL을 허용하지 않는다.

응답: `302 Found` + `Location: https://kauth.kakao.com/...`

### 6.2 카카오 OAuth 콜백

`GET /auth/kakao/callback?code={authorizationCode}&state={state}`

- 서버가 state와 PKCE를 검증하고 카카오 토큰을 서버 간 통신으로 교환한다.
- 최초 로그인은 사용자 계정을 만들고, 이후 로그인은 기존 계정을 사용한다.
- 성공 시 세션 쿠키를 설정하고 최초 `redirectUri`로 `302` 이동한다.
- 실패 시 프론트엔드 로그인 화면으로 오류 코드를 붙여 이동한다.

### 6.3 현재 사용자 조회

`GET /users/me`

```json
{
  "data": {
    "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "displayName": "김나영",
    "profileImageUrl": null,
    "timezone": "Asia/Seoul",
    "csrfToken": "csrf_7b7f...",
    "createdAt": "2026-08-01T04:00:00Z"
  }
}
```

### 6.4 로그아웃

`POST /auth/logout`

- 현재 세션을 서버에서 폐기하고 쿠키 만료 값을 설정한다.
- 멱등 동작으로 이미 만료된 세션에도 `204 No Content`를 반환할 수 있다.

### 6.5 주간 대시보드

`GET /dashboard?weekOf=2026-08-17&sort=ABS_CHANGE_DESC`

쿼리:

| 이름 | 필수 | 기본값 | 설명 |
|---|---|---|---|
| `weekOf` | N | 현재 주 월요일 | `YYYY-MM-DD` |
| `sort` | N | `ABS_CHANGE_DESC` | `ABS_CHANGE_DESC`, `SCORE_DESC`, `SCORE_ASC`, `UPDATED_DESC` |

```json
{
  "data": {
    "week": {
      "startDate": "2026-08-17",
      "endDate": "2026-08-23",
      "label": "2026년 8월 3주차"
    },
    "summary": {
      "relationshipCount": 6,
      "averageScore": 70,
      "averageChange": -3
    },
    "relationships": [
      {
        "id": "0198c8a7-0001-7000-8000-000000000001",
        "name": "최현우",
        "initial": "현",
        "relationshipType": "COWORKER",
        "status": "ACTIVE",
        "score": 45,
        "statusCode": "NEEDS_ATTENTION",
        "statusLabel": "주의 필요",
        "change": -22,
        "lastAnalyzedAt": "2026-08-17T05:00:00Z",
        "sparkline": [68, 65, 62, 57, 53, 49, 45]
      }
    ],
    "largestChanges": [
      {
        "relationshipId": "0198c8a7-0001-7000-8000-000000000001",
        "name": "최현우",
        "change": -22,
        "sparkline": [68, 65, 62, 57, 53, 49, 45]
      }
    ],
    "needsAttention": [
      {
        "relationshipId": "0198c8a7-0001-7000-8000-000000000001",
        "name": "최현우",
        "score": 45,
        "reasonCode": "SCORE_AND_DROP",
        "reasonLabel": "점수 하락이 관찰됨"
      }
    ]
  }
}
```

집계 규칙:

- `weekOf`는 해당 날짜가 포함된 월요일~일요일 주차로 정규화한다. 생략하면 사용자 타임존의 현재 날짜를 사용한다.
- 카드에는 선택 주차까지 완료된 관계별 최신 리포트를 사용한다. 선택 주차 이후의 미래 리포트는 포함하지 않는다.
- 분석 이력이 없는 `DRAFT` 관계와 삭제 중인 관계는 카드와 평균에서 제외한다.
- 이전 완료 리포트가 있는 `ANALYZING`, `ANALYSIS_FAILED` 관계는 마지막 결과를 유지할 수 있다.
- 각 카드의 `change`는 해당 카드 리포트 주차의 점수와 직전 주 최신점수 차이다.
- 직전 주 데이터가 없으면 `change`는 `null`이다. 이때 화살표를 표시하지 않는다.
- `averageScore`는 반환된 관계 카드 전체의 점수 평균을 반올림한다.
- `averageChange`는 `change != null`인 카드만 평균을 내어 반올림하며, 대상이 없으면 `null`이다.
- `largestChanges`는 `abs(change)` 내림차순 상위 3개다.
- `needsAttention`은 점수/변화 정책에 의해 서버가 선정한다. MVP 기본 규칙은 `score < 60 OR change <= -10`이다.
- `sparkline`은 선택 주차를 포함한 최근 8주 범위에서 주차별 최신 리포트 하나만 오래된 순서로 반환한다. 데이터가 없는 주차는 생략한다.
- 동일 주차에 재분석이 여러 번 있어도 카드와 스파크라인에는 가장 최근 완료 리포트만 사용한다.
- 관계 카드 정렬은 `ABS_CHANGE_DESC`, `SCORE_DESC`, `SCORE_ASC`, `UPDATED_DESC`를 지원한다.

### 6.6 관계 목록

`GET /relationships?search=김&sort=ABS_CHANGE_DESC&status=ACTIVE&limit=20&cursor=...`

| 쿼리 | 설명 |
|---|---|
| `search` | 이름 부분 검색, 앞뒤 공백 제거, 최대 50자 |
| `sort` | 대시보드와 동일한 정렬 enum |
| `status` | `DRAFT`, `ANALYZING`, `ACTIVE`, `ANALYSIS_FAILED` |
| `limit`, `cursor` | cursor 페이지네이션 |

응답 항목은 `RelationshipSummary`이며 점수, 증감, 관계 유형, 마지막 분석 시간을 포함한다.

### 6.7 신규 관계 생성

`POST /relationships`

```json
{
  "name": "홍길동",
  "relationshipType": "FRIEND"
}
```

검증:

- `name`: 공백 제거 후 1~50자
- `relationshipType`: 관계 유형 enum
- 동명이인은 허용한다.

응답: `201 Created`

```json
{
  "data": {
    "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "name": "홍길동",
    "initial": "길",
    "relationshipType": "FRIEND",
    "status": "DRAFT",
    "createdAt": "2026-08-19T06:20:00Z",
    "updatedAt": "2026-08-19T06:20:00Z"
  }
}
```

### 6.8 관계 수정 및 삭제

`PATCH /relationships/{relationshipId}`

```json
{
  "name": "홍길동",
  "relationshipType": "COWORKER"
}
```

`DELETE /relationships/{relationshipId}`

- 응답: `202 Accepted`
- 민감 파생 데이터까지 삭제하는 비동기 삭제 작업을 시작한다.
- 활성 분석 Job이 있으면 먼저 취소한다.
- 삭제 요청 직후 목록에서 숨기고, 복구 정책이 필요하다면 별도 제품 정책으로 정의한다.

### 6.9 카카오톡 대화 파일 업로드

`POST /relationships/{relationshipId}/conversation-files`

`multipart/form-data`:

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `file` | binary | Y | `.txt`, 최대 50MB |
| `source` | string | Y | MVP는 `KAKAO_TALK` |

응답: `201 Created`

```json
{
  "data": {
    "id": "0198c8a7-1000-7000-8000-000000000001",
    "relationshipId": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "originalFileName": "KakaoTalk_Chat.txt",
    "sizeBytes": 825341,
    "source": "KAKAO_TALK",
    "validationStatus": "VALID",
    "messageCount": 8421,
    "conversationStartedAt": "2025-12-01T01:20:00Z",
    "conversationEndedAt": "2026-08-18T14:44:00Z",
    "expiresAt": "2026-08-20T06:21:00Z",
    "uploadedAt": "2026-08-19T06:21:00Z"
  }
}
```

검증 규칙:

- 확장자뿐 아니라 MIME, BOM/인코딩, 파일 시그니처와 텍스트 파싱 가능 여부를 검증한다.
- 실행 파일, 심볼릭 링크, 압축 파일은 허용하지 않는다.
- 카카오톡 내보내기 형식을 인식할 수 없으면 `422 INVALID_KAKAO_EXPORT`를 반환한다.
- 참가자가 2명을 초과하는 단체 채팅은 MVP에서 `422 GROUP_CHAT_NOT_SUPPORTED`로 거부한다.
- 동일 파일 해시가 같은 관계에 이미 분석되었다면 `409 DUPLICATE_CONVERSATION_FILE`을 반환하거나 기존 file ID를 제공한다.

백엔드-AI 내부 전달 규칙:

- 프론트엔드 공개 응답에는 원본 또는 정규화 파일의 경로·URL을 노출하지 않는다.
- 50MB급 원문을 AI 요청 JSON 문자열에 포함하지 않는다.
- 백엔드가 카카오톡 원문을 검증·파싱한 뒤 정규화된 NDJSON을 gzip으로 압축해 Object Storage에 저장한다.
- AI 서버에는 다운로드 전용, 5~15분 만료 Presigned URL과 `sha256`, `sizeBytes`, `format`을 전달한다.
- Object Storage를 사용할 수 없는 MVP 환경에서는 `multipart/form-data` 스트리밍을 대안으로 사용한다.
- 서로 다른 컨테이너·호스트에서 유효하지 않을 수 있는 백엔드 로컬 파일 경로는 전달하지 않는다.
- 자세한 내부 계약은 [`AI_INTERNAL_API_SPEC.md`](./AI_INTERNAL_API_SPEC.md)를 따른다.

### 6.10 체크인 저장

`POST /relationships/{relationshipId}/check-ins`

```json
{
  "answers": [
    {
      "questionCode": "RELATIONSHIP_FEELING",
      "score": 5
    },
    {
      "questionCode": "CONVERSATION_COMFORT",
      "score": 4
    }
  ]
}
```

MVP 질문:

| `questionCode` | 질문 | 1점 | 7점 |
|---|---|---|---|
| `RELATIONSHIP_FEELING` | 요즘 이 사람과의 관계, 어떻게 느껴지세요? | 많이 불편해요 | 매우 좋아요 |
| `CONVERSATION_COMFORT` | 최근 이 사람과 대화할 때 얼마나 편안함을 느끼시나요? | 전혀 편안하지 않아요 | 매우 편안해요 |

서버는 사용자 타임존의 제출일이 속한 월요일을 `weekStart`로 계산한다. 같은 관계와
같은 주차에는 체크인을 하나만 유지한다.

- 해당 주차의 최초 제출: `201 Created` + `CheckIn` 객체
- 같은 주차 재제출: 기존 체크인 ID와 `createdAt`은 유지하고 두 응답을 갱신한 뒤
  `200 OK` + `CheckIn` 객체
- 두 질문은 각각 정확히 한 번 포함해야 하며, 누락·중복은
  `422 CHECK_IN_INCOMPLETE`, 1~7 범위 위반은 `400 INVALID_REQUEST`

`GET /relationships/{relationshipId}/check-ins?from=2026-08-01&to=2026-08-31`

관계의 주차별 체크인 이력을 `weekStart` 최신순으로 조회한다. `from`과 `to`는 모두
선택 사항이며 각 경곗값을 포함한다. 두 값이 모두 있고 `from > to`이면
`400 INVALID_REQUEST`를 반환한다. 현재 데이터 규모에서는 단일 페이지 응답을 사용하며
`meta.hasNext`는 `false`다.

MVP에서 체크인은 분석 시점의 사용자 주관 신호를 보존하기 위한 별도 데이터다. `checkInId`는 분석 Job 및 리포트와 연결하지만 체크인 응답을 AI 서버에 전달하지 않으며 PRQC 6요소와 `overall.score` 산식에도 반영하지 않는다. 향후 결합 점수를 도입할 때는 새로운 `scoringPolicyVersion`으로 명시한다.

### 6.11 분석 시작

`POST /relationships/{relationshipId}/analyses`

```json
{
  "conversationFileId": "0198c8a7-1000-7000-8000-000000000001",
  "checkInId": "0198c8a7-2000-7000-8000-000000000001"
}
```

응답: `202 Accepted`

```json
{
  "data": {
    "id": "0198c8a7-3000-7000-8000-000000000001",
    "relationshipId": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "status": "QUEUED",
    "stage": "LOADING_CONVERSATION",
    "progress": 0,
    "estimatedSecondsRemaining": 30,
    "reportId": null,
    "failure": null,
    "createdAt": "2026-08-19T06:22:00Z",
    "updatedAt": "2026-08-19T06:22:00Z"
  }
}
```

규칙:

- 파일/체크인/관계는 모두 현재 사용자 소유여야 한다.
- `conversationFileId`는 `VALID` 상태여야 한다.
- 관계별 동시에 하나의 분석만 허용한다. 중복 요청은 `409 ANALYSIS_ALREADY_RUNNING`과 기존 Job ID를 반환한다.
- 분석이 완료되기 전에는 기존 리포트가 있으면 기존 리포트를 계속 보여주고 `isRefreshing: true`를 표시할 수 있다.
- `checkInId`는 분석 당시 체크인 스냅샷과의 연결을 위한 메타데이터이며 AI 입력에는 포함하지 않는다.
- 백엔드 API는 Job을 만든 뒤 Queue에 작업을 등록하고 즉시 `202`를 반환한다.
- 백엔드 Worker는 AI 서버의 `POST /internal/v1/prqc-analyses`를 동기 호출한다. AI 서버의 웹훅·콜백은 사용하지 않는다.
- Queue 등록 자체가 불가능하면 Job 접수 전 `503 ANALYSIS_UNAVAILABLE`을 반환할 수 있다.

### 6.12 분석 Job 조회

`GET /analysis-jobs/{jobId}`

클라이언트 권장 폴링:

- 첫 10초: 1초 간격
- 이후: 2초 간격
- `SUCCEEDED`, `FAILED`, `CANCELED`에서 중단
- 탭이 백그라운드면 폴링 간격을 늘린다.

완료 예시:

```json
{
  "data": {
    "id": "0198c8a7-3000-7000-8000-000000000001",
    "relationshipId": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "status": "SUCCEEDED",
    "stage": "CALCULATING_RELATIONSHIP_SCORE",
    "progress": 100,
    "estimatedSecondsRemaining": 0,
    "reportId": "0198c8a7-4000-7000-8000-000000000001",
    "failure": null,
    "createdAt": "2026-08-19T06:22:00Z",
    "updatedAt": "2026-08-19T06:22:27Z"
  }
}
```

실패 시 `failure.code`, `failure.message`, `failure.retryable`을 포함한다. 민감한 원문이나 모델 내부 오류는 노출하지 않는다.

실행 중 실패 예시:

```json
{
  "data": {
    "id": "0198c8a7-3000-7000-8000-000000000001",
    "relationshipId": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "status": "FAILED",
    "stage": "ANALYZING_MESSAGE_PATTERNS",
    "progress": 40,
    "estimatedSecondsRemaining": null,
    "reportId": null,
    "failure": {
      "code": "ANALYSIS_UNAVAILABLE",
      "message": "일시적으로 분석할 수 없어요. 잠시 후 다시 시도해 주세요.",
      "retryable": true
    },
    "createdAt": "2026-08-19T06:22:00Z",
    "updatedAt": "2026-08-19T06:23:10Z"
  }
}
```

Job이 이미 `202`로 접수된 후 발생한 AI 오류는 프론트엔드에 뒤늦게 HTTP `503`으로 전달하지 않는다. `GET /analysis-jobs/{jobId}`는 `200`을 반환하고 본문의 `status=FAILED`와 `failure`로 실패를 알린다. 백엔드 Worker는 내부 AI 응답 `429`, `503`, `504`를 지수 백오프로 2~3회 재시도한 뒤 최종 실패 처리한다.

### 6.13 인물별 리포트

`GET /relationships/{relationshipId}/report?weeks=8`

```json
{
  "data": {
    "id": "0198c8a7-4000-7000-8000-000000000001",
    "relationship": {
      "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
      "name": "박민준",
      "initial": "민",
      "relationshipType": "FRIEND"
    },
    "overall": {
      "score": 58,
      "change": -15,
      "statusCode": "NEEDS_ATTENTION",
      "statusLabel": "주의 필요"
    },
    "prqc": {
      "satisfaction": 55,
      "commitment": 45,
      "intimacy": 68,
      "trust": 72,
      "passion": 40,
      "love": 58
    },
    "evidences": [
      {
        "id": "0198c8a7-4100-7000-8000-000000000001",
        "component": "passion",
        "score": 40,
        "summary": "최근 한 달간 대화 빈도가 주 평균 3.2회에서 1.1회로 줄어든 것이 관찰됐어요.",
        "metric": {
          "name": "weeklyConversationCount",
          "currentValue": 1.1,
          "previousValue": 3.2,
          "unit": "회/주",
          "period": "최근 4주 vs 이전 4주"
        }
      }
    ],
    "trend": [
      {
        "weekStart": "2026-06-29",
        "label": "7주 전",
        "score": 75
      },
      {
        "weekStart": "2026-08-17",
        "label": "이번 주",
        "score": 58
      }
    ],
    "analyzedAt": "2026-08-18T05:00:00Z",
    "modelVersion": "prqc-2026-08-19.1",
    "scoringPolicyVersion": "relationship-temperature-1.0.0",
    "disclaimer": "대화에서 관찰된 패턴을 바탕으로 한 참고 정보이며 관계를 진단하거나 단정하지 않습니다."
  }
}
```

리포트 규칙:

- 점수에는 반드시 `evidences` 또는 점수 산정 설명을 함께 제공한다.
- AI 서버는 `prqc` 6개 구성요소와 `evidences`를 계산한다.
- 백엔드는 관계 유형별 버전된 가중치 정책으로 canonical `overall.score`를 계산하고, 프론트엔드는 이 값을 재계산하지 않는다.
- 리포트의 `weekStart`는 분석 Job에 연결된 체크인의 주차 시작일을 사용한다.
- `overall.change`는 단순 직전 분석이 아니라 직전 주차의 가장 최근 완료 리포트 점수와 비교한다. 직전 주 리포트가 없으면 `null`이다.
- 추이는 주차별로 가장 최근 완료된 리포트 하나만 포함한다. 같은 주에 재분석해도 추이 점이 중복되지 않는다.
- `statusCode`, `statusLabel`, `disclaimer`는 리포트 생성 시점의 스냅샷으로 저장하여 이후 정책 변경이 과거 리포트 표현을 바꾸지 않게 한다.
- 각 관찰 근거의 `score`는 해당 `component`의 PRQC 점수와 일치해야 한다.
- `modelVersion`은 AI의 PRQC 분석 버전이며 `prqc-YYYY-MM-DD.N` 형식을 권장한다.
- `scoringPolicyVersion`은 백엔드 종합점수 산식 버전이며 Semantic Versioning을 사용한다.
- AI가 평가 목적으로 제안 종합점수를 반환하더라도 canonical `overall.score`로 저장하지 않는다.
- 근거 문장은 확정적 인과관계 대신 관찰 사실로 작성한다.
- 원문 메시지를 그대로 보여줘야 한다면 별도 사용자 동의, 마스킹, 최소 노출 정책이 필요하다. MVP 응답은 집계 근거를 우선한다.
- `weeks` 허용값은 4~52, 기본값은 8이다.
- 완료된 리포트가 없으면 `409 REPORT_REQUIRED`를 반환한다.

### 6.14 최근 상담 목록

`GET /consultations?limit=20&cursor=...`

```json
{
  "data": [
    {
      "id": "0198c8a7-5000-7000-8000-000000000001",
      "relationship": {
        "id": "0198c8a7-0001-7000-8000-000000000002",
        "name": "김지수",
        "initial": "지",
        "relationshipType": "ROMANTIC_PARTNER"
      },
      "lastMessagePreview": "그렇게 느낀 데에는 충분한 이유가 있어요.",
      "lastMessageAt": "2026-08-19T11:48:00Z",
      "unreadCount": 1
    }
  ],
  "meta": {
    "nextCursor": null,
    "hasNext": false
  }
}
```

### 6.15 새 상담 시작

`POST /consultations`

```json
{
  "relationshipId": "0198c8a7-0001-7000-8000-000000000002"
}
```

응답: `201 Created`

- 가장 최근 완료 리포트를 상담 컨텍스트로 연결한다.
- 분석 완료 리포트가 없으면 `409 REPORT_REQUIRED`를 반환한다.
- 같은 관계에 여러 상담방 생성을 허용하되, UX에서 기존 상담 재개 여부를 선택할 수 있다.

### 6.16 상담 메시지 조회

`GET /consultations/{consultationId}/messages?limit=50&before={messageId}`

- 최신 메시지 기준 역방향 페이지네이션을 사용한다.
- 화면에는 시간순으로 정렬해 표시한다.
- 메시지 `status`는 `GENERATING`, `COMPLETED`, `FAILED` 중 하나다.

### 6.17 사용자 메시지 전송

`POST /consultations/{consultationId}/messages`

```json
{
  "content": "요즘은 전보다 가까워진 것 같은데, 답장이 늦으면 불안해져요."
}
```

검증:

- 앞뒤 공백 제거 후 1~4000자
- 동일 상담방에서 AI 응답을 동시에 1개만 생성
- 사용자별/세션별 호출 제한 적용

응답: `202 Accepted`

```json
{
  "data": {
    "userMessage": {
      "id": "0198c8a7-6000-7000-8000-000000000001",
      "role": "USER",
      "content": "요즘은 전보다 가까워진 것 같은데, 답장이 늦으면 불안해져요.",
      "status": "COMPLETED",
      "createdAt": "2026-08-19T11:47:00Z"
    },
    "assistantMessage": {
      "id": "0198c8a7-6000-7000-8000-000000000002",
      "role": "ASSISTANT",
      "content": "",
      "status": "GENERATING",
      "createdAt": "2026-08-19T11:47:00Z"
    },
    "streamUrl": "/api/v1/consultations/0198c8a7-5000-7000-8000-000000000001/events?after=0198c8a7-6000-7000-8000-000000000001"
  }
}
```

AI 완료 메시지는 다음 구조를 가질 수 있다.

```json
{
  "id": "0198c8a7-6000-7000-8000-000000000002",
  "role": "ASSISTANT",
  "content": "제가 볼 수 있는 건 대화 속 패턴뿐이라 확정해서 말씀드리긴 어려워요. 다만 최근 대화에서 회피성 답변이 반복된 건 관찰돼요.",
  "status": "COMPLETED",
  "evidenceRefs": [
    {
      "evidenceId": "ev_01J5P8K9W8G0H7P9T0W1K2J3M4",
      "label": "최근 4주 응답 패턴"
    }
  ],
  "safetyNotice": {
    "type": "SUPPORT_RECOMMENDATION",
    "title": "마음을 돌보는 제안",
    "message": "이런 패턴이 반복된다면 전문 상담사와 이야기 나눠보는 것도 방법이 될 수 있어요.",
    "resourceQuery": {
      "category": "MENTAL_HEALTH_COUNSELING",
      "region": "KR"
    }
  },
  "createdAt": "2026-08-19T11:47:03Z"
}
```

### 6.18 AI 답변 SSE

`GET /consultations/{consultationId}/events?after={messageId}`

헤더:

```http
Accept: text/event-stream
Cache-Control: no-cache
```

이벤트 예시:

```text
event: assistant.delta
id: evt_01
data: {"messageId":"0198...002","delta":"그럴 수 있어요."}

event: assistant.completed
id: evt_02
data: {"message":{"id":"0198...002","role":"ASSISTANT","content":"그럴 수 있어요...","status":"COMPLETED","evidenceRefs":[],"safetyNotice":null,"createdAt":"2026-08-19T11:47:03Z"}}
```

이벤트 종류:

| 이벤트 | 설명 |
|---|---|
| `assistant.started` | 생성 시작 |
| `assistant.delta` | 텍스트 증분 |
| `assistant.completed` | 최종 구조화 메시지 |
| `assistant.failed` | 생성 실패, 재시도 가능 여부 포함 |
| `heartbeat` | 연결 유지용, 15초 간격 권장 |

재연결:

- 서버는 SSE `id`를 발급한다.
- 클라이언트는 재연결 시 `Last-Event-ID`를 보낸다.
- 스트림이 끊겨도 `GET /consultations/{consultationId}/messages`로 최종 상태를 복구할 수 있어야 한다.

### 6.19 상담 리소스

`GET /support-resources?region=KR&category=MENTAL_HEALTH_COUNSELING`

응답 항목:

- 기관/서비스 이름
- 짧은 설명
- 공식 URL 또는 전화번호
- 운영시간(확인 가능한 경우)
- `verifiedAt`과 `source`

운영 원칙:

- 리소스는 관리자 검수 목록만 노출한다.
- 긴급 위험 신호가 감지된 경우 일반 AI 답변으로만 처리하지 말고 제품의 별도 위기 대응 정책을 적용한다.
- 연락처, 운영시간, 공식 URL은 변경될 수 있으므로 정기 검증이 필요하다.

## 7. 화면-API 매핑

| 화면/행동 | 호출 API | 주요 필드 |
|---|---|---|
| 카카오 계정 연동 | `GET /auth/kakao/authorize` | `redirectUri` |
| 로그인 사용자/프로필 | `GET /users/me` | `displayName`, `profileImageUrl` |
| 메인 대시보드 | `GET /dashboard` | 평균, 관계 카드, 변화 상위, 주의 관계 |
| 카드 정렬 | `GET /dashboard?sort=...` | `sort` |
| 인물 목록/검색 | `GET /relationships?search=...` | 이름, 유형, 점수, 변화 |
| 인물 등록 1단계 | `POST /relationships` | 이름, 관계 유형 |
| 인물 등록 2단계 | `POST /relationships/{relationshipId}/conversation-files` | `.txt` 파일 |
| 인물 등록 3단계 | `POST /relationships/{relationshipId}/check-ins` | 1~7점 2문항 |
| 분석 시작/로딩 | `POST /relationships/{relationshipId}/analyses`, `GET /analysis-jobs/{jobId}` | 진행률, 단계, 상태 |
| 인물별 상세 리포트 | `GET /relationships/{relationshipId}/report` | 종합점수, 변화, PRQC, 근거, 8주 추이 |
| 대화 내역 추가 | 파일 업로드 → 체크인(선택) → 분석 시작 | 새 파일/Job |
| AI 상담 목록 | `GET /consultations` | 미리보기, 시간, 읽지 않음 |
| 새 상담 | `POST /consultations` | `relationshipId` |
| 메시지 이력 | `GET /consultations/{consultationId}/messages` | 사용자/AI 말풍선 |
| 메시지 전송/응답 | `POST /consultations/{consultationId}/messages`, SSE `GET /consultations/{consultationId}/events` | 증분 답변, 근거, 안전 제안 |
| 상담 리소스 보기 | `GET /support-resources` | 검증된 지원 정보 |
| 로그아웃 | `POST /auth/logout` | 204 |

## 8. 상태 전이

### 8.1 관계 등록/분석

```text
DRAFT
  └─ 파일 검증 + 체크인 완료 + 분석 시작
       → ANALYZING
          ├─ 성공 → ACTIVE
          └─ 실패 → ANALYSIS_FAILED
                         └─ 재시도 → ANALYZING
```

프론트엔드는 관계 `status`에 따라 다음과 같이 표시한다.

- `DRAFT`: 등록 이어서 하기
- `ANALYZING`: 진행률/단계 표시
- `ACTIVE`: 대시보드 및 리포트 표시
- `ANALYSIS_FAILED`: 중립적 오류 안내와 재시도 제공

### 8.2 상담 메시지

```text
사용자 메시지 저장(COMPLETED)
  → AI 메시지 자리 생성(GENERATING)
     ├─ SSE delta 누적 → COMPLETED
     └─ 생성 실패 → FAILED → 사용자 재시도
```

## 9. 보안·개인정보·AI 안전 요구사항

### 9.1 인증/세션

- OAuth 토큰은 서버에서만 보관하고 브라우저로 전달하지 않는다.
- 세션 고정 공격 방지를 위해 로그인 성공 시 세션 ID를 재발급한다.
- 로그아웃/비밀번호 또는 연결 해제 성격의 이벤트 발생 시 활성 세션을 폐기한다.
- 객체 조회 시 항상 현재 사용자 소유권을 검증한다. UUID를 안다고 다른 사용자의 관계/파일/리포트/상담방에 접근할 수 없어야 한다.

### 9.2 업로드/대화 데이터

- 업로드 파일은 실행 불가능한 격리 저장소에 보관한다.
- 파일명은 표시용으로만 사용하고 실제 저장 경로에 직접 사용하지 않는다.
- 악성 파일 탐지, 크기 제한, 파서 시간/메모리 제한을 적용한다.
- 로그, 오류 추적, 분석 메트릭에 원문 대화 내용을 기록하지 않는다.
- AI 서버에 전달하는 Presigned URL은 다운로드 전용, 5~15분 만료로 발급하고 사용자 이름이나 원본 파일명을 URL에 포함하지 않는다.
- AI 서버는 전달받은 정규화 파일의 `sha256`과 `sizeBytes`를 검증한다.
- 삭제 요청은 원본, 파싱 결과, 임베딩, 캐시, 파생 리포트, 상담 컨텍스트에 전파되어야 한다.
- 백업에서의 삭제/만료 정책도 별도로 정의한다.

### 9.3 점수/근거

- 모든 점수 결과에 분석 시점, AI `modelVersion`, 백엔드 `scoringPolicyVersion`, 근거 요약, 면책 문구를 제공한다.
- 모델 버전이 바뀌면 과거 점수와 직접 비교 가능한지 검증하고, 불가능하면 추이 그래프에 기준 변경을 표시한다.
- 가중치 정책이 바뀐 경우에도 과거 추이의 재계산 여부와 기준 변경 표시 정책을 적용한다.
- 근거 문장은 제3자의 민감정보를 불필요하게 노출하지 않도록 집계/마스킹한다.

### 9.4 AI 상담

- 시스템 프롬프트와 대화 원문을 API 응답이나 오류에 노출하지 않는다.
- 사용자 입력 및 업로드 파일을 신뢰할 수 없는 입력으로 취급하고 프롬프트 인젝션 방어를 적용한다.
- 답변에는 확정적 진단, 관계 단절 강요, 의료/법률적 단정 표현을 사용하지 않는다.
- 근거가 없는 수치나 원문 인용을 생성하지 않는다.
- 안전 신호는 `safetyNotice`로 구조화하여 UI가 일관되게 렌더링하도록 한다.
- 긴급 위험 신호 처리, 사람 검토, 신고/에스컬레이션 범위는 출시 전 별도 정책으로 승인해야 한다.

### 9.5 백엔드-AI 내부 통신

- Private subnet 여부와 관계없이 `Authorization: Bearer {service-token}` 기반 서비스 인증을 적용한다.
- 사용자 세션 쿠키, 카카오 OAuth 토큰, CSRF 토큰을 AI 서버에 전달하지 않는다.
- 모든 내부 요청에 `X-Request-Id`와 `Idempotency-Key: {analysisId}`를 전달한다.
- 운영 단계에서는 정적 토큰을 짧은 만료 서비스 JWT, 클라우드 IAM 또는 mTLS로 교체할 수 있다.
- AI 오류의 원문 스택, 공급자 응답, 프롬프트를 공개 API 오류로 그대로 전달하지 않는다.

## 10. 캐시 및 성능 권장값

| API | 목표 | 캐시 |
|---|---:|---|
| `GET /users/me` | p95 300ms 이하 | `private, no-store` |
| `GET /dashboard` | p95 700ms 이하 | 사용자별 30초, ETag 권장 |
| `GET /relationships` | p95 500ms 이하 | `private, max-age=15` 가능 |
| `GET /relationships/{relationshipId}/report` | p95 700ms 이하 | ETag, 분석 완료 시 무효화 |
| 파일 업로드 | 50MB 기준 타임아웃 120초 | 캐시 금지 |
| 분석 Job 조회 | p95 300ms 이하 | 캐시 금지 |
| AI 첫 토큰 | p95 3초 이하 목표 | 캐시 금지 |

## 11. 출시 전 결정이 필요한 항목

1. 원본 대화 파일과 파생 데이터의 정확한 보존 기간
2. 단체 채팅 지원 여부와 대화 상대 식별 UX
3. 체크인을 동일 주차에 수정할지 이력으로 누적할지
4. 관계 유형별 종합점수 가중치의 구체적인 값과 모델 검증 기준
5. 분석 실패/부분 성공 시 사용자에게 보여줄 범위
6. 전문상담 리소스의 제공 국가, 검수 주체, 업데이트 주기
7. 긴급 위험 신호의 정의와 위기 대응 플로우
8. 계정 탈퇴, 카카오 연결 해제, 데이터 다운로드 API의 출시 범위
9. 사용자 동의 화면 및 제3자 대화 데이터 처리 근거
10. API rate limit의 구체적인 사용자별/엔드포인트별 수치

## 12. 권장 구현 순서

1. 카카오 로그인, 세션, `/users/me`
2. 관계 CRUD, 파일 업로드/검증, 체크인
3. 분석 Job과 리포트 저장 모델
4. 대시보드 집계와 관계 목록/검색
5. 상담방/메시지와 SSE 스트리밍
6. 안전 제안/상담 리소스, 삭제 전파, 감사 로그
