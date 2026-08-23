# 인트로(스플래시) 애니메이션 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 비로그인 최초 진입 시 13초짜리 자동 제품 데모 인트로(`/intro`)를 재생한 뒤 `/login`으로 자연 전환한다.

**Architecture:** `/intro` 라우트의 `IntroPage`가 Scene 배열 기반 상태 머신(setTimeout 체인)으로 10개 장면을 순차 재생한다. 장면 내부 모션은 CSS 키프레임, 장면 전환은 key 교체 + 블러/줌 애니메이션. 배경 Starfield는 인트로 전체에서 고정되어 "하나의 공간" 느낌을 유지한다. 데모 UI는 인트로 전용 경량 목업이며 기존 theme.css 변수·컴포넌트(Astronaut, Moon, LogoMark)를 재사용한다.

**Tech Stack:** React 19 + Vite, 일반 CSS (라이브러리 추가 금지). react-router-dom 7.

## Global Constraints

- 의존성 추가 금지. 애니메이션은 `transform` / `opacity` / `filter`만 사용 (top/left/width/height/margin 프레임 애니메이션 금지)
- 스펙: `docs/superpowers/specs/2026-08-20-intro-animation-design.md` (타임라인·문구는 스펙이 원본)
- 태그라인(확정): **"당신의 대화 속에 관계를 이해할 힌트가 있어요."**
- 노출: 비로그인 + `sessionStorage.introPlayed !== "true"` 최초 방문 시 1회. 종료(또는 스킵) 시점에 기록
- 로그인 사용자는 인트로 진입 시 `/dashboard`로 이동
- 스킵: UI 미노출, `Escape` 키만 (개발·시연용)
- `prefers-reduced-motion` 또는 뷰포트 폭 < 768px → 정적 버전(로고+태그라인 ~1.6초) 후 `/login`
- 타이핑 효과는 3곳만: 홍길동 / "요즘 권태기인 것 같아.." / 마지막 카톡 메시지. AI 답변은 Bubble 페이드인
- 데모 데이터 고정: 홍길동·연인·72°·7:3·한강 (API 의존 없음)
- 테스트 인프라(vitest 등)가 없는 프로젝트이므로 TDD 대신 각 태스크마다 브라우저(localhost:5173, 도커 HMR) 실측 검증으로 대체한다. 검증 항목은 각 태스크에 명시

## File Structure

```
front/src/intro/
  IntroPage.jsx        # 오케스트레이터: Scene 상태 머신, 게이트, Esc, reduced-motion 분기
  intro.css            # 인트로 전체 스타일 (장면별 키프레임 포함)
  parts/
    DemoFrame.jsx      # 중앙 고정 브라우저 창 목업 프레임
    StageStack.jsx     # 좌측 단계 아카이브 스택 (CTRL 레퍼런스 4번 기법)
    DemoCursor.jsx     # 데모 커서 블롭 (variant별 이동 키프레임)
    TypeText.jsx       # 타이핑 효과
    SpaceProps.jsx     # Rocket, Planet SVG (Scene 10용 신규 아트)
  scenes/
    Scene01Login.jsx   Scene02Dashboard.jsx  Scene03Form.jsx
    Scene04Upload.jsx  Scene05Checkin.jsx    Scene06Analysis.jsx
    Scene07Report.jsx  Scene08Chat.jsx       Scene09Kakao.jsx
    Scene10Logo.jsx
front/src/App.jsx        # /intro 라우트 추가 (modify)
front/src/pages/LoginPage.jsx  # 최초 방문 게이트 추가 (modify)
```

장면 타임라인(합계 13.0초, `SCENES` 배열 상수로 중앙 관리 — 튜닝은 이 배열만 수정):

| # | id | dur(ms) | stage 배지 |
|---|----|---------|-----------|
| 1 | login | 1000 | — |
| 2 | dashboard | 1000 | — |
| 3 | form | 1200 | 인물 등록 |
| 4 | upload | 1300 | 대화 업로드 |
| 5 | checkin | 800 | 체크인 |
| 6 | analysis | 1200 | AI 분석 |
| 7 | report | 900 | 리포트 |
| 8 | chat | 2100 | AI 상담 |
| 9 | kakao | 2000 | 실제 대화 |
| 10 | logo | 1500 | — |

stage 배지는 "해당 장면이 끝난 뒤" StageStack에 쌓인다.

---

### Task 1: 라우팅·게이트·타임라인 엔진

**Files:**
- Create: `front/src/intro/IntroPage.jsx`, `front/src/intro/intro.css`
- Modify: `front/src/App.jsx` (라우트 추가), `front/src/pages/LoginPage.jsx` (게이트)

**Interfaces:**
- Produces: `SCENES` 배열 규약 — 각 Scene 컴포넌트는 props 없이 렌더되고, 자기 duration 안에서 CSS/내부 타이머로 완결된다. `INTRO_PLAYED_KEY = 'introPlayed'`
- Consumes: `Starfield`, `LogoMark`, `useAuth` (기존 코드)

- [ ] **Step 1: IntroPage 뼈대 구현** — Scene 자리에 placeholder(`<div className="intro-ph">{id}</div>`)를 두고 상태 머신·게이트·Esc·정적 분기를 완성한다. (전체 코드는 아래 — scenes import만 Task 3~8에서 교체)

```jsx
// front/src/intro/IntroPage.jsx
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Starfield from '../components/Starfield';
import { LogoMark } from '../components/Icons';
import StageStack from './parts/StageStack';
import './intro.css';

export const INTRO_PLAYED_KEY = 'introPlayed';

const SCENES = [
  { id: 'login', dur: 1000, stage: null, Comp: () => <div className="intro-ph">login</div> },
  // ... (Task 3~8에서 실제 Scene 컴포넌트로 교체, dur/stage는 File Structure 표와 동일)
];

export default function IntroPage() {
  const navigate = useNavigate();
  const { isLoggedIn } = useAuth();
  const [idx, setIdx] = useState(0);
  const isStatic = useMemo(
    () =>
      (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) ||
      window.innerWidth < 768,
    []
  );

  const finish = useCallback(() => {
    sessionStorage.setItem(INTRO_PLAYED_KEY, 'true');
    navigate('/login', { replace: true });
  }, [navigate]);

  useEffect(() => {
    if (isLoggedIn) navigate('/dashboard', { replace: true });
  }, [isLoggedIn, navigate]);

  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && finish();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [finish]);

  useEffect(() => {
    if (isStatic) {
      const t = setTimeout(finish, 1600);
      return () => clearTimeout(t);
    }
    if (idx >= SCENES.length) {
      finish();
      return;
    }
    const t = setTimeout(() => setIdx((i) => i + 1), SCENES[idx].dur);
    return () => clearTimeout(t);
  }, [idx, isStatic, finish]);

  if (isStatic)
    return (
      <div className="intro-root">
        <Starfield />
        <div className="intro-static-center">
          <LogoMark size={46} />
          <div className="intro-static-name">관계온도</div>
          <p className="intro-static-tag">당신의 대화 속에 관계를 이해할 힌트가 있어요.</p>
        </div>
      </div>
    );

  if (idx >= SCENES.length) return null;
  const scene = SCENES[idx];
  const SceneComp = scene.Comp;
  const stages = SCENES.slice(0, idx).map((s) => s.stage).filter(Boolean);
  return (
    <div className="intro-root">
      <Starfield />
      {scene.id !== 'logo' && <StageStack stages={stages} />}
      <div className={scene.id === 'logo' ? 'intro-fullbleed' : 'intro-stage'}>
        <div className="intro-scene-swap" key={scene.id}>
          <SceneComp />
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: intro.css 기본 레이어** — 루트/스테이지/장면 전환(블러+줌)/정적 버전 스타일

```css
/* front/src/intro/intro.css — 기본 레이어 */
.intro-root {
  position: fixed;
  inset: 0;
  z-index: 40;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(ellipse 900px 500px at 82% -8%, rgba(165, 149, 232, 0.2), transparent 60%),
    radial-gradient(ellipse 700px 500px at -6% 18%, rgba(226, 160, 201, 0.14), transparent 60%),
    linear-gradient(180deg, var(--bg-void) 0%, var(--bg-deep) 46%, var(--bg-nebula) 100%);
}
.intro-stage {
  position: relative;
  z-index: 2;
  width: min(760px, 82vw);
}
.intro-fullbleed {
  position: relative;
  z-index: 2;
}
.intro-scene-swap {
  animation: introSceneIn 0.45s cubic-bezier(0.22, 0.9, 0.3, 1) both;
}
@keyframes introSceneIn {
  from {
    opacity: 0.15;
    transform: scale(1.045);
    filter: blur(14px);
  }
  to {
    opacity: 1;
    transform: scale(1);
    filter: blur(0);
  }
}
.intro-static-center {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  text-align: center;
  animation: introSceneIn 0.6s ease both;
}
.intro-static-name {
  font-size: 30px;
  font-weight: 800;
}
.intro-static-tag {
  color: var(--text-secondary);
  font-size: 14.5px;
}
.intro-ph {
  color: var(--text-muted);
  text-align: center;
  padding: 80px 0;
}
```

- [ ] **Step 3: 라우트/게이트 연결**

`App.jsx`: `import IntroPage from './intro/IntroPage';` 후 `<Route path="/login" .../>` 위에 `<Route path="/intro" element={<IntroPage />} />` 추가.

`LoginPage.jsx`: 기존 인증 useEffect 아래에 추가:

```jsx
import { INTRO_PLAYED_KEY } from '../intro/IntroPage';
// ...
useEffect(() => {
  if (status === 'checking' || isLoggedIn) return;
  if (sessionStorage.getItem(INTRO_PLAYED_KEY) !== 'true') navigate('/intro', { replace: true });
}, [status, isLoggedIn, navigate]);
```

- [ ] **Step 4: 브라우저 검증** — 시크릿 기준 시나리오: (a) `sessionStorage.clear()` 후 `/login` 접속 → `/intro`로 이동, placeholder가 순서대로 자동 진행 후 `/login` 복귀 (b) 그 상태에서 `/login` 재접속 → 인트로 안 뜸 (c) `/intro`에서 Esc → 즉시 `/login` (d) DevTools로 prefers-reduced-motion emulate → 정적 버전 1.6초 후 `/login`
- [ ] **Step 5: Commit** — `feat: 인트로 라우트와 타임라인 엔진 추가`

### Task 2: 공용 부품 (프레임·스택·커서·타이핑·우주 소품)

**Files:**
- Create: `front/src/intro/parts/DemoFrame.jsx`, `StageStack.jsx`, `DemoCursor.jsx`, `TypeText.jsx`, `SpaceProps.jsx`
- Modify: `front/src/intro/intro.css` (부품 스타일 추가)

**Interfaces (Produces):**
- `<DemoFrame title>{children}</DemoFrame>` — 중앙 브라우저 창 목업
- `<StageStack stages={string[]} />`
- `<DemoCursor variant="login" />` → `.demo-cursor.cursor-login` (variant별 키프레임은 각 Scene 태스크에서 정의)
- `<TypeText text delay speed />` — delay(ms) 후 speed(ms/자)로 타이핑
- `Rocket`, `Planet` SVG 컴포넌트 (`{ size }`)

- [ ] **Step 1: 부품 구현**

```jsx
// DemoFrame.jsx
export default function DemoFrame({ title, children }) {
  return (
    <div className="demo-frame">
      <div className="demo-frame-bar">
        <i /><i /><i />
        <span>{title}</span>
      </div>
      <div className="demo-frame-body">{children}</div>
    </div>
  );
}

// StageStack.jsx
export default function StageStack({ stages }) {
  if (!stages.length) return null;
  return (
    <div className="stage-stack">
      {stages.map((s) => (
        <div key={s} className="stage-chip">{s} ✓</div>
      ))}
    </div>
  );
}

// DemoCursor.jsx
export default function DemoCursor({ variant }) {
  return <div className={`demo-cursor cursor-${variant}`} aria-hidden="true" />;
}

// TypeText.jsx
import { useEffect, useState } from 'react';
export default function TypeText({ text, delay = 0, speed = 70, className = '' }) {
  const [n, setN] = useState(0);
  useEffect(() => {
    let iv;
    const start = setTimeout(() => {
      iv = setInterval(() => {
        setN((v) => {
          if (v >= text.length) {
            clearInterval(iv);
            return v;
          }
          return v + 1;
        });
      }, speed);
    }, delay);
    return () => {
      clearTimeout(start);
      if (iv) clearInterval(iv);
    };
  }, [text, delay, speed]);
  return (
    <span className={`type-text ${className}`}>
      {text.slice(0, n)}
      <i className="type-caret" />
    </span>
  );
}
```

```jsx
// SpaceProps.jsx — 서비스 아트 톤(파스텔, 라운드)에 맞춘 신규 SVG
export function Rocket({ size = 56 }) {
  return (
    <svg viewBox="0 0 100 100" width={size} height={size} fill="none">
      <path d="M50 12c10 8 15 22 15 36 0 8-2 15-5 20H40c-3-5-5-12-5-20 0-14 5-28 15-36z" fill="#f4f0fb" />
      <circle cx="50" cy="40" r="8" fill="#a595e8" />
      <circle cx="50" cy="40" r="4.5" fill="#f6e9ff" />
      <path d="M35 52c-7 3-11 10-11 18 5-1 10-3 13-6" fill="#e2a0c9" />
      <path d="M65 52c7 3 11 10 11 18-5-1-10-3-13-6" fill="#e2a0c9" />
      <path d="M44 70h12l-2 8h-8l-2-8z" fill="#c9bdf0" />
      <path d="M47 80c0 5 1 8 3 11 2-3 3-6 3-11h-6z" fill="#f0b56c" />
    </svg>
  );
}
export function Planet({ size = 56 }) {
  return (
    <svg viewBox="0 0 100 100" width={size} height={size} fill="none">
      <circle cx="50" cy="50" r="24" fill="#a595e8" />
      <circle cx="42" cy="44" r="5" fill="rgba(255,255,255,0.35)" />
      <circle cx="58" cy="58" r="3.4" fill="rgba(255,255,255,0.22)" />
      <ellipse cx="50" cy="52" rx="38" ry="10" stroke="#e2a0c9" strokeWidth="4" fill="none" transform="rotate(-16 50 52)" />
    </svg>
  );
}
```

- [ ] **Step 2: 부품 CSS 추가** (intro.css)

```css
/* ===== parts ===== */
.demo-frame {
  background: rgba(20, 16, 58, 0.86);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  overflow: hidden;
  backdrop-filter: blur(6px);
}
.demo-frame-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border);
  color: var(--text-muted);
  font-size: 11.5px;
  font-weight: 700;
}
.demo-frame-bar i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--surface-strong);
}
.demo-frame-bar i:nth-child(1) { background: rgba(226, 137, 111, 0.75); }
.demo-frame-bar i:nth-child(2) { background: rgba(240, 181, 108, 0.75); }
.demo-frame-bar i:nth-child(3) { background: rgba(127, 217, 182, 0.75); }
.demo-frame-bar span { margin-left: 8px; }
.demo-frame-body { position: relative; padding: 26px 30px 30px; min-height: 320px; }

.stage-stack {
  position: absolute;
  left: max(24px, calc(50vw - 560px));
  top: 50%;
  transform: translateY(-50%);
  z-index: 3;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.stage-chip {
  background: var(--surface-strong);
  border: 1px solid var(--border);
  border-radius: var(--radius-pill);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
  padding: 7px 14px;
  animation: stageChipIn 0.45s cubic-bezier(0.22, 0.9, 0.3, 1) both;
}
@keyframes stageChipIn {
  from { opacity: 0; transform: translateX(-14px); }
  to { opacity: 1; transform: translateX(0); }
}

.demo-cursor {
  position: absolute;
  z-index: 5;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: rgba(165, 149, 232, 0.45);
  border: 1.5px solid rgba(244, 240, 251, 0.75);
  box-shadow: 0 0 18px rgba(165, 149, 232, 0.5);
  pointer-events: none;
  top: 0;
  left: 0;
}

.type-caret {
  display: inline-block;
  width: 2px;
  height: 1em;
  background: var(--accent-lavender);
  vertical-align: text-bottom;
  margin-left: 2px;
  animation: caretBlink 0.7s steps(1) infinite;
}
@keyframes caretBlink { 50% { opacity: 0; } }
```

- [ ] **Step 3: 검증** — placeholder Scene 하나에 부품들을 임시 렌더해 스타일 확인 후 원복 (or Task 3에서 함께 확인)
- [ ] **Step 4: Commit** — `feat: 인트로 공용 부품 추가`

### Task 3: Scene 1–2 (로그인 → 대시보드 진입)

**Files:** Create `scenes/Scene01Login.jsx`, `scenes/Scene02Dashboard.jsx` / Modify `IntroPage.jsx`(SCENES 교체), `intro.css`

**연출 (스펙 Scene 1·2):**
- S1(1.0s): Astronaut가 하단에서 부상(translateY 40px→0, 0.5s), 미니 로그인 카드(LogoMark+관계온도, 카카오 버튼). 커서가 버튼으로 이동(0.15→0.7s), 0.75s에 클릭 수축+버튼 glow
- S2(1.0s): 미니 대시보드(타이틀 "나의 우주" + 빈 상태 + `+ 새 인물 등록`). 커서가 버튼으로 이동, 0.8s 클릭. 버튼 주변 spotlight(나머지 dim)

```jsx
// Scene01Login.jsx
import DemoFrame from '../parts/DemoFrame';
import DemoCursor from '../parts/DemoCursor';
import Astronaut from '../../components/Astronaut';
import { LogoMark, KakaoIcon } from '../../components/Icons';

export default function Scene01Login() {
  return (
    <DemoFrame title="relationship-temperature.app">
      <div className="s1-center">
        <div className="s1-logo"><LogoMark size={34} /><b>관계온도</b></div>
        <p className="s1-copy">감이 아니라 데이터로, 관계를 이해하는 시간</p>
        <button className="kakao-demo-btn" type="button"><KakaoIcon /> 카카오로 시작하기</button>
      </div>
      <div className="s1-astro"><Astronaut size={72} /></div>
      <DemoCursor variant="login" />
    </DemoFrame>
  );
}
```

```jsx
// Scene02Dashboard.jsx
import DemoFrame from '../parts/DemoFrame';
import DemoCursor from '../parts/DemoCursor';

export default function Scene02Dashboard() {
  return (
    <DemoFrame title="관계온도 — 대시보드">
      <div className="s2-dim">
        <h2 className="mini-title">나의 우주</h2>
        <p className="mini-sub">아직 등록된 인물이 없어요. 첫 인물을 등록해볼까요?</p>
      </div>
      <button className="s2-add btn btn-primary" type="button">+ 새 인물 등록</button>
      <DemoCursor variant="dashboard" />
    </DemoFrame>
  );
}
```

```css
/* Scene 1 */
.s1-center { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 26px 0 40px; }
.s1-logo { display: flex; align-items: center; gap: 10px; font-size: 20px; font-weight: 800; }
.s1-copy { color: var(--text-secondary); font-size: 13px; }
.kakao-demo-btn {
  display: inline-flex; align-items: center; gap: 8px; margin-top: 10px;
  background: #fee500; color: #191600; border: none; border-radius: var(--radius-pill);
  font-weight: 800; font-size: 13.5px; padding: 11px 22px;
}
.cursor-login ~ * .kakao-demo-btn { position: relative; }
.s1-astro { position: absolute; left: 34px; bottom: 18px; animation: s1AstroUp 0.55s cubic-bezier(0.22, 0.9, 0.3, 1) both; }
@keyframes s1AstroUp { from { opacity: 0; transform: translateY(46px); } to { opacity: 1; transform: translateY(0); } }
.cursor-login { animation: cursorLogin 1s cubic-bezier(0.3, 0.7, 0.3, 1) both; }
@keyframes cursorLogin {
  0% { transform: translate(120px, 260px); opacity: 0; }
  18% { opacity: 1; }
  70% { transform: translate(388px, 208px) scale(1); }
  78% { transform: translate(388px, 208px) scale(0.72); }
  88%, 100% { transform: translate(388px, 208px) scale(1); }
}
/* Scene 2 */
.mini-title { font-size: 18px; font-weight: 800; }
.mini-sub { color: var(--text-secondary); font-size: 13px; margin-top: 8px; }
.s2-dim { animation: s2Dim 1s ease both; padding-bottom: 60px; }
@keyframes s2Dim { 0%, 45% { opacity: 1; } 100% { opacity: 0.45; } }
.s2-add { position: absolute; right: 30px; bottom: 26px; animation: s2Glow 1s ease both; }
@keyframes s2Glow {
  0%, 55% { box-shadow: var(--shadow-pink); }
  80%, 100% { box-shadow: 0 0 0 6px rgba(226, 160, 201, 0.25), var(--shadow-pink); }
}
.cursor-dashboard { animation: cursorDashboard 1s cubic-bezier(0.3, 0.7, 0.3, 1) both; }
@keyframes cursorDashboard {
  0% { transform: translate(388px, 208px); }
  62% { transform: translate(560px, 300px) scale(1); }
  74% { transform: translate(560px, 300px) scale(0.72); }
  86%, 100% { transform: translate(560px, 300px) scale(1); }
}
```

- [ ] Step 1: 두 Scene 작성, SCENES의 두 placeholder 교체
- [ ] Step 2: 검증 — 커서가 카카오 버튼→클릭 수축, S2에서 버튼 spotlight·클릭이 duration 안에 완결되는지, 전환 블러 확인
- [ ] Step 3: Commit — `feat: 인트로 Scene 1-2 (로그인·대시보드)`

### Task 4: Scene 3–4 (정보 입력 → 대화 업로드)

**Files:** Create `scenes/Scene03Form.jsx`, `scenes/Scene04Upload.jsx` / Modify `IntroPage.jsx`, `intro.css`

**연출:** S3(1.2s) — 이름 필드 TypeText(`홍길동`, delay 100, speed 90), 관계 4버튼 중 커서가 "연인" 0.65s 클릭 → `.picked` glow, 0.95s "다음" 클릭. S4(1.3s) — 좌측 미니 카톡 패널(채팅방→메뉴→**대화 내보내기** 강조), `.txt` 파일 칩이 드롭존으로 이동(transform, 0.35→0.8s), 드롭존 하이라이트 후 `홍길동_대화.txt ✓` 체크(0.95s)

```jsx
// Scene03Form.jsx
import DemoFrame from '../parts/DemoFrame';
import DemoCursor from '../parts/DemoCursor';
import TypeText from '../parts/TypeText';

export default function Scene03Form() {
  return (
    <DemoFrame title="새 인물 등록">
      <div className="s3-field">
        <label>이름</label>
        <div className="s3-input"><TypeText text="홍길동" delay={100} speed={90} /></div>
      </div>
      <div className="s3-field">
        <label>관계</label>
        <div className="s3-rels">
          <span>친구</span><span>가족</span><span className="picked">연인</span><span>기타</span>
        </div>
      </div>
      <button className="btn btn-primary s3-next" type="button">다음</button>
      <DemoCursor variant="form" />
    </DemoFrame>
  );
}

// Scene04Upload.jsx
import DemoFrame from '../parts/DemoFrame';
import DemoCursor from '../parts/DemoCursor';

export default function Scene04Upload() {
  return (
    <DemoFrame title="카카오톡 대화 업로드">
      <div className="s4-wrap">
        <div className="s4-kakao">
          <div className="s4-kakao-head">홍길동 ♥</div>
          <div className="s4-menu">
            <span>사진 보내기</span>
            <span className="s4-menu-hot">대화 내보내기</span>
            <span>설정</span>
          </div>
        </div>
        <div className="s4-file">📄 홍길동_대화.txt</div>
        <div className="s4-drop">
          <span className="s4-drop-idle">여기로 파일을 끌어다 놓으세요</span>
          <span className="s4-drop-done">홍길동_대화.txt ✓</span>
        </div>
      </div>
      <DemoCursor variant="upload" />
    </DemoFrame>
  );
}
```

```css
/* Scene 3 */
.s3-field { margin-bottom: 18px; }
.s3-field label { display: block; font-size: 12px; font-weight: 700; color: var(--text-muted); margin-bottom: 7px; }
.s3-input {
  background: var(--surface); border: 1px solid var(--border-strong); border-radius: 12px;
  padding: 11px 14px; font-size: 14.5px; width: 300px;
}
.s3-rels { display: flex; gap: 8px; }
.s3-rels span {
  border: 1px solid var(--border); border-radius: var(--radius-pill);
  padding: 8px 16px; font-size: 13px; color: var(--text-secondary);
}
.s3-rels .picked { animation: s3Pick 1.2s both; }
@keyframes s3Pick {
  0%, 54% { background: transparent; color: var(--text-secondary); box-shadow: none; }
  62%, 100% {
    background: rgba(226, 160, 201, 0.18); color: var(--accent-pink);
    border-color: var(--accent-pink); box-shadow: 0 0 14px rgba(226, 160, 201, 0.35);
  }
}
.s3-next { position: absolute; right: 30px; bottom: 26px; }
.cursor-form { animation: cursorForm 1.2s cubic-bezier(0.3, 0.7, 0.3, 1) both; }
@keyframes cursorForm {
  0% { transform: translate(560px, 300px); }
  46% { transform: translate(252px, 172px) scale(1); }
  55% { transform: translate(252px, 172px) scale(0.72); }
  64% { transform: translate(252px, 172px) scale(1); }
  82% { transform: translate(636px, 306px) scale(1); }
  89% { transform: translate(636px, 306px) scale(0.72); }
  96%, 100% { transform: translate(636px, 306px) scale(1); }
}
/* Scene 4 */
.s4-wrap { display: flex; align-items: center; gap: 22px; position: relative; }
.s4-kakao { background: var(--surface); border: 1px solid var(--border); border-radius: 14px; overflow: hidden; width: 210px; flex: none; }
.s4-kakao-head { background: var(--surface-strong); padding: 10px 14px; font-size: 13px; font-weight: 800; }
.s4-menu { display: flex; flex-direction: column; padding: 8px 0; }
.s4-menu span { padding: 8px 14px; font-size: 12.5px; color: var(--text-secondary); }
.s4-menu-hot { animation: s4MenuHot 1.3s both; border-radius: 8px; }
@keyframes s4MenuHot {
  0%, 12% { background: transparent; color: var(--text-secondary); }
  22%, 100% { background: rgba(165, 149, 232, 0.2); color: var(--text-primary); font-weight: 800; }
}
.s4-file {
  position: absolute; left: 150px; top: 46px; z-index: 4;
  background: var(--surface-solid); border: 1px solid var(--border-strong);
  border-radius: 10px; padding: 8px 13px; font-size: 12.5px; font-weight: 700;
  animation: s4FileFly 1.3s cubic-bezier(0.3, 0.7, 0.3, 1) both;
}
@keyframes s4FileFly {
  0%, 24% { opacity: 0; transform: translate(0, 8px) scale(0.9); }
  32% { opacity: 1; transform: translate(0, 0) scale(1); }
  66% { opacity: 1; transform: translate(300px, 26px) scale(1); }
  76%, 100% { opacity: 0; transform: translate(316px, 30px) scale(0.6); }
}
.s4-drop {
  flex: 1; border: 1.5px dashed var(--border-strong); border-radius: 14px;
  min-height: 150px; display: grid; place-items: center; position: relative;
  animation: s4DropGlow 1.3s both;
}
@keyframes s4DropGlow {
  0%, 40% { border-color: var(--border-strong); background: transparent; }
  60%, 100% { border-color: var(--accent-mint); background: rgba(127, 217, 182, 0.07); }
}
.s4-drop span { grid-area: 1 / 1; font-size: 13px; }
.s4-drop-idle { color: var(--text-muted); animation: s4IdleOut 1.3s both; }
@keyframes s4IdleOut { 0%, 62% { opacity: 1; } 74%, 100% { opacity: 0; } }
.s4-drop-done { color: var(--accent-mint); font-weight: 800; animation: s4DoneIn 1.3s both; }
@keyframes s4DoneIn { 0%, 68% { opacity: 0; transform: scale(0.85); } 80%, 100% { opacity: 1; transform: scale(1); } }
.cursor-upload { animation: cursorUpload 1.3s cubic-bezier(0.3, 0.7, 0.3, 1) both; }
@keyframes cursorUpload {
  0% { transform: translate(636px, 306px); }
  20% { transform: translate(150px, 120px) scale(1); }
  27% { transform: translate(150px, 120px) scale(0.72); }
  34% { transform: translate(170px, 84px) scale(1); }
  66% { transform: translate(470px, 116px) scale(1); }
  74% { transform: translate(470px, 116px) scale(0.85); }
  84%, 100% { transform: translate(470px, 116px) scale(1); }
}
```

- [ ] Step 1: 구현 + SCENES 교체  · Step 2: 검증(타이핑 완료 시점, 파일 비행 경로, 체크 표시) · Step 3: Commit — `feat: 인트로 Scene 3-4 (등록·업로드)`

### Task 5: Scene 5–6 (체크인 → AI 분석)

**Files:** Create `scenes/Scene05Checkin.jsx`, `scenes/Scene06Analysis.jsx` / Modify `IntroPage.jsx`, `intro.css`

**연출:** S5(0.8s) — Q1 "최근 상대와 대화가 즐거웠나요?" 2점, Q2 "최근 관계에 만족하고 있나요?" 3점이 순차 선택(0.25s/0.5s), "분석 시작" 클릭(0.72s). S6(1.2s) — 중앙 Astronaut float + 파티클 3개 궤도, 문구 3개 순차 교체(0/0.4/0.8s: "대화 패턴을 확인하고 있어요"→"감정 표현을 분석하고 있어요"→"두 사람의 관계를 분석했어요 ✓"), 프로그레스 바 차오름(1.0s), "리포트 보기" 강조

```jsx
// Scene05Checkin.jsx
import DemoFrame from '../parts/DemoFrame';
import DemoCursor from '../parts/DemoCursor';

const ROWS = [
  { q: '최근 상대와 대화가 즐거웠나요?', pick: 2, cls: 's5-r1' },
  { q: '최근 관계에 만족하고 있나요?', pick: 3, cls: 's5-r2' },
];
export default function Scene05Checkin() {
  return (
    <DemoFrame title="관계 체크인">
      {ROWS.map(({ q, pick, cls }) => (
        <div key={q} className={`s5-row ${cls}`}>
          <p>{q}</p>
          <div className="s5-scores">
            {[1, 2, 3, 4, 5].map((n) => (
              <i key={n} className={n === pick ? 'hit' : ''}>{n}</i>
            ))}
          </div>
        </div>
      ))}
      <button className="btn btn-primary s5-go" type="button">분석 시작</button>
      <DemoCursor variant="checkin" />
    </DemoFrame>
  );
}

// Scene06Analysis.jsx
import { useEffect, useState } from 'react';
import DemoFrame from '../parts/DemoFrame';
import Astronaut from '../../components/Astronaut';

const MSGS = ['대화 패턴을 확인하고 있어요', '감정 표현을 분석하고 있어요', '두 사람의 관계를 분석했어요 ✓'];
export default function Scene06Analysis() {
  const [m, setM] = useState(0);
  useEffect(() => {
    const t1 = setTimeout(() => setM(1), 400);
    const t2 = setTimeout(() => setM(2), 800);
    return () => { clearTimeout(t1); clearTimeout(t2); };
  }, []);
  return (
    <DemoFrame title="AI 분석">
      <div className="s6-center">
        <div className="s6-orbit"><i /><i /><i /><Astronaut size={64} /></div>
        <p className="s6-msg" key={m}>{MSGS[m]}</p>
        <div className="s6-bar"><i /></div>
        <button className="btn btn-primary s6-report" type="button">리포트 보기</button>
      </div>
    </DemoFrame>
  );
}
```

```css
/* Scene 5 */
.s5-row { margin-bottom: 16px; }
.s5-row p { font-size: 13.5px; margin-bottom: 8px; }
.s5-scores { display: flex; gap: 8px; }
.s5-scores i {
  width: 34px; height: 34px; border-radius: 50%; display: grid; place-items: center;
  font-size: 12.5px; font-style: normal; font-weight: 700;
  border: 1px solid var(--border); color: var(--text-secondary);
}
.s5-r1 .hit { animation: s5Hit 0.8s both; }
.s5-r2 .hit { animation: s5Hit 0.8s 0.25s both; }
@keyframes s5Hit {
  0%, 30% { background: transparent; color: var(--text-secondary); transform: scale(1); }
  42% { transform: scale(0.82); }
  56%, 100% {
    background: rgba(165, 149, 232, 0.22); color: var(--text-primary);
    border-color: var(--accent-lavender); transform: scale(1);
    box-shadow: 0 0 12px rgba(165, 149, 232, 0.4);
  }
}
.s5-go { position: absolute; right: 30px; bottom: 26px; }
.cursor-checkin { animation: cursorCheckin 0.8s cubic-bezier(0.3, 0.7, 0.3, 1) both; }
@keyframes cursorCheckin {
  0% { transform: translate(470px, 116px); }
  28% { transform: translate(122px, 108px) scale(1); }
  36% { transform: translate(122px, 108px) scale(0.72); }
  44% { transform: translate(164px, 194px) scale(1); }
  58% { transform: translate(164px, 194px) scale(0.72); }
  66% { transform: translate(164px, 194px) scale(1); }
  84% { transform: translate(640px, 306px) scale(1); }
  92%, 100% { transform: translate(640px, 306px) scale(0.72); }
}
/* Scene 6 */
.s6-center { display: flex; flex-direction: column; align-items: center; gap: 14px; padding-top: 6px; }
.s6-orbit { position: relative; width: 110px; height: 110px; display: grid; place-items: center; animation: float 3s ease-in-out infinite; }
.s6-orbit i {
  position: absolute; width: 7px; height: 7px; border-radius: 50%;
  background: var(--accent-lavender); top: 50%; left: 50%; margin: -3.5px;
  animation: spin 1.6s linear infinite;
}
.s6-orbit i:nth-child(1) { transform-origin: 44px 0; }
.s6-orbit i:nth-child(2) { transform-origin: -44px 0; background: var(--accent-pink); animation-duration: 2.1s; }
.s6-orbit i:nth-child(3) { transform-origin: 0 48px; background: var(--accent-amber); animation-duration: 2.6s; }
.s6-msg { font-size: 14px; font-weight: 700; animation: introSceneIn 0.3s ease both; }
.s6-bar { width: 260px; height: 6px; border-radius: 4px; background: var(--surface-strong); overflow: hidden; }
.s6-bar i { display: block; height: 100%; border-radius: 4px; background: linear-gradient(90deg, var(--accent-lavender), var(--accent-pink));
  transform-origin: left; animation: s6Fill 1s cubic-bezier(0.3, 0.7, 0.4, 1) both; }
@keyframes s6Fill { from { transform: scaleX(0); } to { transform: scaleX(1); } }
.s6-report { animation: s2Glow 1.2s both; }
```

- [ ] Step 1: 구현 + SCENES 교체 · Step 2: 검증(문구 3단 교체가 1.2s 안에, 파티클 궤도) · Step 3: Commit — `feat: 인트로 Scene 5-6 (체크인·분석)`

### Task 6: Scene 7 (관계 리포트)

**Files:** Create `scenes/Scene07Report.jsx` / Modify `IntroPage.jsx`, `intro.css`

**연출(0.9s):** 지표 4행 stagger 등장(0.08s 간격), 강조값(`72°`, `7 : 3`, `최근에도 지속`, `한강`)은 accent-pink. 하단 "AI와 상담하기" 버튼 pulse 후 0.8s 클릭

```jsx
// Scene07Report.jsx
import DemoFrame from '../parts/DemoFrame';
import DemoCursor from '../parts/DemoCursor';

const METRICS = [
  ['관계 온도', '72°'],
  ['먼저 연락한 비율', '7 : 3'],
  ['애정 표현', '최근에도 지속'],
  ['자주 등장한 장소', '한강'],
];
export default function Scene07Report() {
  return (
    <DemoFrame title="홍길동 — 관계 리포트">
      <div className="s7-grid">
        {METRICS.map(([k, v], i) => (
          <div key={k} className="s7-row" style={{ '--i': i }}>
            <span>{k}</span><b>{v}</b>
          </div>
        ))}
      </div>
      <button className="btn btn-primary s7-cta" type="button">AI와 상담하기</button>
      <DemoCursor variant="report" />
    </DemoFrame>
  );
}
```

```css
/* Scene 7 */
.s7-grid { display: flex; flex-direction: column; gap: 10px; padding-bottom: 56px; }
.s7-row {
  display: flex; align-items: center; justify-content: space-between;
  background: var(--surface); border: 1px solid var(--border); border-radius: 12px;
  padding: 11px 16px; font-size: 13px;
  animation: s7RowIn 0.4s calc(var(--i) * 0.08s) cubic-bezier(0.22, 0.9, 0.3, 1) both;
}
.s7-row span { color: var(--text-secondary); }
.s7-row b { color: var(--accent-pink); font-size: 14.5px; }
@keyframes s7RowIn { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: translateY(0); } }
.s7-cta { position: absolute; right: 30px; bottom: 26px; animation: s2Glow 0.9s both; }
.cursor-report { animation: cursorReport 0.9s cubic-bezier(0.3, 0.7, 0.3, 1) both; }
@keyframes cursorReport {
  0% { transform: translate(640px, 306px); opacity: 0; }
  30% { opacity: 1; }
  74% { transform: translate(600px, 312px) scale(1); }
  84% { transform: translate(600px, 312px) scale(0.72); }
  94%, 100% { transform: translate(600px, 312px) scale(1); }
}
```

- [ ] Step 1: 구현 + SCENES 교체 · Step 2: 검증(stagger·강조색) · Step 3: Commit — `feat: 인트로 Scene 7 (리포트)`

### Task 7: Scene 8–9 (AI 상담 → 실제 대화)

**Files:** Create `scenes/Scene08Chat.jsx`, `scenes/Scene09Kakao.jsx` / Modify `IntroPage.jsx`, `intro.css`

**연출:** S8(2.1s) — 사용자 버블 TypeText("요즘 권태기인 것 같아.. 대화 분석 결과는 어때?", speed 32 → 약 0.9s), thinking `···`(0.9~1.3s), AI 버블 페이드인(1.3s)에 요약 답변, 강조 스팬('사랑해'/'좋아해'/70%/한강 accent), 마지막 문장 "한강 데이트를 제안해보는 건 어떨까요?" 하이라이트(1.7s). S9(2.0s) — 패널이 옆으로 밀리는 전환감(introSceneIn이 담당) + 카톡풍 대화, 사용자 메시지 TypeText(speed 30), 인디케이터, 답장 버블(1.55s): "그랬구나.. 몰라줘서 미안해. 한강 가서 같이 맛있는 것도 먹고 이야기해보자 :)"

```jsx
// Scene08Chat.jsx
import DemoFrame from '../parts/DemoFrame';
import TypeText from '../parts/TypeText';

export default function Scene08Chat() {
  return (
    <DemoFrame title="AI 상담">
      <div className="chat-col">
        <div className="bubble me s8-user">
          <TypeText text="요즘 권태기인 것 같아.. 대화 분석 결과는 어때?" delay={80} speed={32} />
        </div>
        <div className="bubble ai s8-think">···</div>
        <div className="bubble ai s8-answer">
          연인과의 관계가 예전 같지 않아 많이 속상하셨겠어요.
          <br />
          최근 대화에서도 <b>‘사랑해’, ‘좋아해’</b> 같은 애정 표현이 이어졌고, 상대방이 먼저 연락한
          비율도 약 <b>70%</b>로 높아요. 두 분은 <b>한강</b> 이야기를 자주 나누고 있어요.
          <span className="s8-hi">먼저 한강 데이트를 제안해보는 건 어떨까요?</span>
        </div>
      </div>
    </DemoFrame>
  );
}

// Scene09Kakao.jsx
import DemoFrame from '../parts/DemoFrame';
import TypeText from '../parts/TypeText';

export default function Scene09Kakao() {
  return (
    <DemoFrame title="카카오톡 — 홍길동 ♥">
      <div className="chat-col">
        <div className="bubble me kk s9-user">
          <TypeText
            text="나 최근에.. 우리 관계가 조금 권태기인 것 같아. 같이 한강 가서 이야기해볼까?"
            delay={100}
            speed={30}
          />
        </div>
        <div className="bubble other s9-think">···</div>
        <div className="bubble other s9-reply">
          그랬구나.. 몰라줘서 미안해.
          <br />
          한강 가서 같이 맛있는 것도 먹고 이야기해보자 :)
        </div>
      </div>
    </DemoFrame>
  );
}
```

```css
/* Scene 8-9 공통 채팅 */
.chat-col { display: flex; flex-direction: column; gap: 10px; }
.bubble {
  max-width: 78%; border-radius: 14px; padding: 10px 14px;
  font-size: 13px; line-height: 1.6;
}
.bubble.me { align-self: flex-end; background: rgba(165, 149, 232, 0.22); border: 1px solid rgba(165, 149, 232, 0.3); }
.bubble.me.kk { background: rgba(254, 229, 0, 0.16); border-color: rgba(254, 229, 0, 0.3); }
.bubble.ai, .bubble.other { align-self: flex-start; background: var(--surface); border: 1px solid var(--border); }
.bubble b { color: var(--accent-pink); }
.s8-think { animation: s8Think 2.1s both; }
@keyframes s8Think { 0%, 43% { opacity: 0; } 48%, 60% { opacity: 1; } 66%, 100% { opacity: 0; height: 0; padding: 0; border-width: 0; margin: 0; } }
.s8-answer { animation: s8Answer 2.1s both; }
@keyframes s8Answer { 0%, 62% { opacity: 0; transform: translateY(10px); } 72%, 100% { opacity: 1; transform: translateY(0); } }
.s8-hi { display: inline-block; margin-top: 6px; font-weight: 800; color: var(--accent-pink); animation: s8Hi 2.1s both; }
@keyframes s8Hi {
  0%, 80% { background: transparent; }
  90%, 100% { background: rgba(226, 160, 201, 0.16); box-shadow: 0 0 0 4px rgba(226, 160, 201, 0.16); border-radius: 4px; }
}
.s9-think { animation: s9Think 2s both; }
@keyframes s9Think { 0%, 55% { opacity: 0; } 60%, 70% { opacity: 1; } 76%, 100% { opacity: 0; height: 0; padding: 0; border-width: 0; margin: 0; } }
.s9-reply { animation: s9Reply 2s both; }
@keyframes s9Reply { 0%, 74% { opacity: 0; transform: translateY(10px); } 84%, 100% { opacity: 1; transform: translateY(0); } }
```

- [ ] Step 1: 구현 + SCENES 교체 · Step 2: 검증(타이핑→thinking→답변 타이밍, 강조 하이라이트) · Step 3: Commit — `feat: 인트로 Scene 8-9 (AI 상담·실제 대화)`

### Task 8: Scene 10 (로고 리빌) + 최종 검증

**Files:** Create `scenes/Scene10Logo.jsx` / Modify `IntroPage.jsx`, `intro.css`

**연출(1.5s, Fauna 레퍼런스):** 우주 오브젝트 4개(Rocket·Astronaut·Planet·Moon 축소)가 float 상태로 떠오른 뒤(0~0.35s, 90ms stagger), 왼쪽부터 순서대로 플립되어 "관·계·온·도" 글자로 정착(0.5s부터 0.12s stagger). LogoMark 팝(1.0s), 태그라인 페이드(1.1s), 마지막 0.25s 전체 페이드아웃 후 `/login`

```jsx
// Scene10Logo.jsx
import Astronaut from '../../components/Astronaut';
import Moon from '../../components/Moon';
import { LogoMark } from '../../components/Icons';
import { Rocket, Planet } from '../parts/SpaceProps';

const SLOTS = [
  { ch: '관', Obj: () => <Rocket size={54} /> },
  { ch: '계', Obj: () => <Astronaut size={54} /> },
  { ch: '온', Obj: () => <Planet size={54} /> },
  { ch: '도', Obj: () => <Moon scale={0.5} /> },
];
export default function Scene10Logo() {
  return (
    <div className="s10-wrap">
      <div className="s10-row">
        <span className="s10-mark"><LogoMark size={40} /></span>
        {SLOTS.map(({ ch, Obj }, i) => (
          <span key={ch} className="s10-slot" style={{ '--i': i }}>
            <span className="s10-obj"><Obj /></span>
            <span className="s10-char">{ch}</span>
          </span>
        ))}
      </div>
      <p className="s10-tag">당신의 대화 속에 관계를 이해할 힌트가 있어요.</p>
      <div className="s10-fade" />
    </div>
  );
}
```

```css
/* Scene 10 */
.s10-wrap { display: flex; flex-direction: column; align-items: center; gap: 20px; }
.s10-row { display: flex; align-items: center; gap: 10px; }
.s10-slot {
  position: relative; width: 64px; height: 72px; display: grid; place-items: center;
  perspective: 500px;
  animation: s10SlotUp 0.5s calc(var(--i) * 0.09s) cubic-bezier(0.22, 0.9, 0.3, 1) both;
}
@keyframes s10SlotUp { from { opacity: 0; transform: translateY(34px); } to { opacity: 1; transform: translateY(0); } }
.s10-obj, .s10-char { grid-area: 1 / 1; display: grid; place-items: center; backface-visibility: hidden; }
.s10-obj { animation: float 2.4s ease-in-out calc(var(--i) * 0.3s) infinite, s10ObjOut 0.4s calc(0.5s + var(--i) * 0.12s) forwards; }
@keyframes s10ObjOut { from { opacity: 1; transform: rotateX(0); } to { opacity: 0; transform: rotateX(88deg) translateY(-8px); } }
.s10-char {
  font-size: 44px; font-weight: 800; opacity: 0;
  animation: s10CharIn 0.4s calc(0.68s + var(--i) * 0.12s) cubic-bezier(0.22, 0.9, 0.3, 1) forwards;
}
@keyframes s10CharIn { from { opacity: 0; transform: rotateX(-88deg) translateY(8px); } to { opacity: 1; transform: rotateX(0); } }
.s10-mark { opacity: 0; animation: s10Pop 0.35s 1s cubic-bezier(0.22, 0.9, 0.3, 1) forwards; }
@keyframes s10Pop { from { opacity: 0; transform: scale(0.5); } to { opacity: 1; transform: scale(1); } }
.s10-tag { color: var(--text-secondary); font-size: 14.5px; opacity: 0; animation: s10Tag 0.4s 1.1s ease forwards; }
@keyframes s10Tag { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
.s10-fade { position: fixed; inset: 0; background: var(--bg-void); opacity: 0; pointer-events: none; animation: s10Fade 0.28s 1.24s ease forwards; }
@keyframes s10Fade { to { opacity: 1; } }
```

- [ ] Step 1: 구현 + SCENES 교체 (placeholder 전부 제거 확인)
- [ ] Step 2: 최종 검증 — 스펙 "10. 완료 기준" 전 항목 체크: 최초 진입 자동 실행 / 13초 내외 / 자동 진행 / 커서·Astronaut 대행 / 흐름 전달 / UI 일관성 / `/login` 자연 전환 / 세션 재생 1회 / 로그인 사용자 미노출 / reduced-motion 생략 / transform·opacity 위주. 풀런 3회 시청 + Esc·재접속·reduced-motion 각 1회
- [ ] Step 3: Commit — `feat: 인트로 Scene 10 (로고 리빌) 및 인트로 완성`
