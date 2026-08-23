# 관계온도 Backend

관계온도 공개 API와 백엔드-AI 내부 계약을 구현하기 위한 Spring Boot 모듈형 모놀리스 뼈대다. 두 명이 하나의 실행 프로젝트에서 기능 패키지를 나눠 개발할 수 있도록 도메인별로 경계를 분리했다.

## 기술 기준

- Java 21 toolchain
- Spring Boot 4.1.0
- Gradle Wrapper 9.5.1
- Spring MVC, Security, OAuth2 Client, Session JDBC
- Spring Data JPA, Flyway, Spring Data MongoDB
- PostgreSQL 운영 DB / H2 로컬·테스트 DB / MongoDB 상담 데이터 저장소
- `RestClient` 기반 AI 내부 호출
- `SseEmitter` 기반 AI 상담 스트리밍

## 실행

Java 21이 설치되어 있어야 한다.

```bash
./gradlew test
./gradlew bootRun
```

기본 프로필은 별도 DB 없이 H2 메모리 DB와 AI stub을 사용한다.

```text
Health: http://localhost:8080/actuator/health
Kakao login: http://localhost:8080/api/v1/auth/kakao/authorize
```

PostgreSQL과 MongoDB 실행:

compose.yaml은 저장소 루트에 있다.

```bash
(cd .. && docker compose up -d postgres mongo)
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

전체 실행 방법은 [루트 README](../README.md)를 참고한다.

운영 환경값은 [`.env.example`](./.env.example)을 기준으로 설정한다. 실제 비밀값을 저장소에 커밋하지 않는다.

## 패키지 구조

```text
com.relationshiptemperature.api
├── auth          카카오 OAuth, 사용자, 현재 세션
├── relationship  인물·관계 CRUD와 상태
├── conversation  대화 파일, 저장소 Port, 카카오 파서
├── checkin       주차별 1~7점 체크인
├── analysis      비동기 Job, AI Port/Adapter, 재시도
├── report        PRQC, 근거, 종합점수 정책
├── dashboard     주간 집계
├── consultation  MongoDB 상담방·메시지, 리포트 컨텍스트, SSE
├── support       검수된 상담 리소스
├── retention     원본 대화 만료 삭제
├── common        공통 응답, 오류, 요청 ID, JPA 기반
└── config        Security, Async, JPA, 환경 설정
```

패키지는 서로의 `domain` 또는 공개 `application` 인터페이스를 통해서만 호출한다. 다른 기능의 `repository`를 직접 사용하는 코드는 현재 뼈대 이후 점진적으로 application service 뒤로 이동한다.

## 두 명 작업 분담 권장안

### 담당 A: 플랫폼·인증·데이터 수집

- `config`, `common`
- `auth`
- `relationship`
- `conversation`
- `checkin`
- `retention`
- 카카오 OAuth 실제 검증
- S3/호환 Object Storage 어댑터와 Presigned URL 발급
- 카카오톡 내보내기 실제 파서
- 사용자 소유권 및 삭제 전파

### 담당 B: 분석·리포트·상담

- `analysis`
- `report`
- `dashboard`
- `consultation`
- `support`
- AI 내부 API 어댑터
- 관계 유형별 점수 가중치
- 대시보드 쿼리 최적화
- SSE 재연결·이벤트 저장
- AI 안전 제안 구조화

### 충돌 방지 규칙

1. 공개 API 변경은 먼저 [`docs/openapi.yaml`](./docs/openapi.yaml)을 합의한다.
2. AI 내부 계약 변경은 [`docs/openapi-ai-internal.yaml`](./docs/openapi-ai-internal.yaml)을 합의한다.
3. 기존 Flyway 파일은 수정하지 않고 각자 새 버전 파일을 추가한다.
4. 공통 엔티티 필드 변경은 PR을 먼저 합친 뒤 양쪽 기능 브랜치를 rebase한다.
5. `common`에 기능별 로직을 넣지 않는다.
6. 외부 연동은 interface(Port)를 유지하고 Adapter만 교체한다.

## 현재 구현된 뼈대

- 공통 `ApiResponse`, cursor 응답 구조, 오류 응답, Bean Validation, 요청 ID/MDC
- H2/PostgreSQL 공용 Flyway 초기 스키마
- 카카오 OAuth2 사용자 최초 저장 및 세션 로그인 구조
- HttpSession CSRF 토큰을 `/users/me`에서 반환
- 관계 CRUD와 상태 전이
- 50MB `.txt` 업로드, 로컬 저장 Port, SHA-256, parser 교체 지점
- 주차별 체크인 upsert
- 트랜잭션 커밋 후 비동기 분석 Event/Worker
- 공개 Job 예상 진행률과 AI HTTP 재시도 골격
- AI stub 및 HTTP Adapter 경계
- AI PRQC와 백엔드 종합점수 책임 분리
- 리포트·근거·추이 API
- 대시보드 집계 API
- MongoDB 상담방·메시지 저장, 리포트 컨텍스트 기반 답변, SSE 증분·재연결 복구
- 지원 리소스 조회
- 원본 파일 만료 삭제 시 메타데이터·리포트를 보존하는 retention Job

## 반드시 채워야 할 구현

코드의 `TODO(owner)`를 검색하면 교체 지점을 확인할 수 있다.

```bash
rg 'TODO\(' src/main/java
```

핵심 미구현 항목:

- 실제 카카오 내보내기 포맷 파싱과 정규화 NDJSON 생성
- S3/호환 Object Storage 및 다운로드 전용 Presigned URL
- 관계 유형별 PRQC 가중치 확정
- AI 내부 오류 본문 매핑, `Retry-After`와 jitter
- 분산 환경용 Queue 및 Worker 실행 보장
- 분산 환경에서 SSE 중간 이벤트까지 재생해야 할 경우 Redis Streams 등 이벤트 로그 도입
- 운영 AI 모델의 `/internal/v1/consultation-answers` 구현과 프롬프트·안전 정책 확정
- 관계 삭제 시 비동기 삭제 Job 및 모든 외부 저장소 삭제 전파
- 검수된 실제 전문상담 리소스 데이터
- rate limit, 감사 로그, 운영 메트릭과 알림

## API 명세

- [공개 API 상세 명세](./docs/API_SPEC.md)
- [공개 OpenAPI](./docs/openapi.yaml)
- [백엔드-AI 내부 상세 명세](./docs/AI_INTERNAL_API_SPEC.md)
- [백엔드-AI 내부 OpenAPI](./docs/openapi-ai-internal.yaml)
