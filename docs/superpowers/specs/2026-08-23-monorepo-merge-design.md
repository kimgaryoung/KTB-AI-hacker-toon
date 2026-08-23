# backend_team + AI_Team 모노레포 통합 설계

작성일: 2026-08-23

## 목적

KTB-4-AI-Hackathon 조직의 `backend_team`(Spring 백엔드 + React 프론트)과 `AI_Team`(FastAPI 분석 서버)
두 저장소를 하나의 저장소 `kimgaryoung/KTB-AI-hacker-toon`(public)으로 합친다.
클론 직후 `cp .env.example .env && docker compose up -d --build` 만으로 전체 스택이 뜨는 것이 성공 기준이다.

## 결정 사항

| 항목 | 결정 |
|---|---|
| 히스토리 | 보존하지 않음. 스냅샷으로 초기 커밋 1개 |
| backend_team 기준 | 로컬 `feat/intro-animation` 작업 트리 (미커밋 intro 변경 포함) |
| AI_Team 기준 | `origin/main` (로컬은 27커밋 뒤처져 있어 사용하지 않음) |
| 레포 | `kimgaryoung/KTB-AI-hacker-toon`, public |
| `.env.example` 카카오 키 | 원본 그대로 유지 (사용자 결정) |
| git user.name | 현재 전역 설정 그대로 |

## 구조

```
KTB-AI-hacker-toon/
├── backend/   front/   deploy/   docs/      ← backend_team 그대로
├── ai/                                       ← AI_Team 루트 전체 (README, Dockerfile, tests 포함)
├── compose.yaml / compose.prod.yaml
├── .env.example / .env.prod.example / .gitignore
└── README.md / DEPLOYMENT.md
```

## 변경 내용

- `compose.yaml`
  - `ai` 서비스: `profiles: ["ai"]` 제거(항상 기동), `build.context: ../AI_Team → ./ai`, `ANTHROPIC_API_KEY` 전달 추가.
  - `backend`: `depends_on.ai.condition: service_healthy` 추가.
- `compose.prod.yaml`: `ai.profiles: !reset []` 제거 (더 이상 필요 없음). `GOOGLE_API_KEY` 필수 검사는 유지.
- `.env.example`: `AI_MODE=stub` 기본, `GOOGLE_API_KEY`/`ANTHROPIC_API_KEY` 항목 추가, 주석 정리.
- `.env.prod.example`: `../AI_Team` 클론 안내 제거.
- `.gitignore`: `ai/` 파이썬 산출물 ignore 추가.
- `README.md`: 구조도·빠른 시작·포트 표·환경변수 표에 ai 반영, 새 레포 URL.
- `DEPLOYMENT.md`: clone URL을 새 레포로.

## 복사 방식

- backend_team: `git ls-files -co --exclude-standard` 목록을 rsync → ignore 대상(`.env`, `build/`, `node_modules/`, `.idea/`, `.claude/`)은 자동 제외, 작업 트리 내용(미커밋 변경) 포함.
- AI_Team: `git archive origin/main` 을 `ai/`에 풀기.

## 검증

1. `docker compose config` 가 오류 없이 통과.
2. 기존 로컬 스택(포트 5173/8080/5432/27017/8000 사용 중)을 건드리지 않도록 별도 프로젝트명 + 포트 오버라이드로 `docker compose up -d --build` 실행, 5개 서비스 healthy 확인, 프론트/백엔드 health/ai health 응답 확인 후 정리.
3. 새 레포에 `.env`, 빌드 산출물이 없음을 `git ls-files` 로 확인.

## 범위 밖

- 두 저장소의 커밋 히스토리 이관.
- `.env.example` 의 실제 카카오 키 치환.
- 코드 자체의 리팩터링.
