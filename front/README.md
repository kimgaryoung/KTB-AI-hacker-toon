# 관계온도 프론트엔드

채팅 대화 데이터를 기반으로 인간관계의 건강도를 분석하고, 지속적으로 추적·상담해주는 웹 서비스 "관계온도"의 프론트엔드입니다.

"끝없는 관계의 우주 속, 당신에게"라는 컨셉으로, 별이 가득한 우주를 배경으로 귀여운 우주인 캐릭터와 회전하는 달이 등장하는 다크 테마 UI로 구성되어 있습니다. 백엔드(`../backend`)의 `docs/API_SPEC.md`에 정의된 REST API와 실제로 통신하도록 연동되어 있습니다.

## 기술 스택

- **React 19** + **Vite** (rolldown 기반 빌드) — 별도 상태관리 라이브러리 없이 React Context + hooks만 사용
- **react-router-dom v7** — 클라이언트 라우팅
- **순수 CSS** — CSS 프레임워크 없이 CSS 변수 기반 디자인 토큰 + 컴포넌트별 CSS 파일
- **oxlint** — 린트
- 차트(레이더/라인/스파크라인/게이지)는 별도 차트 라이브러리 없이 SVG를 직접 그려서 구현했습니다.

## 폴더 구조

```
front/
├── index.html
├── vite.config.js          # /api, /oauth2 를 백엔드(:8080)로 프록시하는 설정 포함
├── src/
│   ├── main.jsx             # 엔트리 포인트
│   ├── App.jsx               # 라우트 정의
│   ├── api/                  # 백엔드 엔드포인트별 fetch 래퍼
│   │   ├── client.js         # 공통 fetch 클라이언트 (세션 쿠키, CSRF, 에러 파싱)
│   │   ├── auth.js
│   │   ├── dashboard.js
│   │   ├── relationships.js
│   │   ├── conversationFiles.js
│   │   ├── checkins.js
│   │   ├── analyses.js
│   │   ├── reports.js
│   │   ├── consultations.js  # SSE 스트림 연결 포함
│   │   └── supportResources.js
│   ├── context/
│   │   └── AuthContext.jsx   # 로그인 상태 관리 (/users/me 기반)
│   ├── components/           # 화면 공통 컴포넌트
│   │   ├── AppLayout.jsx     # 사이드바 + 라우트 가드
│   │   ├── Starfield.jsx     # 배경 별밭 캔버스 애니메이션
│   │   ├── Moon.jsx / Astronaut.jsx  # 우주 컨셉 마스코트
│   │   ├── NewPersonModal.jsx        # 인물 등록 3단계 마법사 (+대화 내역 추가 모드)
│   │   └── charts/           # RadarChart, TrendLineChart, Sparkline, Gauge (SVG 직접 렌더링)
│   ├── pages/                # 화면 단위 페이지
│   │   ├── LoginPage.jsx     # 카카오 로그인 전용
│   │   ├── ConsentPage.jsx   # 최초 1회 온보딩 동의 (로컬 저장, 백엔드 API 없음)
│   │   ├── DashboardPage.jsx # 메인 대시보드
│   │   ├── ReportPage.jsx    # 인물별 PRQC 리포트
│   │   └── ChatPage.jsx      # AI 상담 (SSE 스트리밍)
│   ├── data/constants.js     # 관계 유형·PRQC·분석 단계 등 백엔드 enum 라벨 매핑
│   ├── utils/avatar.js       # 인물 아바타 그라디언트/이니셜 유틸
│   └── styles/theme.css      # 전역 디자인 토큰(색상·타이포·버튼·카드 등)
```

## 시작하기

```bash
cd front
npm install
npm run dev
```

`http://localhost:5173` 에서 접속합니다. 백엔드도 함께 떠 있어야 로그인 이후 화면이 정상 동작합니다.

```bash
# 다른 터미널에서
cd backend
./gradlew bootRun
```

빌드/린트:

```bash
npm run build     # dist/ 에 정적 빌드 산출물 생성
npm run lint       # oxlint
npm run preview    # 빌드 결과 로컬 프리뷰
```

## 백엔드 연동 방식 (중요)

### 1. Vite 프록시로 같은 오리진 유지

`vite.config.js`에서 `/api`, `/oauth2` 요청을 `http://localhost:8080`(백엔드)으로 프록시합니다. 프론트 코드는 항상 상대 경로(`/api/v1/...`)로만 fetch를 호출합니다.

```js
server: {
  proxy: {
    '/api': { target: 'http://localhost:8080', changeOrigin: false },
    '/oauth2': { target: 'http://localhost:8080', changeOrigin: false },
  },
}
```

`changeOrigin`을 일부러 끈 이유: 백엔드는 카카오 콜백 URL을 만들 때 요청의 `Host` 헤더를 그대로 사용합니다(`{baseUrl}/api/v1/auth/kakao/callback`). 프록시가 Host를 백엔드 쪽(`localhost:8080`)으로 바꿔버리면 스프링이 콜백 URL을 `localhost:8080` 기준으로 생성해버려서, 브라우저가 실제로 접속 중인 `localhost:5173`과 세션 쿠키 오리진이 어긋나 로그인이 깨집니다. Host를 `localhost:5173`으로 유지해야 백엔드가 `http://localhost:5173/api/v1/auth/kakao/callback`을 생성하고, 이 값이 카카오 개발자 콘솔에 등록된 Redirect URI와 정확히 일치해야 합니다.

**따라서 카카오 개발자 콘솔의 Redirect URI는 `http://localhost:5173/api/v1/auth/kakao/callback`으로 등록되어 있어야 합니다.**

### 2. 인증은 세션 쿠키 + CSRF 토큰

- 로그인 여부는 `GET /users/me` 성공 여부로 판단합니다 (`src/context/AuthContext.jsx`).
- 응답의 `csrfToken`을 저장해두었다가, POST/PATCH/DELETE 등 상태 변경 요청에 `X-CSRF-Token` 헤더로 실어 보냅니다.
- `fetch`에는 항상 `credentials: 'include'`를 사용합니다.

### 3. 401 대신 302 리다이렉트를 반환하는 백엔드 인증 방식 대응

백엔드는 인증되지 않은 요청에 대해 평범한 `401`이 아니라 **카카오 로그인 페이지로 302 리다이렉트**를 내려줍니다(Spring Security `oauth2Login()`의 기본 엔트리포인트 동작). 브라우저 `fetch`가 이 리다이렉트를 그대로 따라가면 카카오 서버까지 실제로 이동을 시도하게 되므로, `src/api/client.js`에서 모든 요청에 `redirect: 'manual'`을 사용하고 `response.type === 'opaqueredirect'`인 경우를 "로그인 필요"로 해석합니다.

### 4. 로그인 성공 후 리다이렉트 경로

백엔드는 카카오 로그인 성공 시 항상 `{FRONTEND_BASE_URL}/dashboard`로 리다이렉트하도록 고정되어 있습니다. 그래서 프론트 라우트도 대시보드를 `/`가 아니라 `/dashboard`로 두고, `/`는 `/dashboard`로 리다이렉트하도록 맞췄습니다.

### 5. 최초 로그인 온보딩(동의) 화면은 프론트 전용 기능

기획 단계에서는 "최초 로그인 시 약관 동의 화면"이 있었지만, 현재 백엔드 API 명세에는 별도의 동의 저장 API가 없습니다. 그래서 `ConsentPage`는 브라우저 `localStorage`에만 "온보딩을 마쳤는지"를 저장하는 프론트 전용 1회성 화면입니다. 백엔드에 약관 동의 API가 추가되면 `AuthContext.completeOnboarding()`을 실제 API 호출로 바꾸면 됩니다.

## 화면/라우트 구성

| 경로 | 화면 | 설명 |
|---|---|---|
| `/login` | 로그인 | 카카오 계정 전용 로그인, 사이드바 없음 |
| `/consent` | 온보딩 동의 | 최초 로그인 1회, 로그인은 했지만 아직 동의 안 한 경우 |
| `/dashboard` | 메인 대시보드 | 주간 관계 온도 요약, 인물 카드, 변화가 큰 관계, 주의가 필요한 관계 |
| `/report/:id` | 인물별 리포트 | PRQC 6요소 레이더 차트, 종합 온도 게이지, 근거 카드, 8주 추이 |
| `/chat/:id` | AI 상담 | 관계 리포트 기반 AI 챗봇, SSE로 답변 스트리밍, 위험 신호 시 상담 리소스 안내 |

`/dashboard`, `/report`, `/chat`은 `AppLayout`(사이드바 포함 레이아웃) 하위 라우트이며, 로그인하지 않았으면 `/login`으로, 로그인은 했지만 온보딩을 안 마쳤으면 `/consent`로 자동 이동합니다.

## 백엔드 API 계약과 다른 점 (문서보다 실제 코드 기준으로 맞춘 부분)

`docs/API_SPEC.md`와 실제 컨트롤러 구현을 대조해서 발견한 차이점을 프론트에 반영했습니다.

- **PRQC 점수는 0~100 스케일**입니다 (`RelationshipReport.PrqcScores`는 정수 0~100). 문서의 1~7점 예시와 다릅니다.
- `GET /relationships`는 `search` 쿼리만 지원합니다. `sort`/`status`/커서 페이지네이션은 아직 구현되어 있지 않습니다.
- 대시보드 응답의 `sparkline` 필드는 관계당 값이 1개뿐이라(8주치가 아님) 미니 스파크라인 렌더링은 사용하지 않았습니다.
- `GET /support-resources`는 페이지네이션 없이 배열을 그대로 반환합니다.

## 알려진 제한 사항 / TODO

- 이 환경에서는 인터넷 접속이 불가능해 실제 카카오 로그인~대시보드~리포트~챗봇 전체 흐름을 라이브로 검증하지 못했습니다. `npm run build`/`npm run lint`는 통과했고, 백엔드 없이도 로그인 페이지 폴백·보호 라우트 리다이렉트가 정상 동작하는 것은 확인했습니다.
- 대시보드에서 `DRAFT`/`ANALYZING`/`ANALYSIS_FAILED` 상태인 관계는 표시되지 않습니다(백엔드가 `ACTIVE` 상태만 카드 목록에 포함). 해당 상태의 관계는 `/report` 좌측 인물 목록에서는 보이며, 클릭 시 "대화 데이터 올리기" 흐름으로 이어집니다.
- 분석 실패 시 재시도, 관계 삭제(`DELETE /relationships/{id}`), 상담방 삭제(`DELETE /consultations/{id}`) 등 일부 엔드포인트는 API 래퍼(`src/api/`)만 준비되어 있고 화면에서는 아직 노출하지 않았습니다.
