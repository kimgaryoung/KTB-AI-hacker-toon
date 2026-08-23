# Woo 담당 기능 구현 진행 기록

이 문서는 Woo 담당 백엔드 기능을 항목 단위로 구현하고 검증한 결과를 누적 기록한다.
각 항목은 구현 완료 후 검증 결과와 수동 확인 방법까지 함께 갱신한다.

## 1. 인물·관계 관리 기능

- 상태: 완료
- 기준 커밋: `acb20c6` (`인물·관계 관리 기능 구현`)
- API:
  - 관계 등록: `POST /api/v1/relationships`
  - 관계 목록·검색: `GET /api/v1/relationships`
  - 관계 상세: `GET /api/v1/relationships/{relationshipId}`
  - 관계 수정: `PATCH /api/v1/relationships/{relationshipId}`
  - 관계 삭제: `DELETE /api/v1/relationships/{relationshipId}`
- 구현 내용:
  - 관계 이름과 관계 유형 CRUD
  - 로그인 사용자 기준 소유권 격리
  - 이름 검색, 상태 필터, 정렬
  - `DRAFT`, `ANALYZING`, `ACTIVE`, `ANALYSIS_FAILED`, `DELETING` 상태 관리
  - Bean Validation, 공통 오류 응답, CSRF 보호 적용
- 검증:
  - 도메인·서비스·컨트롤러 테스트 통과
- 연동 메모:
  - 분석 파이프라인이 시작·완료·실패할 때 관계 상태를 전이해야 한다.
  - 파생 데이터가 추가된 뒤 관계 삭제 정책은 해당 데이터와 함께 재검토한다.

## 2. 관계 체크인 저장 기능

- 상태: 완료
- API:
  - 주차 체크인 저장·갱신: `POST /api/v1/relationships/{relationshipId}/check-ins`
  - 관계별·주차별 이력: `GET /api/v1/relationships/{relationshipId}/check-ins`
- 구현 내용:
  - `RELATIONSHIP_FEELING`, `CONVERSATION_COMFORT` 두 문항의 1~7점 응답 저장
  - `check_ins`와 `check_in_answers`로 체크인 헤더와 문항 응답 정규화
  - 사용자 타임존의 제출일을 기준으로 월요일 `weekStart` 산출
  - 관계·주차 유일성 보장 및 같은 주 재제출 시 기존 응답 갱신
  - `from`, `to` 경곗값을 포함하는 기간 조회와 최신 주차 우선 정렬
  - 관계 소유권 격리, 쓰기 요청 CSRF 검증
  - 질문 누락·중복과 1~7점 범위 검증
  - Flyway V2에서 기존 체크인 점수 컬럼을 문항별 응답 행으로 이관한 뒤 제거
- 자동 검증:
  - 체크인 관련 테스트 8건 통과
  - 프로젝트 전체 회귀 테스트 20건 통과 (`failures=0`, `errors=0`, `skipped=0`)
  - 체크인 엔티티 점수 검증 테스트
  - 최초 생성과 같은 주 갱신 테스트
  - 주차 이력 최신순·기간 필터 테스트
  - 질문 누락·중복·점수 범위 테스트
  - 관계 소유권·CSRF 테스트
  - 역전된 기간 범위 테스트
  - V1 기존 데이터의 V2 마이그레이션 테스트
- Postman 확인:
  1. 로그인 세션 쿠키를 유지하고 `GET /api/v1/users/me` 응답의 CSRF 토큰을 준비한다.
  2. 관계 ID로 체크인을 최초 제출해 `201 Created`와 두 응답을 확인한다.
  3. 같은 주에 점수를 바꿔 다시 제출해 `200 OK`와 동일한 체크인 ID를 확인한다.
  4. 이력 API의 최신순 정렬과 `from`, `to` 기간 필터를 확인한다.
  5. 점수 범위 위반, 질문 누락·중복, CSRF 누락, 다른 사용자의 관계 접근 오류를 확인한다.

## 3. 관계 리포트 및 분석 근거 생성

- 상태: 완료
- API:
  - 최신 관계 리포트·주차 추이 조회: `GET /api/v1/relationships/{relationshipId}/report?weeks=8`
- 구현 내용:
  - AI PRQC 6개 항목(`satisfaction`, `commitment`, `intimacy`, `trust`, `passion`, `love`) 저장
  - 버전된 백엔드 점수 정책으로 canonical 종합점수 계산·저장
  - 분석에 연결된 체크인의 월요일 `weekStart`를 리포트 주차로 저장
  - 직전 주차의 최신 완료 리포트와 비교하여 전주 대비 변화량 계산
  - 직전 주 리포트가 없을 때 변화량을 `null`로 유지
  - 4주·8주를 포함한 4~52주 추이 조회와 오래된 주차 우선 응답
  - 같은 주에 재분석한 경우 주차별 최신 리포트 한 건만 추이에 포함
  - PRQC 구성요소별 관찰 요약과 구조화 지표(현재값·이전값·단위·기간) 저장
  - 관찰 근거 점수와 해당 PRQC 구성요소 점수의 일치 검증
  - 리포트 상태 코드·라벨·면책문구를 생성 시점 스냅샷으로 저장
  - 동일 분석 Job의 리포트 생성 멱등 처리
  - 관계 소유권 격리 및 완료 리포트가 없을 때 `409 REPORT_REQUIRED` 반환
  - Flyway V3에서 기존 리포트의 주차·상태·라벨·면책문구를 이관하고 근거 구성요소 코드를 정규화
- 자동 검증:
  - 리포트 관련 테스트 10건 통과
  - 프로젝트 전체 회귀 테스트 29건 통과 (`failures=0`, `errors=0`, `skipped=0`)
  - PRQC·종합점수 범위와 상태 매핑 테스트
  - 구조화 관찰 근거·지표 검증 테스트
  - 전주 최신 리포트 대비 변화량 및 저장 멱등성 테스트
  - 4주·8주 범위와 같은 주 최신 리포트 중복 제거 테스트
  - 관계 소유권과 리포트 미생성 상태 테스트
  - V2 기존 리포트의 V3 마이그레이션 테스트
- Postman 확인:
  1. 분석 Job을 완료하여 관계에 리포트가 생성된 상태를 준비한다.
  2. `GET /api/v1/relationships/{relationshipId}/report?weeks=8`로 종합점수·PRQC·근거·추이를 확인한다.
  3. `weeks=4`와 `weeks=8`에서 조회 범위가 달라지는지 확인한다.
  4. 같은 주에 재분석한 후 추이 점이 하나이고 최신 점수로 바뀌는지 확인한다.
  5. 직전 주 분석 후 이번 주 분석을 실행하여 `overall.change`를 확인한다.
  6. 리포트가 없는 관계의 `409 REPORT_REQUIRED`와 다른 사용자 관계의 `404`를 확인한다.

## 4. 메인 대시보드 집계 API

- 상태: 완료
- API:
  - 주간 대시보드 집계: `GET /api/v1/dashboard?weekOf=2026-08-17&sort=ABS_CHANGE_DESC`
- 구현 내용:
  - `weekOf`를 월요일~일요일 주차로 정규화하고 생략 시 사용자 타임존의 현재 주차 사용
  - 선택 주차까지 완료된 관계별 최신 리포트로 카드 스냅샷 구성
  - 선택 주차 이후 미래 리포트 유입 방지
  - 분석 이력이 없는 `DRAFT` 관계와 삭제 중 관계 제외
  - 카드 전체 종합점수 평균과 null이 아닌 변화량 평균을 반올림하여 반환
  - 관계 카드에 이름·유형·현재 상태·점수 상태·변화량·분석 시각·최근 8주 스파크라인 반환
  - 같은 주 재분석 시 주차별 최신 리포트 하나만 카드와 스파크라인에 반영
  - `abs(change)` 기준 변화가 큰 관계 상위 3개 반환
  - `score < 60 OR change <= -10` 기준 주의 관계 선정
  - `LOW_SCORE`, `LARGE_DROP`, `SCORE_AND_DROP` 사유 코드와 사용자 문구 반환
  - 절대 변화량·점수 오름차순/내림차순·최종 분석시각 정렬 지원
  - 다른 사용자의 관계 집계 격리
  - 잘못된 날짜·정렬 enum을 `400 INVALID_REQUEST`로 통일
  - 관계 이름에 공백이 있어도 공백이 아닌 두 번째 글자를 카드 이니셜로 반환
  - Flyway V4에서 사용자·주차 대시보드 조회용 복합 인덱스 추가
- 자동 검증:
  - 대시보드 통합 테스트 3건 통과
  - 프로젝트 전체 회귀 테스트 32건 통과 (`failures=0`, `errors=0`, `skipped=0`)
  - 주차 정규화·평균·상위 3개·주의 관계 집계 테스트
  - 과거 주차 조회의 미래 리포트 배제와 DRAFT 관계 제외 테스트
  - 같은 주 재분석 중복 제거와 최근 8주 스파크라인 테스트
  - 카드 정렬과 사용자별 데이터 격리 테스트
  - 잘못된 날짜·정렬 요청 검증 테스트
- Postman 확인:
  1. 여러 관계에 주차별 완료 리포트가 존재하는 로그인 세션을 준비한다.
  2. 기본 조회로 주차·평균·카드·상위 3개·주의 관계를 한 응답에서 확인한다.
  3. `weekOf`를 과거 날짜로 바꿔 미래 리포트가 제외되는지 확인한다.
  4. `sort=SCORE_ASC`, `SCORE_DESC`, `UPDATED_DESC`의 카드 순서를 확인한다.
  5. `weekOf=invalid`, `sort=UNKNOWN`의 `400 INVALID_REQUEST`를 확인한다.

## Woo 담당 기능 완료 현황

- [x] 인물·관계 관리
- [x] 관계 체크인 저장
- [x] 관계 리포트 및 분석 근거 생성
- [x] 메인 대시보드 집계 API
- [x] AI 상담 및 MongoDB 저장

## 5. AI 상담 기능 및 MongoDB 전환

- 상태: 완료
- API:
  - 상담방 생성·목록·상세·삭제: `/api/v1/consultations`
  - 메시지 저장·조회: `/api/v1/consultations/{consultationId}/messages`
  - AI 답변 스트림: `GET /api/v1/consultations/{consultationId}/events?after={userMessageId}`
- 저장소:
  - PostgreSQL: 사용자·관계·관계 리포트·PRQC·관찰 근거
  - MongoDB `consultations`: 사용자/관계/리포트 연결, 최근 메시지 미리보기
  - MongoDB `chat_messages`: 사용자·AI 메시지, 생성 상태, 근거 참조, 안전 제안
- 구현 내용:
  - 상담 생성 시 가장 최근 관계 리포트 ID를 상담방 컨텍스트로 고정
  - AI 호출 시 종합점수·전주 변화·PRQC 6개 항목·관찰 근거·최근 완료 대화 최대 20개 전달
  - 동일 상담방에서 AI 답변 동시 생성을 한 건으로 제한
  - 사용자 메시지와 `GENERATING` AI 메시지를 먼저 저장한 뒤 비동기로 답변 생성
  - `assistant.started`, `assistant.delta`, `assistant.completed`, `assistant.failed`, `heartbeat` SSE 이벤트 지원
  - POST 직후 빠르게 답변이 끝나도 MongoDB의 최종 메시지를 SSE 구독 시 재전송하여 이벤트 유실 방지
  - AI 최종 답변, 근거 참조, 구조화 안전 제안을 MongoDB에 저장
  - AI 실패 상태를 저장하고 재시도 가능 오류 이벤트 반환
  - MongoDB Docker Compose 서비스, 인증 URI, 자동 복합 인덱스 구성
- 자동 검증:
  - 상담 서비스의 리포트 컨텍스트 조합, 메시지 저장, 동시 생성 거부 테스트
  - AI 답변·근거·안전 제안 저장 및 상담방 미리보기 갱신 테스트
  - 프로젝트 전체 회귀 테스트 통과
  - 실제 MongoDB 8.0 인증 연결 및 `consultations`, `chat_messages` 컬렉션 생성 확인
  - 사용자/최종 수정 시각 및 상담방/생성 시각 복합 인덱스 생성 확인
  - 애플리케이션 18080 포트 기동 및 Actuator health `UP` 확인
- Postman 확인:
  1. 저장소 루트에서 `docker compose up -d postgres mongo`로 두 저장소를 시작한다.
  2. 로그인 세션과 CSRF 토큰으로 리포트가 있는 관계의 상담방을 생성한다.
  3. 메시지 전송 응답의 `streamUrl`을 확인한다.
  4. Postman에서 별도 `GET streamUrl` 요청을 열고 `Accept: text/event-stream`으로 이벤트를 확인한다.
  5. 완료 후 메시지 조회에서 AI 본문·`evidenceRefs`·`safetyNotice`가 유지되는지 확인한다.
  6. AI 생성 중 다시 메시지를 보내 `409 CHAT_ALREADY_GENERATING`을 확인한다.

## 6. 프론트 AI 상담 메시지 중복 전송 방지

- 상태: 완료
- 수정 내용:
  - Enter 입력 시 기본 동작을 막아 키 이벤트 중복 전송을 방지
  - 한글 IME 조합 중 Enter는 전송하지 않도록 처리
  - AI 답변 스트림이 완료·실패·오류 상태가 될 때까지 전송 잠금 적용
  - 잠금 중 전송 버튼과 추천 질문 버튼 비활성화
  - 전송 실패 시 잠금을 해제해 재시도 가능하도록 처리
- 자동 검증:
  - `npm run lint` 통과 (기존 React Hook 경고만 존재)
  - `npm run build` 통과

## 7. 파일 없는 새 인물 등록 방지

- 상태: 완료
- 수정 내용:
  - 새 인물 1단계에서는 이름·관계 유형만 임시 상태로 보관하고 관계 생성 API를 호출하지 않도록 변경
  - 실제 대화 파일을 선택한 뒤에만 관계를 생성하고 파일을 업로드하도록 순서 변경
  - 파일 업로드 실패·파일 형식 검증 실패·검증 타임아웃 시 이번 흐름에서 생성한 관계를 자동 삭제
  - 기존 관계에 데이터를 추가하는 `add-data` 모드에서는 기존 관계를 삭제하지 않도록 분리
  - 파일이 없거나 유효하지 않으면 다음 단계 버튼을 계속 비활성화
- 자동 검증:
  - `npm run lint` 통과 (기존 React Hook 경고만 존재)
  - `npm run build` 통과

## 8. AI 분석 요청 컨텍스트 확장

- 상태: 완료
- 수정 내용:
  - 분석 Worker가 AI 요청 전에 사용자·관계·체크인 데이터를 조회하도록 구성
  - 사용자 컨텍스트는 `userId`, 표시 이름, 시간대만 전달하고 카카오 식별자·OAuth/세션 토큰·프로필 이미지 URL은 제외
  - 관계 컨텍스트로 관계 ID, 대상 이름, 관계 유형, 분석 요청 시점 상태를 전달
  - 체크인 컨텍스트로 체크인 ID·주차·`RELATIONSHIP_FEELING`, `CONVERSATION_COMFORT`의 1~7점 응답을 모두 전달
  - 카카오톡 CSV/TXT의 파싱 결과는 기존처럼 정규화 NDJSON gzip 파일로 전달하고, 위 컨텍스트는 `application/json` multipart 파트로 전달
  - AI 내부 API 명세와 OpenAPI 계약에 `context` 필드를 필수로 반영
- 자동 검증:
  - `./gradlew compileJava test` 통과
  - 사용자·관계·체크인 주차·두 체크인 점수가 AI 컨텍스트 JSON으로 직렬화되는 전용 테스트 통과
