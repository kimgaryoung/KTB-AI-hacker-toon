# 관계온도 API 상세 명세서

> 버전: v1.2  
> 기준일: 2026-08-19  
> Base URL: `/api/v1`  
> 계약 원본: [`openapi.yaml`](./openapi.yaml)

## 문서 사용법

이 문서는 프론트엔드, 백엔드, QA가 별도 해석 없이 사용할 수 있도록 **API 한 개당 하나의 독립 명세**로 구성했습니다. 각 API에는 용도, 요청 위치별 필드, 요청 예시, 성공 응답 예시, 응답 필드 설명과 오류 상태를 포함합니다. 예시 UUID와 시각은 형식 설명용이며 실제 값은 요청마다 달라집니다.

## 공통 요청 규칙

- 로그인 API를 제외한 보호 API는 `rt_session` 쿠키를 사용합니다.
- 상태 변경 요청은 `X-CSRF-Token` 헤더가 필요합니다.
- 생성·분석·메시지 전송 요청은 `Idempotency-Key` 사용을 권장합니다.
- 모든 응답에는 `X-Request-Id`가 반환됩니다.
- 일시는 ISO 8601 UTC, 리소스 ID는 UUID 문자열입니다.

## 공통 오류 응답

```json
{
  "error": {
    "code": "INVALID_REQUEST",
    "message": "요청 값을 확인해 주세요.",
    "requestId": "req_01J5P8K9W8G0H7P9T0W1K2J3M4",
    "fields": [
      {
        "field": "name",
        "reason": "REQUIRED"
      }
    ]
  }
}
```

## API 목록

| No. | Method | Endpoint | 설명 |
|---|---|---|---|
| 1 | GET | `/api/v1/auth/kakao/authorize` | 카카오 로그인 시작 |
| 2 | GET | `/api/v1/auth/kakao/callback` | 카카오 OAuth 콜백 처리 |
| 3 | POST | `/api/v1/auth/logout` | 로그아웃 |
| 4 | GET | `/api/v1/users/me` | 현재 사용자와 CSRF 토큰 조회 |
| 5 | GET | `/api/v1/dashboard` | 주간 관계 대시보드 조회 |
| 6 | GET | `/api/v1/relationships` | 관계 목록 조회 및 이름 검색 |
| 7 | POST | `/api/v1/relationships` | 신규 관계 초안 생성 |
| 8 | GET | `/api/v1/relationships/{relationshipId}` | 관계 기본정보 조회 |
| 9 | PATCH | `/api/v1/relationships/{relationshipId}` | 관계 이름 또는 유형 수정 |
| 10 | DELETE | `/api/v1/relationships/{relationshipId}` | 관계 및 파생 데이터 삭제 요청 |
| 11 | POST | `/api/v1/relationships/{relationshipId}/conversation-files` | 카카오톡 대화 내보내기 파일 업로드 |
| 12 | GET | `/api/v1/conversation-files/{fileId}` | 업로드 파일 검증 상태 조회 |
| 13 | DELETE | `/api/v1/conversation-files/{fileId}` | 업로드 파일 삭제 |
| 14 | POST | `/api/v1/relationships/{relationshipId}/check-ins` | 1~7점 주관적 체크인 저장 |
| 15 | POST | `/api/v1/relationships/{relationshipId}/analyses` | 대화 분석 Job 시작 |
| 16 | GET | `/api/v1/analysis-jobs/{jobId}` | 분석 진행률 및 완료 결과 조회 |
| 17 | GET | `/api/v1/relationships/{relationshipId}/report` | 인물별 PRQC 리포트 조회 |
| 18 | GET | `/api/v1/consultations` | 최근 상담방 목록 조회 |
| 19 | POST | `/api/v1/consultations` | 새 AI 상담 시작 |
| 20 | GET | `/api/v1/consultations/{consultationId}` | 상담방 상세 조회 |
| 21 | DELETE | `/api/v1/consultations/{consultationId}` | 상담방과 메시지 삭제 |
| 22 | GET | `/api/v1/consultations/{consultationId}/messages` | 상담 메시지 이력 조회 |
| 23 | POST | `/api/v1/consultations/{consultationId}/messages` | 사용자 메시지 저장 및 AI 답변 생성 시작 |
| 24 | GET | `/api/v1/consultations/{consultationId}/events` | AI 답변 Server-Sent Events 스트림 |
| 25 | GET | `/api/v1/support-resources` | 검증된 전문상담 및 지원 리소스 조회 |

## 1. 카카오 로그인 시작

`GET /api/v1/auth/kakao/authorize`

### 어떤 API인가요?

카카오 로그인 시작.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | N | 로그인 세션 `rt_session` |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| redirectUri | query | uri | Y | 로그인 후 이동할 allowlist 등록 프론트엔드 URL |

### Request Body

요청 본문 없음. Path 또는 Query 값만 사용합니다.

### 성공 Response

HTTP `302` — 카카오 인증 화면으로 이동


응답 본문 없음.

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 400 | 잘못된 요청 |

---

## 2. 카카오 OAuth 콜백 처리

`GET /api/v1/auth/kakao/callback`

### 어떤 API인가요?

카카오 OAuth 콜백 처리.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | N | 로그인 세션 `rt_session` |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| code | query | string | Y | code 값 |
| state | query | string | Y | state 값 |

### Request Body

요청 본문 없음. Path 또는 Query 값만 사용합니다.

### 성공 Response

HTTP `302` — 세션 쿠키 설정 후 프론트엔드로 이동


응답 본문 없음.

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 400 | 잘못된 요청 |

---

## 3. 로그아웃

`POST /api/v1/auth/logout`

### 어떤 API인가요?

로그아웃.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |
| X-CSRF-Token | string | Y | 상태 변경 요청 위조 방지 토큰 |
| Idempotency-Key | string | N | 중복 생성 방지를 위한 멱등 키 |

### Path·Query 요청값

해당 없음

### Request Body

요청 본문 없음. Path 또는 Query 값만 사용합니다.

### 성공 Response

HTTP `204` — 세션 폐기 완료


응답 본문 없음.

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 403 | CSRF 검증 실패 또는 접근 권한 없음 |

---

## 4. 현재 사용자와 CSRF 토큰 조회

`GET /api/v1/users/me`

### 어떤 API인가요?

현재 사용자와 CSRF 토큰 조회.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |

### Path·Query 요청값

해당 없음

### Request Body

요청 본문 없음. Path 또는 Query 값만 사용합니다.

### 성공 Response

HTTP `200` — 현재 사용자


#### 응답 JSON 예시

```json
{
  "data": {
    "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "displayName": "홍길동",
    "profileImageUrl": null,
    "timezone": "Asia/Seoul",
    "csrfToken": "example",
    "createdAt": "2026-08-19T06:20:00Z"
  }
}
```

#### 응답 필드 설명

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| data | object | Y | 응답에 포함되는 data 값 |
| data.id | uuid | Y | 응답에 포함되는 id 값 |
| data.displayName | string | Y | 응답에 포함되는 displayName 값 |
| data.profileImageUrl | uri | N | 응답에 포함되는 profileImageUrl 값 |
| data.timezone | string | Y | 응답에 포함되는 timezone 값 |
| data.csrfToken | string | Y | 응답에 포함되는 csrfToken 값 |
| data.createdAt | date-time | Y | 응답에 포함되는 createdAt 값 |

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 401 | 로그인 필요 또는 세션 만료 |

---

## 5. 주간 관계 대시보드 조회

`GET /api/v1/dashboard`

### 어떤 API인가요?

주간 관계 대시보드 조회.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| weekOf | query | date | N | 포함 주차를 찾기 위한 날짜. 생략 시 사용자 타임존의 현재 날짜. |
|  |  | object | N |  값 |

### Request Body

요청 본문 없음. Path 또는 Query 값만 사용합니다.

### 성공 Response

HTTP `200` — 주간 요약, 관계 카드, 변화 상위, 주의 관계


#### 응답 JSON 예시

```json
{
  "data": {
    "week": {
      "startDate": "2026-08-17",
      "endDate": "2026-08-17",
      "label": "example"
    },
    "summary": {
      "relationshipCount": 0,
      "averageScore": null,
      "averageChange": null
    },
    "relationships": [
      {
        "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
        "name": "홍길동",
        "initial": "example",
        "relationshipType": "ROMANTIC_PARTNER"
      }
    ],
    "largestChanges": [
      {
        "relationshipId": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
        "name": "홍길동",
        "change": -100,
        "sparkline": [
          0
        ]
      }
    ],
    "needsAttention": [
      {
        "relationshipId": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
        "name": "홍길동",
        "score": 0,
        "reasonCode": "LOW_SCORE",
        "reasonLabel": "example"
      }
    ]
  }
}
```

#### 응답 필드 설명

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| data | object | Y | 응답에 포함되는 data 값 |
| data.week | object | Y | 응답에 포함되는 week 값 |
| data.week.startDate | date | Y | 응답에 포함되는 startDate 값 |
| data.week.endDate | date | Y | 응답에 포함되는 endDate 값 |
| data.week.label | string | Y | 응답에 포함되는 label 값 |
| data.summary | object | Y | 응답에 포함되는 summary 값 |
| data.summary.relationshipCount | integer | Y | 응답에 포함되는 relationshipCount 값 |
| data.summary.averageScore | ["integer", "null"] | Y | 응답에 포함되는 averageScore 값 |
| data.summary.averageChange | ["integer", "null"] | Y | 응답에 포함되는 averageChange 값 |
| data.relationships | array<object> | Y | 응답에 포함되는 relationships 값 |
| data.largestChanges | array<object> | Y | 응답에 포함되는 largestChanges 값 |
| data.needsAttention | array<object> | Y | 응답에 포함되는 needsAttention 값 |

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 400 | 잘못된 요청 |
| 401 | 로그인 필요 또는 세션 만료 |

---

## 6. 관계 목록 조회 및 이름 검색

`GET /api/v1/relationships`

### 어떤 API인가요?

관계 목록 조회 및 이름 검색.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| search | query | string | N | search 값 |
|  |  | object | N |  값 |
| status | query | `DRAFT` \| `ANALYZING` \| `ACTIVE` \| `ANALYSIS_FAILED` \| `DELETING` | N | status 값 |
|  |  | object | N |  값 |
|  |  | object | N |  값 |

### Request Body

요청 본문 없음. Path 또는 Query 값만 사용합니다.

### 성공 Response

HTTP `200` — 관계 목록


#### 응답 JSON 예시

```json
{
  "data": [
    {
      "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
      "name": "홍길동",
      "initial": "example",
      "relationshipType": "ROMANTIC_PARTNER"
    }
  ],
  "meta": {
    "nextCursor": null,
    "hasNext": false
  }
}
```

#### 응답 필드 설명

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| data | array<object> | Y | 응답에 포함되는 data 값 |
| meta | object | Y | 응답에 포함되는 meta 값 |
| meta.nextCursor | ["string", "null"] | Y | 응답에 포함되는 nextCursor 값 |
| meta.hasNext | boolean | Y | 응답에 포함되는 hasNext 값 |

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 401 | 로그인 필요 또는 세션 만료 |

---

## 7. 신규 관계 초안 생성

`POST /api/v1/relationships`

### 어떤 API인가요?

신규 관계 초안 생성.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |
| X-CSRF-Token | string | Y | 상태 변경 요청 위조 방지 토큰 |
| Idempotency-Key | string | N | 중복 생성 방지를 위한 멱등 키 |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
|  |  | object | N |  값 |

### Request Body

요청 형식: `application/json`


| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| name | string | Y | 응답에 포함되는 name 값 (최대 50자) |
| relationshipType | `ROMANTIC_PARTNER` \| `FRIEND` \| `FAMILY` \| `COWORKER` \| `OTHER` | Y | 응답에 포함되는 relationshipType 값 |

#### 요청 JSON 예시

```json
{
  "name": "홍길동",
  "relationshipType": "ROMANTIC_PARTNER"
}
```

### 성공 Response

HTTP `201` — 관계 생성 완료


#### 응답 JSON 예시

```json
{
  "data": {
    "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "name": "홍길동",
    "initial": "example",
    "relationshipType": "ROMANTIC_PARTNER"
  }
}
```

#### 응답 필드 설명

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| data | object | Y | 응답에 포함되는 data 값 |

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 400 | 잘못된 요청 |
| 401 | 로그인 필요 또는 세션 만료 |
| 403 | CSRF 검증 실패 또는 접근 권한 없음 |
| 409 | 리소스 상태 또는 멱등성 충돌 |

---

## 8. 관계 기본정보 조회

`GET /api/v1/relationships/{relationshipId}`

### 어떤 API인가요?

관계 기본정보 조회.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
|  |  | object | N |  값 |

### Request Body

요청 본문 없음. Path 또는 Query 값만 사용합니다.

### 성공 Response

HTTP `200` — 관계 기본정보


#### 응답 JSON 예시

```json
{
  "data": {
    "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "name": "홍길동",
    "initial": "example",
    "relationshipType": "ROMANTIC_PARTNER"
  }
}
```

#### 응답 필드 설명

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| data | object | Y | 응답에 포함되는 data 값 |

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 401 | 로그인 필요 또는 세션 만료 |
| 404 | 리소스를 찾을 수 없음 |

---

## 9. 관계 이름 또는 유형 수정

`PATCH /api/v1/relationships/{relationshipId}`

### 어떤 API인가요?

관계 이름 또는 유형 수정.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |
| X-CSRF-Token | string | Y | 상태 변경 요청 위조 방지 토큰 |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
|  |  | object | N |  값 |

### Request Body

요청 형식: `application/json`


| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| name | string | N | 응답에 포함되는 name 값 (최대 50자) |
| relationshipType | `ROMANTIC_PARTNER` \| `FRIEND` \| `FAMILY` \| `COWORKER` \| `OTHER` | N | 응답에 포함되는 relationshipType 값 |

#### 요청 JSON 예시

```json
{
  "name": "홍길동",
  "relationshipType": "ROMANTIC_PARTNER"
}
```

### 성공 Response

HTTP `200` — 수정된 관계


#### 응답 JSON 예시

```json
{
  "data": {
    "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "name": "홍길동",
    "initial": "example",
    "relationshipType": "ROMANTIC_PARTNER"
  }
}
```

#### 응답 필드 설명

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| data | object | Y | 응답에 포함되는 data 값 |

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 400 | 잘못된 요청 |
| 403 | CSRF 검증 실패 또는 접근 권한 없음 |
| 404 | 리소스를 찾을 수 없음 |

---

## 10. 관계 및 파생 데이터 삭제 요청

`DELETE /api/v1/relationships/{relationshipId}`

### 어떤 API인가요?

관계 및 파생 데이터 삭제 요청.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |
| X-CSRF-Token | string | Y | 상태 변경 요청 위조 방지 토큰 |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
|  |  | object | N |  값 |

### Request Body

요청 본문 없음. Path 또는 Query 값만 사용합니다.

### 성공 Response

HTTP `202` — 비동기 삭제 요청 접수


#### 응답 JSON 예시

```json
{
  "data": {
    "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "status": "QUEUED",
    "createdAt": "2026-08-19T06:20:00Z"
  }
}
```

#### 응답 필드 설명

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| data | object | Y | 응답에 포함되는 data 값 |
| data.id | uuid | Y | 응답에 포함되는 id 값 |
| data.status | `QUEUED` \| `RUNNING` \| `SUCCEEDED` \| `FAILED` | Y | 응답에 포함되는 status 값 |
| data.createdAt | date-time | Y | 응답에 포함되는 createdAt 값 |

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 403 | CSRF 검증 실패 또는 접근 권한 없음 |
| 404 | 리소스를 찾을 수 없음 |

---

## 11. 카카오톡 대화 내보내기 파일 업로드

`POST /api/v1/relationships/{relationshipId}/conversation-files`

### 어떤 API인가요?

카카오톡 대화 내보내기 파일 업로드.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |
| X-CSRF-Token | string | Y | 상태 변경 요청 위조 방지 토큰 |
| Idempotency-Key | string | N | 중복 생성 방지를 위한 멱등 키 |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
|  |  | object | N |  값 |

### Request Body

요청 형식: `multipart/form-data`


| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| file | binary | Y | .txt, 최대 50MB |
| source | string | Y | 응답에 포함되는 source 값 |

### 성공 Response

HTTP `201` — 업로드 및 형식 검증 완료. 공개 응답에는 원본 또는 정규화 파일의
저장 경로, Presigned URL, 다운로드 자격증명을 포함하지 않는다.



#### 응답 JSON 예시

```json
{
  "data": {
    "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "relationshipId": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "originalFileName": "홍길동",
    "sizeBytes": 1,
    "source": "example",
    "validationStatus": "VALIDATING",
    "messageCount": null,
    "conversationStartedAt": null,
    "conversationEndedAt": null,
    "expiresAt": null,
    "uploadedAt": "2026-08-19T06:20:00Z"
  }
}
```

#### 응답 필드 설명

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| data | object | Y | 응답에 포함되는 data 값 |
| data.id | uuid | Y | 응답에 포함되는 id 값 |
| data.relationshipId | uuid | Y | 응답에 포함되는 relationshipId 값 |
| data.originalFileName | string | Y | 응답에 포함되는 originalFileName 값 |
| data.sizeBytes | integer | Y | 응답에 포함되는 sizeBytes 값 (1~52428800) |
| data.source | string | Y | 응답에 포함되는 source 값 |
| data.validationStatus | `VALIDATING` \| `VALID` \| `INVALID` | Y | 응답에 포함되는 validationStatus 값 |
| data.messageCount | ["integer", "null"] | N | 응답에 포함되는 messageCount 값 |
| data.conversationStartedAt | date-time | N | 응답에 포함되는 conversationStartedAt 값 |
| data.conversationEndedAt | date-time | N | 응답에 포함되는 conversationEndedAt 값 |
| data.expiresAt | date-time | N | 응답에 포함되는 expiresAt 값 |
| data.uploadedAt | date-time | Y | 응답에 포함되는 uploadedAt 값 |

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 403 | CSRF 검증 실패 또는 접근 권한 없음 |
| 404 | 리소스를 찾을 수 없음 |
| 409 | 리소스 상태 또는 멱등성 충돌 |
| 413 | 50MB 초과 |
| 415 | 지원하지 않는 파일 형식 |
| 422 | 의미 검증 실패 |

---

## 12. 업로드 파일 검증 상태 조회

`GET /api/v1/conversation-files/{fileId}`

### 어떤 API인가요?

업로드 파일 검증 상태 조회.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
|  |  | object | N |  값 |

### Request Body

요청 본문 없음. Path 또는 Query 값만 사용합니다.

### 성공 Response

HTTP `200` — 파일 메타데이터. 원문 다운로드 URL은 제공하지 않는다.


#### 응답 JSON 예시

```json
{
  "data": {
    "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "relationshipId": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "originalFileName": "홍길동",
    "sizeBytes": 1,
    "source": "example",
    "validationStatus": "VALIDATING",
    "messageCount": null,
    "conversationStartedAt": null,
    "conversationEndedAt": null,
    "expiresAt": null,
    "uploadedAt": "2026-08-19T06:20:00Z"
  }
}
```

#### 응답 필드 설명

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| data | object | Y | 응답에 포함되는 data 값 |
| data.id | uuid | Y | 응답에 포함되는 id 값 |
| data.relationshipId | uuid | Y | 응답에 포함되는 relationshipId 값 |
| data.originalFileName | string | Y | 응답에 포함되는 originalFileName 값 |
| data.sizeBytes | integer | Y | 응답에 포함되는 sizeBytes 값 (1~52428800) |
| data.source | string | Y | 응답에 포함되는 source 값 |
| data.validationStatus | `VALIDATING` \| `VALID` \| `INVALID` | Y | 응답에 포함되는 validationStatus 값 |
| data.messageCount | ["integer", "null"] | N | 응답에 포함되는 messageCount 값 |
| data.conversationStartedAt | date-time | N | 응답에 포함되는 conversationStartedAt 값 |
| data.conversationEndedAt | date-time | N | 응답에 포함되는 conversationEndedAt 값 |
| data.expiresAt | date-time | N | 응답에 포함되는 expiresAt 값 |
| data.uploadedAt | date-time | Y | 응답에 포함되는 uploadedAt 값 |

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 404 | 리소스를 찾을 수 없음 |

---

## 13. 업로드 파일 삭제

`DELETE /api/v1/conversation-files/{fileId}`

### 어떤 API인가요?

업로드 파일 삭제.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |
| X-CSRF-Token | string | Y | 상태 변경 요청 위조 방지 토큰 |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
|  |  | object | N |  값 |

### Request Body

요청 본문 없음. Path 또는 Query 값만 사용합니다.

### 성공 Response

HTTP `204` — 삭제 완료


응답 본문 없음.

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 403 | CSRF 검증 실패 또는 접근 권한 없음 |
| 404 | 리소스를 찾을 수 없음 |
| 409 | 리소스 상태 또는 멱등성 충돌 |

---

## 14. 1~7점 주관적 체크인 저장

`POST /api/v1/relationships/{relationshipId}/check-ins`

### 어떤 API인가요?

1~7점 주관적 체크인 저장.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |
| X-CSRF-Token | string | Y | 상태 변경 요청 위조 방지 토큰 |
| Idempotency-Key | string | N | 중복 생성 방지를 위한 멱등 키 |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
|  |  | object | N |  값 |

### Request Body

요청 형식: `application/json`


| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| answers | array<object> | Y | 응답에 포함되는 answers 값 |

#### 요청 JSON 예시

```json
{
  "answers": [
    {
      "questionCode": "RELATIONSHIP_FEELING",
      "score": 1
    }
  ]
}
```

### 성공 Response

HTTP `201` — 체크인 저장 완료


#### 응답 JSON 예시

```json
{
  "data": {
    "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "relationshipId": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "answers": [
      {
        "questionCode": "RELATIONSHIP_FEELING",
        "score": 1
      }
    ],
    "weekStart": "2026-08-17",
    "createdAt": "2026-08-19T06:20:00Z"
  }
}
```

#### 응답 필드 설명

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| data | object | Y | 응답에 포함되는 data 값 |
| data.id | uuid | Y | 응답에 포함되는 id 값 |
| data.relationshipId | uuid | Y | 응답에 포함되는 relationshipId 값 |
| data.answers | array<object> | Y | 응답에 포함되는 answers 값 |
| data.weekStart | date | Y | 응답에 포함되는 weekStart 값 |
| data.createdAt | date-time | Y | 응답에 포함되는 createdAt 값 |

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 400 | 잘못된 요청 |
| 403 | CSRF 검증 실패 또는 접근 권한 없음 |
| 404 | 리소스를 찾을 수 없음 |
| 422 | 의미 검증 실패 |

---

## 15. 대화 분석 Job 시작

`POST /api/v1/relationships/{relationshipId}/analyses`

### 어떤 API인가요?

백엔드가 Job을 생성하고 Queue에 등록한 뒤 즉시 202를 반환한다.
Backend Worker는 정규화 대화 파일 참조로 AI 서버를 동기 호출한다.
checkInId는 분석 시점 연결용 메타데이터이며 MVP 점수 계산이나 AI 입력에
사용하지 않는다. Queue 등록 자체가 불가능한 경우에만 요청 단계에서
503 ANALYSIS_UNAVAILABLE을 반환할 수 있다.
.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |
| X-CSRF-Token | string | Y | 상태 변경 요청 위조 방지 토큰 |
| Idempotency-Key | string | N | 중복 생성 방지를 위한 멱등 키 |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
|  |  | object | N |  값 |
|  |  | object | N |  값 |

### Request Body

요청 형식: `application/json`


| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| conversationFileId | uuid | Y | 응답에 포함되는 conversationFileId 값 |
| checkInId | uuid | Y | 분석 당시 체크인 연결용 메타데이터. MVP AI 입력 및 점수 산식에서는 제외한다. |

#### 요청 JSON 예시

```json
{
  "conversationFileId": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
  "checkInId": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281"
}
```

### 성공 Response

HTTP `202` — 분석 작업 접수


#### 응답 JSON 예시

```json
{
  "data": {
    "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "relationshipId": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "status": "QUEUED",
    "stage": "LOADING_CONVERSATION",
    "progress": 0,
    "estimatedSecondsRemaining": null,
    "reportId": null,
    "failure": {
      "code": "example",
      "message": "요청 처리에 필요한 예시 값입니다.",
      "retryable": false
    },
    "createdAt": "2026-08-19T06:20:00Z",
    "updatedAt": "2026-08-19T06:20:00Z"
  }
}
```

#### 응답 필드 설명

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| data | object | Y | 응답에 포함되는 data 값 |
| data.id | uuid | Y | 응답에 포함되는 id 값 |
| data.relationshipId | uuid | Y | 응답에 포함되는 relationshipId 값 |
| data.status | `QUEUED` \| `RUNNING` \| `SUCCEEDED` \| `FAILED` \| `CANCELED` | Y | 응답에 포함되는 status 값 |
| data.stage | `LOADING_CONVERSATION` \| `ANALYZING_MESSAGE_PATTERNS` \| `ANALYZING_EMOTIONAL_FLOW` \| `CALCULATING_PRQC` \| `CALCULATING_RELATIONSHIP_SCORE` | Y | 응답에 포함되는 stage 값 |
| data.progress | integer | Y | 백엔드 오케스트레이션 기준의 UI 표시용 예상 진행률. AI 응답 전 최대 90, 리포트 저장 중 최대 95, status=SUCCEEDED인 경우에만 100이다.  (0~100) |
| data.estimatedSecondsRemaining | ["integer", "null"] | N | 정확히 추정할 수 없으면 null |
| data.reportId | uuid | Y | 응답에 포함되는 reportId 값 |
| data.failure | object | Y | 응답에 포함되는 failure 값 |
| data.createdAt | date-time | Y | 응답에 포함되는 createdAt 값 |
| data.updatedAt | date-time | Y | 응답에 포함되는 updatedAt 값 |

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 403 | CSRF 검증 실패 또는 접근 권한 없음 |
| 404 | 리소스를 찾을 수 없음 |
| 409 | 리소스 상태 또는 멱등성 충돌 |
| 422 | 의미 검증 실패 |

---

## 16. 분석 진행률 및 완료 결과 조회

`GET /api/v1/analysis-jobs/{jobId}`

### 어떤 API인가요?

stage와 progress는 AI 서버가 보고한 실제 내부 진행률이 아니라 백엔드가
파일 준비, AI 호출, 결과 저장 상태를 기준으로 계산한 UI 표시용 예상값이다.
AI 응답 전에는 최대 90, 리포트 저장 중에는 최대 95로 제한하며 리포트가
저장되고 SUCCEEDED가 된 경우에만 100을 반환한다. 접수 후 내부 AI 호출이
실패해도 이 API는 200과 status=FAILED, failure 객체를 반환한다.
.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
|  |  | object | N |  값 |

### Request Body

요청 본문 없음. Path 또는 Query 값만 사용합니다.

### 성공 Response

HTTP `200` — 분석 Job


#### 응답 JSON 예시

```json
{
  "data": {
    "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "relationshipId": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "status": "QUEUED",
    "stage": "LOADING_CONVERSATION",
    "progress": 0,
    "estimatedSecondsRemaining": null,
    "reportId": null,
    "failure": {
      "code": "example",
      "message": "요청 처리에 필요한 예시 값입니다.",
      "retryable": false
    },
    "createdAt": "2026-08-19T06:20:00Z",
    "updatedAt": "2026-08-19T06:20:00Z"
  }
}
```

#### 응답 필드 설명

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| data | object | Y | 응답에 포함되는 data 값 |
| data.id | uuid | Y | 응답에 포함되는 id 값 |
| data.relationshipId | uuid | Y | 응답에 포함되는 relationshipId 값 |
| data.status | `QUEUED` \| `RUNNING` \| `SUCCEEDED` \| `FAILED` \| `CANCELED` | Y | 응답에 포함되는 status 값 |
| data.stage | `LOADING_CONVERSATION` \| `ANALYZING_MESSAGE_PATTERNS` \| `ANALYZING_EMOTIONAL_FLOW` \| `CALCULATING_PRQC` \| `CALCULATING_RELATIONSHIP_SCORE` | Y | 응답에 포함되는 stage 값 |
| data.progress | integer | Y | 백엔드 오케스트레이션 기준의 UI 표시용 예상 진행률. AI 응답 전 최대 90, 리포트 저장 중 최대 95, status=SUCCEEDED인 경우에만 100이다.  (0~100) |
| data.estimatedSecondsRemaining | ["integer", "null"] | N | 정확히 추정할 수 없으면 null |
| data.reportId | uuid | Y | 응답에 포함되는 reportId 값 |
| data.failure | object | Y | 응답에 포함되는 failure 값 |
| data.createdAt | date-time | Y | 응답에 포함되는 createdAt 값 |
| data.updatedAt | date-time | Y | 응답에 포함되는 updatedAt 값 |

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 401 | 로그인 필요 또는 세션 만료 |
| 404 | 리소스를 찾을 수 없음 |

---

## 17. 인물별 PRQC 리포트 조회

`GET /api/v1/relationships/{relationshipId}/report`

### 어떤 API인가요?

인물별 PRQC 리포트 조회.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
|  |  | object | N |  값 |
| weeks | query | integer | N | weeks 값 |

### Request Body

요청 본문 없음. Path 또는 Query 값만 사용합니다.

### 성공 Response

HTTP `200` — 종합점수, PRQC, 근거, 추이


#### 응답 JSON 예시

```json
{
  "data": {
    "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "relationship": {
      "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
      "name": "홍길동",
      "initial": "example",
      "relationshipType": "ROMANTIC_PARTNER"
    },
    "overall": {
      "score": 0,
      "change": null,
      "statusCode": "HEALTHY",
      "statusLabel": "example"
    },
    "prqc": {
      "satisfaction": 0,
      "commitment": 0,
      "intimacy": 0,
      "trust": 0,
      "passion": 0,
      "love": 0
    },
    "evidences": [
      {
        "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
        "component": "satisfaction",
        "score": 0,
        "summary": "example",
        "metric": {
          "name": "홍길동",
          "currentValue": 1,
          "previousValue": null,
          "unit": "example",
          "period": "example"
        }
      }
    ],
    "trend": [
      {
        "weekStart": "2026-08-17",
        "label": "example",
        "score": 0
      }
    ],
    "analyzedAt": "2026-08-19T06:20:00Z",
    "modelVersion": "prqc-2026-08-19.1",
    "scoringPolicyVersion": "relationship-temperature-1.0.0",
    "selfReportComparison": "체크인에서 느낀 관계와 대화에서 관찰된 신호를 함께 비교한 설명입니다.",
    "disclaimer": "example"
  }
}
```

#### 응답 필드 설명

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| data | object | Y | 응답에 포함되는 data 값 |
| data.id | uuid | Y | 응답에 포함되는 id 값 |
| data.relationship | object | Y | 응답에 포함되는 relationship 값 |
| data.relationship.id | uuid | Y | 응답에 포함되는 id 값 |
| data.relationship.name | string | Y | 응답에 포함되는 name 값 (최대 50자) |
| data.relationship.initial | string | Y | 응답에 포함되는 initial 값 (최대 2자) |
| data.relationship.relationshipType | `ROMANTIC_PARTNER` \| `FRIEND` \| `FAMILY` \| `COWORKER` \| `OTHER` | Y | 응답에 포함되는 relationshipType 값 |
| data.overall | object | Y | AI PRQC 결과에 백엔드의 버전된 관계 유형별 가중치를 적용한 canonical 종합점수 |
| data.overall.score | integer | Y | 응답에 포함되는 score 값 |
| data.overall.change | ["integer", "null"] | Y | 응답에 포함되는 change 값 |
| data.overall.statusCode | `HEALTHY` \| `GOOD` \| `NEEDS_ATTENTION` \| `CHANGE_DETECTED` | Y | 응답에 포함되는 statusCode 값 |
| data.overall.statusLabel | string | Y | 응답에 포함되는 statusLabel 값 |
| data.prqc | object | Y | AI 서버가 대화 데이터에서 산출한 PRQC 6개 구성요소 점수 |
| data.prqc.satisfaction | integer | Y | 응답에 포함되는 satisfaction 값 |
| data.prqc.commitment | integer | Y | 응답에 포함되는 commitment 값 |
| data.prqc.intimacy | integer | Y | 응답에 포함되는 intimacy 값 |
| data.prqc.trust | integer | Y | 응답에 포함되는 trust 값 |
| data.prqc.passion | integer | Y | 응답에 포함되는 passion 값 |
| data.prqc.love | integer | Y | 응답에 포함되는 love 값 |
| data.evidences | array<object> | Y | 응답에 포함되는 evidences 값 |
| data.trend | array<object> | Y | 응답에 포함되는 trend 값 |
| data.analyzedAt | date-time | Y | 응답에 포함되는 analyzedAt 값 |
| data.modelVersion | string | Y | AI PRQC 분석 모델 버전 |
| data.scoringPolicyVersion | string | Y | 백엔드의 관계 유형별 종합점수 가중치 정책 버전 |
| data.selfReportComparison | string | Y | 사용자의 체크인 자기보고와 대화 기반 PRQC 분석에서 관찰된 신호의 일치·차이 설명 |
| data.disclaimer | string | Y | 응답에 포함되는 disclaimer 값 |

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 401 | 로그인 필요 또는 세션 만료 |
| 404 | 리소스를 찾을 수 없음 |
| 409 | 완료된 리포트가 없음 |

---

## 18. 최근 상담방 목록 조회

`GET /api/v1/consultations`

### 어떤 API인가요?

최근 상담방 목록 조회.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
|  |  | object | N |  값 |
|  |  | object | N |  값 |

### Request Body

요청 본문 없음. Path 또는 Query 값만 사용합니다.

### 성공 Response

HTTP `200` — 상담방 목록


#### 응답 JSON 예시

```json
{
  "data": [
    {
      "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
      "relationship": {
        "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
        "name": "홍길동",
        "initial": "example",
        "relationshipType": "ROMANTIC_PARTNER"
      },
      "lastMessagePreview": null,
      "lastMessageAt": null,
      "unreadCount": 0
    }
  ],
  "meta": {
    "nextCursor": null,
    "hasNext": false
  }
}
```

#### 응답 필드 설명

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| data | array<object> | Y | 응답에 포함되는 data 값 |
| meta | object | Y | 응답에 포함되는 meta 값 |
| meta.nextCursor | ["string", "null"] | Y | 응답에 포함되는 nextCursor 값 |
| meta.hasNext | boolean | Y | 응답에 포함되는 hasNext 값 |

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 401 | 로그인 필요 또는 세션 만료 |

---

## 19. 새 AI 상담 시작

`POST /api/v1/consultations`

### 어떤 API인가요?

새 AI 상담 시작.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |
| X-CSRF-Token | string | Y | 상태 변경 요청 위조 방지 토큰 |
| Idempotency-Key | string | N | 중복 생성 방지를 위한 멱등 키 |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
|  |  | object | N |  값 |

### Request Body

요청 형식: `application/json`


| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| relationshipId | uuid | Y | 응답에 포함되는 relationshipId 값 |

#### 요청 JSON 예시

```json
{
  "relationshipId": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281"
}
```

### 성공 Response

HTTP `201` — 상담방과 첫 AI 메시지 생성


#### 응답 JSON 예시

```json
{
  "data": {
    "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "relationship": {
      "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
      "name": "홍길동",
      "initial": "example",
      "relationshipType": "ROMANTIC_PARTNER"
    },
    "lastMessagePreview": null,
    "lastMessageAt": null,
    "unreadCount": 0
  }
}
```

#### 응답 필드 설명

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| data | object | Y | 응답에 포함되는 data 값 |

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 403 | CSRF 검증 실패 또는 접근 권한 없음 |
| 404 | 리소스를 찾을 수 없음 |
| 409 | 리소스 상태 또는 멱등성 충돌 |

---

## 20. 상담방 상세 조회

`GET /api/v1/consultations/{consultationId}`

### 어떤 API인가요?

상담방 상세 조회.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
|  |  | object | N |  값 |

### Request Body

요청 본문 없음. Path 또는 Query 값만 사용합니다.

### 성공 Response

HTTP `200` — 상담방 상세


#### 응답 JSON 예시

```json
{
  "data": {
    "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
    "relationship": {
      "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
      "name": "홍길동",
      "initial": "example",
      "relationshipType": "ROMANTIC_PARTNER"
    },
    "lastMessagePreview": null,
    "lastMessageAt": null,
    "unreadCount": 0
  }
}
```

#### 응답 필드 설명

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| data | object | Y | 응답에 포함되는 data 값 |

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 404 | 리소스를 찾을 수 없음 |

---

## 21. 상담방과 메시지 삭제

`DELETE /api/v1/consultations/{consultationId}`

### 어떤 API인가요?

상담방과 메시지 삭제.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |
| X-CSRF-Token | string | Y | 상태 변경 요청 위조 방지 토큰 |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
|  |  | object | N |  값 |

### Request Body

요청 본문 없음. Path 또는 Query 값만 사용합니다.

### 성공 Response

HTTP `204` — 삭제 완료


응답 본문 없음.

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 403 | CSRF 검증 실패 또는 접근 권한 없음 |
| 404 | 리소스를 찾을 수 없음 |

---

## 22. 상담 메시지 이력 조회

`GET /api/v1/consultations/{consultationId}/messages`

### 어떤 API인가요?

상담 메시지 이력 조회.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
|  |  | object | N |  값 |
| before | query | uuid | N | 이 메시지 이전의 더 오래된 메시지 조회 |
| limit | query | integer | N | limit 값 |

### Request Body

요청 본문 없음. Path 또는 Query 값만 사용합니다.

### 성공 Response

HTTP `200` — 메시지 목록. 서버는 최신순으로 반환하고 클라이언트는 시간순 표시 가능.


#### 응답 JSON 예시

```json
{
  "data": [
    {
      "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
      "role": "USER",
      "content": "example",
      "status": "GENERATING",
      "evidenceRefs": [
        {
          "evidenceId": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
          "label": "example"
        }
      ],
      "safetyNotice": {
        "type": "SUPPORT_RECOMMENDATION",
        "title": "example",
        "message": "요청 처리에 필요한 예시 값입니다.",
        "resourceQuery": {
          "category": "MENTAL_HEALTH_COUNSELING",
          "region": "example"
        }
      },
      "createdAt": "2026-08-19T06:20:00Z"
    }
  ],
  "meta": {
    "nextCursor": null,
    "hasNext": false
  }
}
```

#### 응답 필드 설명

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| data | array<object> | Y | 응답에 포함되는 data 값 |
| meta | object | Y | 응답에 포함되는 meta 값 |
| meta.nextCursor | ["string", "null"] | Y | 응답에 포함되는 nextCursor 값 |
| meta.hasNext | boolean | Y | 응답에 포함되는 hasNext 값 |

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 404 | 리소스를 찾을 수 없음 |

---

## 23. 사용자 메시지 저장 및 AI 답변 생성 시작

`POST /api/v1/consultations/{consultationId}/messages`

### 어떤 API인가요?

사용자 메시지 저장 및 AI 답변 생성 시작.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |
| X-CSRF-Token | string | Y | 상태 변경 요청 위조 방지 토큰 |
| Idempotency-Key | string | N | 중복 생성 방지를 위한 멱등 키 |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
|  |  | object | N |  값 |
|  |  | object | N |  값 |

### Request Body

요청 형식: `application/json`


| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| content | string | Y | 응답에 포함되는 content 값 (최대 4000자) |

#### 요청 JSON 예시

```json
{
  "content": "example"
}
```

### 성공 Response

HTTP `202` — 사용자 메시지와 생성 중 AI 메시지


#### 응답 JSON 예시

```json
{
  "data": {
    "userMessage": {
      "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
      "role": "USER",
      "content": "example",
      "status": "GENERATING",
      "evidenceRefs": [
        {
          "evidenceId": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
          "label": "example"
        }
      ],
      "safetyNotice": {
        "type": "SUPPORT_RECOMMENDATION",
        "title": "example",
        "message": "요청 처리에 필요한 예시 값입니다.",
        "resourceQuery": {
          "category": "MENTAL_HEALTH_COUNSELING",
          "region": "example"
        }
      },
      "createdAt": "2026-08-19T06:20:00Z"
    },
    "assistantMessage": {
      "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
      "role": "USER",
      "content": "example",
      "status": "GENERATING",
      "evidenceRefs": [
        {
          "evidenceId": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
          "label": "example"
        }
      ],
      "safetyNotice": {
        "type": "SUPPORT_RECOMMENDATION",
        "title": "example",
        "message": "요청 처리에 필요한 예시 값입니다.",
        "resourceQuery": {
          "category": "MENTAL_HEALTH_COUNSELING",
          "region": "example"
        }
      },
      "createdAt": "2026-08-19T06:20:00Z"
    },
    "streamUrl": "https://example.com/resource"
  }
}
```

#### 응답 필드 설명

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| data | object | Y | 응답에 포함되는 data 값 |
| data.userMessage | object | Y | 응답에 포함되는 userMessage 값 |
| data.userMessage.id | uuid | Y | 응답에 포함되는 id 값 |
| data.userMessage.role | `USER` \| `ASSISTANT` | Y | 응답에 포함되는 role 값 |
| data.userMessage.content | string | Y | 응답에 포함되는 content 값 (최대 20000자) |
| data.userMessage.status | `GENERATING` \| `COMPLETED` \| `FAILED` | Y | 응답에 포함되는 status 값 |
| data.userMessage.evidenceRefs | array<object> | N | 응답에 포함되는 evidenceRefs 값 |
| data.userMessage.safetyNotice | object | N | 응답에 포함되는 safetyNotice 값 |
| data.userMessage.createdAt | date-time | Y | 응답에 포함되는 createdAt 값 |
| data.assistantMessage | object | Y | 응답에 포함되는 assistantMessage 값 |
| data.assistantMessage.id | uuid | Y | 응답에 포함되는 id 값 |
| data.assistantMessage.role | `USER` \| `ASSISTANT` | Y | 응답에 포함되는 role 값 |
| data.assistantMessage.content | string | Y | 응답에 포함되는 content 값 (최대 20000자) |
| data.assistantMessage.status | `GENERATING` \| `COMPLETED` \| `FAILED` | Y | 응답에 포함되는 status 값 |
| data.assistantMessage.evidenceRefs | array<object> | N | 응답에 포함되는 evidenceRefs 값 |
| data.assistantMessage.safetyNotice | object | N | 응답에 포함되는 safetyNotice 값 |
| data.assistantMessage.createdAt | date-time | Y | 응답에 포함되는 createdAt 값 |
| data.streamUrl | string | Y | 응답에 포함되는 streamUrl 값 |

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 400 | 잘못된 요청 |
| 403 | CSRF 검증 실패 또는 접근 권한 없음 |
| 404 | 리소스를 찾을 수 없음 |
| 409 | 리소스 상태 또는 멱등성 충돌 |
| 429 | 호출 한도 초과 |
| 503 | 분석 또는 AI 공급자 일시 장애 |

---

## 24. AI 답변 Server-Sent Events 스트림

`GET /api/v1/consultations/{consultationId}/events`

### 어떤 API인가요?

AI 답변 Server-Sent Events 스트림.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
|  |  | object | N |  값 |
| after | query | uuid | N | 이 사용자 메시지 이후 생성 이벤트 |
| Last-Event-ID | header | string | N | SSE 재연결 시 마지막으로 처리한 이벤트 ID |

### Request Body

요청 본문 없음. Path 또는 Query 값만 사용합니다.

### 성공 Response

HTTP `200` — assistant.started, assistant.delta, assistant.completed,
assistant.failed, heartbeat 이벤트를 전송한다.



#### 응답 JSON 예시

```json
"example"
```

#### 응답 필드 설명

해당 없음

### 오류 Response

| HTTP | 발생 조건 |
|---|---|
| 404 | 리소스를 찾을 수 없음 |

---

## 25. 검증된 전문상담 및 지원 리소스 조회

`GET /api/v1/support-resources`

### 어떤 API인가요?

검증된 전문상담 및 지원 리소스 조회.

### 인증 및 주요 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Cookie | string | Y | 로그인 세션 `rt_session` |

### Path·Query 요청값

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| region | query | string | N | region 값 |
| category | query | `MENTAL_HEALTH_COUNSELING` \| `RELATIONSHIP_COUNSELING` \| `CRISIS_SUPPORT` | N | category 값 |

### Request Body

요청 본문 없음. Path 또는 Query 값만 사용합니다.

### 성공 Response

HTTP `200` — 검수된 지원 리소스


#### 응답 JSON 예시

```json
{
  "data": [
    {
      "id": "0198c8a7-7f49-7a35-b7a7-8e81b4db0281",
      "name": "홍길동",
      "description": "example",
      "category": "MENTAL_HEALTH_COUNSELING",
      "region": "example",
      "url": null,
      "phone": null,
      "hours": null,
      "verifiedAt": "2026-08-19T06:20:00Z",
      "source": "example"
    }
  ]
}
```

#### 응답 필드 설명

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| data | array<object> | Y | 응답에 포함되는 data 값 |

### 오류 Response

해당 없음

---
