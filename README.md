# 관계온도 (Relationship Temperature)

카카오톡 대화를 분석해 관계의 상태를 점수로 보여주는 서비스입니다.

백엔드(`backend_team`)·AI(`AI_Team`) 두 팀 저장소를 하나로 합친 모노레포입니다.
`docker compose up` 한 번으로 DB·백엔드·AI 서버·프론트가 전부 뜹니다.

```
KTB-AI-hacker-toon/
├── compose.yaml       전체 실행 스택 (PostgreSQL · MongoDB · 백엔드 · AI · 프론트)
├── compose.prod.yaml  EC2 배포 오버레이
├── .env.example       환경변수 템플릿
├── backend/           Spring Boot API (Java 21) + Dockerfile
├── ai/                FastAPI 분석 서버 (Python 3.12, Gemini) + Dockerfile
└── front/             React 19 + Vite + Dockerfile
```

## 빠른 시작

**필요한 것: Docker Desktop 하나면 됩니다.** (Java·Python·Node는 전부 컨테이너 안에 들어 있습니다)

```bash
git clone https://github.com/kimgaryoung/KTB-AI-hacker-toon.git
cd KTB-AI-hacker-toon

cp .env.example .env          # 그대로 두면 카카오 로그인 + AI stub 으로 동작
docker compose up -d --build  # DB + 백엔드 + AI + 프론트 전부 기동 (첫 빌드 수 분)
docker compose ps             # 5개 서비스가 healthy/running 이면 준비 완료
```

그 다음 http://localhost:5173 으로 접속하면 됩니다.

**실제 AI 분석을 켜려면** `.env`에서 두 줄만 바꾸고 백엔드를 재기동합니다.

```bash
# .env
AI_MODE=http
GOOGLE_API_KEY=<Gemini API 키>
```

```bash
docker compose up -d backend ai
```

| 주소 | 내용 |
|---|---|
| http://localhost:5173 | 프론트엔드 (여기로 접속) |
| http://localhost:8080/actuator/health | 백엔드 헬스체크 |
| http://localhost:5173/api/v1/auth/kakao/authorize | 카카오 로그인 진입 |

AI 서버(8000)는 백엔드만 호출하는 내부 서비스라 호스트 포트를 열지 않습니다.
`docker compose exec ai python -c "import urllib.request;print(urllib.request.urlopen('http://127.0.0.1:8000/health').read())"` 로 확인할 수 있습니다.

**로그인은 반드시 5173으로 들어가세요.** 프론트가 `/api`·`/oauth2`를 백엔드로 프록시하기 때문에
세션 쿠키와 카카오 redirect_uri가 한 오리진(`localhost:5173`)으로 유지됩니다.

`front/`는 소스가 bind mount 되어 있어 코드를 고치면 그대로 HMR이 됩니다.
백엔드 코드를 고치면 `docker compose up -d --build backend`, AI 서버 코드를 고치면
`docker compose up -d --build ai`로 다시 빌드하세요.

### 컨테이너 없이 호스트에서 돌리기

기본 프로필은 **H2 메모리 DB**와 AI stub을 쓰기 때문에 Docker 없이도 백엔드는 뜹니다.
이 방식은 Java 21과 Node가 호스트에 있어야 합니다.

```bash
docker compose up -d postgres mongo   # 인프라만
cd backend && ./gradlew bootRun       # 다른 터미널
cd front && npm install && npm run dev
cd ai && python -m venv .venv && source .venv/bin/activate \
  && pip install -r requirements.txt && uvicorn app.main:app --port 8000   # AI_MODE=http 일 때만
```

## Java 21 설치

프로젝트는 Java 21 toolchain을 씁니다. 없으면 Gradle이 이렇게 실패합니다.

```
Cannot find a Java installation on your machine matching: {languageVersion=21}
```

macOS 기준:

```bash
brew install openjdk@21
mkdir -p ~/Library/Java/JavaVirtualMachines
ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk \
        ~/Library/Java/JavaVirtualMachines/openjdk-21.jdk
```

기존 JDK는 지워지지 않습니다. Gradle이 프로젝트별 요구 버전을 알아서 고릅니다.
`sudo`도 필요 없습니다.

## 인프라 (Docker)

`compose.yaml`은 **저장소 루트**에 있습니다.

```bash
docker compose up -d          # 전체 기동
docker compose ps             # 상태 확인
docker compose logs -f postgres
docker compose down           # 정지 (데이터 유지)
docker compose down -v        # 데이터까지 삭제
```

| 서비스 | 포트 | 용도 |
|---|---|---|
| front | 5173 | Vite 개발 서버 (`/api`·`/oauth2`는 backend로 프록시) |
| backend | 8080 | Spring Boot API |
| ai | (내부 8000) | FastAPI 분석 서버. backend만 `http://ai:8000`으로 호출 |
| postgres | 5432 | 애플리케이션 DB (Flyway가 스키마 생성) |
| mongo | 27017 | 분석 원문 저장 |

DB 계정은 둘 다 `relationship_temperature` 로 동일합니다.

컨테이너로 띄운 백엔드는 H2가 아니라 **PostgreSQL**에 붙습니다
(`DB_URL`을 compose가 `jdbc:postgresql://postgres:5432/...`로 덮어씀).

프론트를 정적 빌드 + nginx로 서빙하려면 `compose.yaml`의 `front.build.target`을
`prod`로, 포트를 `"5173:80"`으로 바꾸면 됩니다.

```bash
docker compose exec postgres psql -U relationship_temperature -d relationship_temperature

docker compose exec mongo mongosh -u relationship_temperature -p relationship_temperature \
  --authenticationDatabase admin relationship_temperature
```

```
jdbc:postgresql://localhost:5432/relationship_temperature
mongodb://relationship_temperature:relationship_temperature@localhost:27017/relationship_temperature?authSource=admin
```

## 백엔드 실행

```bash
cd backend
./gradlew bootRun      # H2 메모리 DB (기본)
./gradlew test
```

### PostgreSQL로 실행

```bash
docker compose up -d postgres
cd backend && SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

`prod` 프로필은 `.env`의 값을 요구합니다.

## 환경변수

**카카오 API 키는 저장소 루트의 `.env`에 넣습니다.** `.env`는 커밋되지 않습니다.
`docker compose`가 이 파일을 읽어 backend 컨테이너에 환경변수로 주입합니다.

```bash
cp .env.example .env
# .env 를 열어 KAKAO_CLIENT_ID / KAKAO_CLIENT_SECRET 수정
docker compose up -d backend   # 값을 바꿨으면 재기동해야 반영됩니다
```

`DB_URL`·`MONGODB_URI`·`APP_STORAGE_ROOT`는 컨테이너 실행 시 compose가 서비스 이름 기준으로
덮어쓰므로 `.env`의 `localhost` 값은 **호스트에서 직접 실행할 때만** 쓰입니다.

| 변수 | 용도 |
|---|---|
| `KAKAO_CLIENT_ID` | 카카오 **REST API 키** (JavaScript 키가 아닙니다) |
| `KAKAO_CLIENT_SECRET` | 카카오 Client Secret |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL 접속 |
| `FRONTEND_BASE_URL` | 로그인 성공 후 돌아갈 주소 (기본 `http://localhost:5173`) |
| `AI_MODE` | `stub`(더미 결과, 기본) 또는 `http`(ai 컨테이너 실제 호출). 이 둘만 유효 |
| `AI_SERVICE_TOKEN` | 백엔드 ↔ ai 내부 토큰. compose 가 양쪽 컨테이너에 같은 값을 주입 |
| `AI_BASE_URL` | 컨테이너 실행 시 compose 가 `http://ai:8000`으로 덮어씀. 호스트 실행 시만 사용 |
| `GOOGLE_API_KEY` | Gemini API 키. `AI_MODE=http` 일 때 필수 |

**REST API 키와 Client Secret은 서버 전용 비밀값입니다. 커밋하지 마세요.**

## 카카오 로그인 설정

[카카오 개발자 콘솔](https://developers.kakao.com/console/app)에서 아래를 설정합니다.

| 위치 | 할 일 |
|---|---|
| 앱 설정 > 앱 키 | **REST API 키** 복사 |
| 앱 설정 > 플랫폼 > Web | 사이트 도메인 `http://localhost:5173`, `http://localhost:8080`, `https://ktb-ai-hackathon-team14.com` 등록 |
| 제품 설정 > 카카오 로그인 | 활성화 **ON** |
| 제품 설정 > 카카오 로그인 > Redirect URI | `http://localhost:5173/api/v1/auth/kakao/callback` (컨테이너 실행 시)<br>`http://localhost:8080/api/v1/auth/kakao/callback` (백엔드에 직접 붙을 때)<br>`https://ktb-ai-hackathon-team14.com/api/v1/auth/kakao/callback` (배포) |
| 제품 설정 > 카카오 로그인 > 동의항목 | 닉네임, 프로필 사진 |

**콜백 경로는 `/api/v1/auth/kakao/callback` 입니다.** `/auth/kakao/callback` 처럼 `/api/v1`이 빠지면
KOE006이 납니다. 이 경로는 두 곳에 고정되어 있습니다.

- `application.yml` → `spring.security.oauth2.client.registration.kakao.redirect-uri`
- `SecurityConfig.java` → `.redirectionEndpoint(e -> e.baseUri("/api/v1/auth/kakao/callback"))`

호스트 부분(`{baseUrl}`)은 요청의 Host 헤더에서 만들어집니다. 즉 **5173으로 접속하면 5173짜리
redirect_uri가, 8080으로 접속하면 8080짜리가** 생성되므로 콘솔에는 두 개 다 등록해두는 편이 편합니다.

동의항목을 켜지 않으면 로그인은 되지만 닉네임이 내려오지 않습니다.

로그인 흐름을 확인하려면:

```bash
curl -s -o /dev/null -w '%{redirect_url}\n' http://localhost:5173/oauth2/authorization/kakao
# → https://kauth.kakao.com/oauth/authorize?...&redirect_uri=http://localhost:5173/api/v1/auth/kakao/callback
```

## 문제가 생기면

| 증상 | 해결 |
|---|---|
| `Cannot find a Java installation ... 21` | 위 "Java 21 설치" 절차를 따르세요 |
| `Port 8080 was already in use` | `pkill -f RelationshipTemperatureApplication` |
| `Cannot connect to the Docker daemon` | Docker Desktop이 실행 중인지 확인 |
| `port is already allocated` | 다른 컨테이너/프로세스가 5173·8080·5432·27017을 쓰는 중입니다. `docker ps`, `lsof -nP -iTCP:5173 -sTCP:LISTEN`으로 확인 후 정리하세요 |
| 프론트가 API를 못 부름 | 8080이 아니라 **5173**으로 접속했는지 확인 |
| `KOE101` | REST API 키가 아닌 다른 키를 넣었거나 오타입니다 |
| `KOE004` | 콘솔에서 카카오 로그인이 비활성 상태입니다 |
| `KOE006` | Redirect URI가 콘솔 등록값과 정확히 일치하지 않습니다. 경로에 `/api/v1`이 들어가는지, 포트(5173/8080)가 맞는지 확인 |
| 닉네임이 비어 있음 | 콘솔의 동의항목에서 닉네임을 켜세요 |
| DB를 초기화하고 싶음 | `docker compose down -v` 후 다시 실행 |

## 더 읽을 것

- [`backend/README.md`](./backend/README.md) — 백엔드 패키지 구조와 설계 원칙
- [`ai/README.md`](./ai/README.md) — AI 분석 서버 파이프라인 구조와 API
- [`backend/docs/AI_INTERNAL_API_SPEC.md`](./backend/docs/AI_INTERNAL_API_SPEC.md) — 백엔드 ↔ AI 내부 계약


---

# 배포

AWS 배포는 별도 문서로 분리했습니다.

**→ [DEPLOYMENT.md](./DEPLOYMENT.md)** — EC2 1대 + ALB(ACM) + Route53 구성

- 아키텍처와 HTTPS 스킴 전달 체인
- ACM · ALB · 대상 그룹 · 보안 그룹 · Route53 설정값
- 배포 절차와 검증 체크리스트
- 배포 주의사항 10가지, 트러블슈팅 표

요약하면 이렇습니다.

```bash
cp .env.prod.example .env   # FRONTEND_BASE_URL=https://..., SESSION_COOKIE_SECURE=true
docker compose -f compose.yaml -f compose.prod.yaml up -d --build
```

TLS는 **ALB에서 ACM 인증서로 종료**하고 EC2는 평문 80만 받습니다. EC2 안에는 인증서를 두지 않습니다.
