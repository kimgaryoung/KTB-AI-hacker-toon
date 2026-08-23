# 배포 가이드 — EC2 1대 + ALB(ACM) + Route53

관계온도 서비스를 AWS에 올리는 절차입니다. 전제 조건은 아래 네 가지입니다.

1. **EC2 1대**에 컨테이너 4개를 모두 올린다
2. 도메인은 **Route53**으로 관리한다 (`ktb-ai-hackathon-team14.com`)
3. 카카오 소셜 로그인을 위해 **HTTPS가 반드시 동작**해야 한다
4. TLS 인증서는 **ACM**을 쓰고, 이를 붙이기 위해 **ALB**를 둔다

---

## 1. 아키텍처

```
                    사용자 브라우저
                          │ HTTPS (443)
                          ▼
              ┌───────────────────────┐
   Route53 ──▶│  ALB  (ACM 인증서)     │  ← 여기서 TLS 종료
   A(Alias)   │  :443 → TG / :80 → 301│
              └───────────┬───────────┘
                          │ HTTP (80)  ※ 평문. VPC 내부 구간
                          ▼
   ┌──────────────────────────────────────────────┐
   │ EC2 1대                                       │
   │  ┌────────────┐                               │
   │  │   front    │ nginx: 정적 서빙 + 리버스 프록시 │
   │  │  (nginx)   │ :80                           │
   │  └─────┬──────┘                               │
   │        │ 도커 내부 네트워크 (호스트 포트 없음)     │
   │  ┌─────▼──────┐                               │
   │  │  backend   │ Spring Boot :8080             │
   │  └──┬──────┬──┘                               │
   │  ┌──▼────┐ ┌▼────────┐                        │
   │  │postgres│ │  mongo  │                        │
   │  └────────┘ └─────────┘                        │
   └──────────────────────────────────────────────┘
```

**핵심**: 인증서는 ALB에만 있습니다. EC2 안에서는 인증서도, 443도, nginx TLS 설정도 쓰지 않습니다.
그래서 배포용 `.env`의 `NGINX_CONF`는 **`nginx.conf`(HTTP 설정) 그대로** 둡니다.
`nginx.tls.conf`는 ALB 없이 EC2에서 직접 Let's Encrypt를 붙이는 다른 구성을 위한 파일이라, 이 가이드에서는 쓰지 않습니다.

---

## 2. HTTPS가 성립하는 원리 — 반드시 이해하고 넘어갈 것

브라우저는 HTTPS로 접속하지만 Spring은 평문 HTTP로 요청을 받습니다.
그래서 **"원래 스킴이 https였다"는 정보를 헤더로 이어 붙여 전달**해야 합니다. 한 군데라도 끊기면 카카오 로그인이 `KOE006`으로 깨집니다.

```
브라우저 ──https──▶ ALB ──http──▶ nginx ──http──▶ Spring
                     │             │              │
                     │ ALB가 자동으로│ 그 값을 그대로  │ forward-headers-strategy:
                     │ X-Forwarded- │ 다시 전달       │ framework 가 읽어서
                     │ Proto: https │ (덮어쓰면 안 됨) │ {baseUrl}을 https로 조립
                     ▼             ▼              ▼
                  자동 처리    nginx.conf 의     application.yml:53
                             $client_proto      (수정 불필요)
```

가장 흔한 실수는 nginx에서 `proxy_set_header X-Forwarded-Proto $scheme;`을 쓰는 것입니다.
ALB→EC2 구간은 http라서 `$scheme`은 항상 `http`이고, 그러면 Spring이 카카오 `redirect_uri`를 `http://`로 만들어 콘솔 등록값과 어긋납니다.

`front/nginx.conf`는 이를 피하려고 `map`으로 처리합니다.

```nginx
map $http_x_forwarded_proto $client_proto {
    default     $scheme;                  # 헤더 없으면(로컬 직접 접속) 실제 스킴
    "~^https?$" $http_x_forwarded_proto;  # ALB가 준 값이 있으면 그것을 사용
}
```

**애플리케이션 코드는 수정할 필요가 없습니다.** 필요한 설정이 이미 들어 있습니다.

| 설정 | 위치 | 역할 |
|---|---|---|
| `forward-headers-strategy: framework` | `application.yml:53` | `X-Forwarded-*`를 읽어 `{baseUrl}`을 재구성 |
| `secure: ${SESSION_COOKIE_SECURE:false}` | `application.yml:60` | 쿠키 Secure 플래그를 환경변수로 전환 |
| `same-site: lax` | `application.yml:61` | 카카오 리다이렉트(top-level GET)에서 세션 유지 |

---

## 3. AWS 리소스 준비

### 3-1. ACM 인증서

**반드시 ALB와 같은 리전(`ap-northeast-2` 서울)에서 발급**해야 합니다. CloudFront용(`us-east-1`)과 헷갈리기 쉽습니다.

| 항목 | 값 |
|---|---|
| 리전 | ap-northeast-2 |
| 도메인 이름 | `ktb-ai-hackathon-team14.com` |
| 추가 이름 (선택) | `www.ktb-ai-hackathon-team14.com` |
| 검증 방법 | **DNS 검증** |

발급 화면에서 **"Route 53에서 레코드 생성"** 버튼을 누르면 검증 CNAME이 자동 등록됩니다. 상태가 `발급됨(Issued)`이 될 때까지 보통 몇 분 걸립니다.

### 3-2. 보안 그룹 2개

역할을 분리해야 합니다. EC2를 인터넷에 직접 열지 않는 것이 핵심입니다.

**`alb-sg`** (ALB에 부착)

| 방향 | 포트 | 소스 |
|---|---|---|
| 인바운드 | 80 | `0.0.0.0/0` (Anywhere-IPv4) |
| 인바운드 | 443 | `0.0.0.0/0` (Anywhere-IPv4) |

> **규칙 추가 화면에서 소스 드롭다운의 기본값은 "내 IP(My IP)"입니다.** 그대로 저장하면
> `x.x.x.x/32`가 되어 **본인 네트워크에서만 접속되고 팀원·심사위원은 전부 못 들어옵니다.**
> 브라우저에서는 잘 되는데 다른 사람은 "연결할 수 없음"이 뜬다면 이것부터 확인하세요.

아웃바운드는 기본값(모든 트래픽 허용) 그대로 둡니다. 여기를 막으면 ALB가 EC2로 보내지 못합니다.

**`ec2-sg`** (EC2에 부착)

| 방향 | 포트 | 소스 |
|---|---|---|
| 인바운드 | 80 | **`alb-sg`** (IP가 아니라 보안 그룹을 지정) |
| 인바운드 | 22 | 내 IP만 |

> `5432`, `27017`, `8080`은 **어디에도 열지 마세요.** `compose.prod.yaml`이 이 포트들을 호스트에 바인딩하지 않으므로 애초에 열 필요가 없습니다.

### 3-3. 대상 그룹 (Target Group)

**대상 그룹은 하나면 됩니다.** 프론트/백엔드를 나눠 두 개를 만들 필요가 없습니다.

| 항목 | 값 |
|---|---|
| 대상 유형 | Instances |
| 프로토콜 / 포트 | HTTP / **80** |
| 상태 검사 경로 | **`/healthz`** |
| 상태 검사 포트 | 트래픽 포트(Traffic port) |
| 성공 코드 | 200 |
| 정상 임계값 | 2 |
| 간격 | 30초 |

#### 포트는 반드시 80

nginx가 호스트 80에서 듣습니다. **3000이나 8080으로 만들면 상태 검사가 100% 실패**하고
ALB가 503을 돌려줍니다. 3000은 Next.js/CRA의 기본 포트라 습관적으로 넣기 쉬운데,
이 프로젝트는 Vite라 개발 포트가 5173이고 배포에서는 nginx가 80입니다.

> **대상 그룹의 프로토콜·포트는 생성 후 수정할 수 없습니다.** (상태 검사 설정은 수정 가능)
> 이미 잘못 만들었다면 둘 중 하나로 고칩니다.
>
> - **대상만 다시 등록**: Targets 탭 → 인스턴스 **Deregister** → **Register targets** →
>   인스턴스 선택 → *Ports for the selected instances* 에 **`80`** → Include as pending → Register
> - **새로 생성**: HTTP:80 대상 그룹을 만들고 443 리스너의 Forward 대상을 교체

#### 백엔드용 대상 그룹(8080)을 만들지 마세요

`compose.prod.yaml`이 backend·postgres·mongo의 호스트 포트를 걷어내기 때문에,
**EC2 호스트의 8080에는 아무도 듣고 있지 않습니다.** 8080짜리 대상 그룹은 영원히 unhealthy입니다.

```bash
# EC2에서 확인
sudo ss -tlnp | grep -E ':80|:8080'      # 80만 나오고 8080은 없다
curl -s -m 5 localhost:8080/actuator/health   # 연결 실패가 정상
```

경로 분기는 **ALB가 아니라 nginx가** 합니다.

```
                                    ┌─ /api/*    ─▶ backend:8080  (도커 내부)
브라우저 ─https─▶ ALB ─80─▶ nginx ──┼─ /oauth2/* ─▶ backend:8080  (도커 내부)
                  │                 └─ 그 외      ─▶ 정적 파일
            대상 그룹 1개
             HTTP:80
```

ALB 입장에서 EC2는 "80번 포트짜리 웹서버 하나"일 뿐입니다.

#### 상태 검사 경로 선택

`/healthz`는 nginx가 백엔드의 `/actuator/health`로 프록시하는 경로라, 200이면 **nginx와 backend가 모두 살아 있다**는 뜻입니다.

| 경로 | 백엔드 장애 시 | 언제 |
|---|---|---|
| `/healthz` | 사이트 전체 503 | 정확한 상태 파악. **기본 권장** |
| `/` | 화면은 뜨고 API만 실패 | 데모 중 "화면이라도 떠야" 할 때 |

EC2가 1대뿐이라 `/healthz`는 백엔드 장애 시 정적 페이지까지 503이 됩니다. 시연 직전이라면 `/`도 합리적인 선택입니다.

### 3-4. ALB

| 항목 | 값 |
|---|---|
| 유형 | Application Load Balancer |
| 체계 | **인터넷 경계(Internet-facing)** |
| IP 주소 유형 | IPv4 |
| 서브넷 | **서로 다른 AZ의 퍼블릭 서브넷 2개 이상** (ALB 필수 요건) |
| 보안 그룹 | `alb-sg` |

**리스너 2개**

| 리스너 | 동작 | 대상 그룹 |
|---|---|---|
| HTTPS : 443 | ACM 인증서 선택 → **전달(Forward)** | HTTP:80 대상 그룹 |
| HTTP : 80 | **HTTPS로 리디렉션** (301, 포트 443) | **연결하지 않음** |

**HTTP:80 리스너는 지우지 마세요.** 사용자가 주소창에 도메인만 치면 브라우저는 `http://`로 먼저
시도합니다. 80 리스너가 없으면 "사이트에 연결할 수 없음"이 뜨고, `https://`를 직접 입력한 사람만
들어올 수 있습니다.

**80 리스너에 대상 그룹을 붙이면 안 됩니다.** 평문으로 서비스가 노출될 뿐 아니라, 그 경로로 들어온
요청은 `X-Forwarded-Proto: http`가 되어 카카오 `redirect_uri`가 `http://`로 만들어져 KOE006이 납니다.

> 헷갈리기 쉬운 지점: **대상 그룹의 `HTTP:80`은 ALB→EC2 구간**이고, **리스너의 `HTTP:80`은
> 브라우저→ALB 구간**입니다. 이름만 같고 완전히 다른 구간입니다.

**443 리스너에 `/api/*` → 별도 대상 그룹 같은 경로 기반 규칙을 만들지 마세요.** 기본 규칙 하나로
전부 nginx에 넘기면 됩니다. 경로 규칙이 남아 있으면 화면은 떠도 **API 호출만 503**이 나서
로그인이 되지 않습니다.

```
IF  기본(default)  →  THEN  Forward to  <HTTP:80 대상 그룹>
```

**속성에서 유휴 시간 초과(Idle timeout)를 `180`초로 올리세요.** 기본값 60초로는 상담 SSE 스트림이 끊깁니다 (아래 6-3 참고).

### 3-5. Route53

기존에 EC2 IP를 가리키던 A 레코드가 있다면 **ALB Alias로 교체**해야 합니다.

| 항목 | 값 |
|---|---|
| 레코드 이름 | `ktb-ai-hackathon-team14.com` |
| 유형 | A |
| 별칭(Alias) | 예 |
| 대상 | Application Load Balancer → `ap-northeast-2` → 생성한 ALB |

`www`는 **레코드를 만들지 않으면 아예 해석되지 않습니다.** apex 도메인만 등록한 상태에서
`www.` 를 붙여 접속하면 DNS 응답이 없어 연결 실패합니다. `www`도 쓰려면 A(Alias) 레코드를
하나 더 만들고, **ACM 인증서에도 `www.` 를 대체 도메인 이름으로 포함**시켜야 합니다
(인증서에 없으면 TLS 경고가 뜹니다).

```bash
# 전환 확인 (ALB의 IP로 바뀌어야 한다. EC2 퍼블릭 IP가 나오면 아직 반영 전)
dig +short ktb-ai-hackathon-team14.com
```

---

## 4. EC2 준비

아래 명령은 전부 **SSH로 EC2에 접속한 뒤 그 서버의 bash에서** 실행합니다.

```bash
ssh -i <키페어>.pem ubuntu@<EC2 퍼블릭 IP>
```

**인스턴스**: t3.xlarge (4 vCPU / 16GB). 런타임 실측 사용량은 컨테이너 4개 합계 약 520MB라 넉넉합니다.
최소로 줄인다면 t3.medium(2 vCPU / 4GB)까지는 무난하고, t3.small(2GB)은 스왑을 붙이고
`.env`에서 메모리 한도를 낮춰야 합니다 (8-3 참고). Gradle·vite 빌드가 런타임보다 메모리를 더 씁니다.

**EBS**: **20GB 이상.** 기본 8GB로는 부족합니다 (prod 이미지 약 2.5GB + 빌드 캐시가 4GB 넘게 쌓임).

### Docker 설치 — Ubuntu

> `sudo apt install docker.io` 로 설치하지 마세요. **`docker compose` 플러그인이 딸려오지 않아**
> 이 프로젝트의 `docker compose -f a -f b` 명령이 동작하지 않습니다. 공식 저장소로 설치합니다.

```bash
# 1) 저장소 등록
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg git

sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 2) 설치
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io \
                        docker-buildx-plugin docker-compose-plugin

# 3) 부팅 시 자동 시작 + sudo 없이 사용
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
```

간단히 가려면 공식 설치 스크립트도 있습니다 (위와 같은 것을 설치합니다).

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
```

**`usermod` 후에는 반드시 재접속하세요.** 그러지 않으면 계속 `permission denied`가 납니다.

```bash
exit            # 그리고 다시 ssh 접속
# 또는 현재 셸에서만 즉시 반영
newgrp docker
```

설치 확인:

```bash
docker --version           # Docker version 27.x 이상
docker compose version     # Docker Compose version v2.x  ← v2 여야 함
docker run --rm hello-world
```



### 스왑 (2GB 이하 인스턴스만)

Ubuntu EC2에는 스왑이 없습니다. 빌드 중 OOM이 난다면 추가하세요.

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

---

## 5. 배포

```bash
cd ~                       # /home/ubuntu
git clone https://github.com/kimgaryoung/KTB-AI-hacker-toon.git
cd KTB-AI-hacker-toon
cp .env.prod.example .env
vi .env
```

`.env`를 이렇게 맞춥니다.

```bash
# ALB가 HTTPS로 받으므로 https. 브라우저가 실제로 치는 주소와 완전히 같아야 한다.
FRONTEND_BASE_URL=https://ktb-ai-hackathon-team14.com

# 브라우저↔ALB 구간이 https이므로 true.
SESSION_COOKIE_SECURE=true

# ALB가 TLS를 종료하므로 nginx는 HTTP 설정 그대로. nginx.tls.conf 아님!
NGINX_CONF=nginx.conf

KAKAO_CLIENT_ID=<카카오 REST API 키>
KAKAO_CLIENT_SECRET=<카카오 Client Secret>

# AI 연동. 아래 5-1 참고. 데모 전까지는 stub 으로 두어도 서비스는 뜬다.
AI_MODE=stub
AI_BASE_URL=http://localhost:8000
AI_SERVICE_TOKEN=replace-me

# 업로드 파일 저장 경로. 컨테이너 내부 경로이며 compose 가 볼륨으로 마운트한다.
# compose.prod.yaml 이 /app/data/uploads 로 덮어쓰므로 .env 값은 무시된다.
APP_STORAGE_ROOT=./data/uploads
```

### 5-1. AI 연동 값 — 실제로 무엇을 넣는가

| 변수 | 넣을 값 | 설명 |
|---|---|---|
| `AI_MODE` | **`stub`** 또는 **`http`** — 이 둘뿐 | 어느 구현체를 띄울지 고르는 스위치 |
| `AI_BASE_URL` | AI 서버의 **오리진만**. 예: `http://10.0.1.23:8000` | 경로(`/internal/v1`)는 코드가 붙이므로 **넣지 마세요** |
| `AI_SERVICE_TOKEN` | AI 팀에게 받은 **내부 서비스 토큰 문자열** | `Authorization: Bearer <값>` 으로 전송됩니다 |
| `APP_STORAGE_ROOT` | 배포에서는 **손대지 않아도 됩니다** | compose가 `/app/data/uploads`로 덮어씁니다 |

**`AI_MODE`는 정확히 두 값만 유효합니다.**

```
stub  →  StubAiAnalysisClient / StubChatAiClient   (기본값. 값이 없어도 이쪽)
http  →  HttpAiAnalysisClient / HttpChatAiClient   (실제 AI 서버 호출)
```

`@ConditionalOnProperty`로 빈을 고르기 때문에 **`live`, `prod`, `real` 같은 다른 값을 넣으면
어느 쪽 빈도 만들어지지 않아 백엔드가 기동에 실패합니다.** 오타도 마찬가지입니다.

**`AI_MODE=http`로 바꾸기 전 체크리스트**

`http`로 두면 백엔드가 아래 두 엔드포인트를 호출합니다 (`backend/docs/AI_INTERNAL_API_SPEC.md`).

```http
POST {AI_BASE_URL}/internal/v1/prqc-analyses        # 대화 분석
POST {AI_BASE_URL}/internal/v1/consultation-answers # 상담 답변
Authorization: Bearer {AI_SERVICE_TOKEN}
```

- `AI_BASE_URL`에 **끝 슬래시나 `/internal/v1`을 붙이지 마세요.** 코드가 `/internal/v1/...`을 이어 붙이므로 `//internal/v1/...`처럼 되어 404가 납니다.
- AI 서버가 **같은 VPC 안**이라면 사설 IP(`http://10.x.x.x:8000`)를 쓰고, 보안 그룹에서 EC2 → AI 서버 포트를 열어야 합니다. `localhost`는 백엔드 **컨테이너 자기 자신**을 가리키므로 절대 동작하지 않습니다.
- AI 서버가 별도 도메인에 있다면 `https://ai.example.com` 형태로 넣습니다.
- 호출 타임아웃은 `AI_TIMEOUT`(기본 `90s`)으로 조정합니다.

**stub으로 두면 어떻게 되나**

서비스는 정상적으로 뜨고 로그인·업로드·화면 이동까지 전부 동작하지만, 분석 점수와 상담 답변이
**미리 정해진 더미 값**으로 나옵니다. AI 서버가 준비되기 전까지는 `stub`으로 배포해도 됩니다.
전환은 `.env`만 고치고 재기동하면 됩니다.

```bash
vi .env          # AI_MODE=http, AI_BASE_URL, AI_SERVICE_TOKEN
dcp up -d backend
dcp logs -f backend
```

### 5-2. `APP_STORAGE_ROOT`

업로드된 카카오톡 대화 원문을 저장하는 **컨테이너 내부 경로**입니다.

- `compose.prod.yaml`이 `/app/data/uploads`로 덮어쓰고, 같은 경로에 named volume
  `relationship-temperature-uploads`를 마운트합니다. **`.env` 값은 컨테이너 실행 시 무시되므로 그대로 두면 됩니다.**
- `.env`의 `./data/uploads`는 컨테이너 없이 호스트에서 `./gradlew bootRun` 할 때만 쓰입니다.
- 원문 파일은 `RAW_CONVERSATION_RETENTION`(기본 24시간)이 지나면 매시 15분에 도는 정리 작업이 삭제합니다.
  AI 서버와 파일 시스템을 공유할 필요는 없습니다 — 대화 데이터는 HTTP 요청 본문으로 전달됩니다.

기동합니다.

```bash
docker compose -f compose.yaml -f compose.prod.yaml up -d --build
```

명령이 길어지니 별칭을 만들어 두면 편합니다.

```bash
echo "alias dcp='docker compose -f compose.yaml -f compose.prod.yaml'" >> ~/.bashrc
source ~/.bashrc
dcp ps
```

---

## 6. 카카오 개발자 콘솔

| 위치 | 값 |
|---|---|
| 앱 설정 > 플랫폼 > Web > 사이트 도메인 | `https://ktb-ai-hackathon-team14.com` |
| 제품 설정 > 카카오 로그인 | 활성화 **ON** |
| 제품 설정 > 카카오 로그인 > Redirect URI | `https://ktb-ai-hackathon-team14.com/api/v1/auth/kakao/callback` |
| 제품 설정 > 카카오 로그인 > 동의항목 | 닉네임, 프로필 사진 |

**경로의 `/api/v1`은 반드시 포함**해야 합니다. `SecurityConfig`의 `redirectionEndpoint`에 고정된 값입니다.

로컬 개발용 `http://localhost:5173/api/v1/auth/kakao/callback`도 함께 등록해 두면 양쪽에서 개발할 수 있습니다 (카카오는 Redirect URI를 여러 개 등록할 수 있습니다).

---

## 7. 배포 후 검증

순서대로 확인하세요. 앞이 실패하면 뒤는 볼 필요가 없습니다.

```bash
# 1) EC2 안에서 nginx가 뜨는가
dcp ps                        # 4개 전부 Up, backend 는 (healthy)
curl -s localhost/healthz     # {"status":"UP"}

# 2) ALB 대상 그룹이 healthy 인가
#    AWS 콘솔 → 대상 그룹 → 대상 탭에서 "healthy" 확인

# 3) DNS가 ALB를 가리키는가
dig +short ktb-ai-hackathon-team14.com

# 4) HTTPS가 열리는가
curl -I https://ktb-ai-hackathon-team14.com/

# 5) HTTP → HTTPS 리디렉션
curl -I http://ktb-ai-hackathon-team14.com/      # 301, Location: https://...

# 6) 스킴 전달 체인이 살아 있는가  ← 로그인 성패를 가르는 핵심
curl -s -o /dev/null -w '%{redirect_url}\n' \
  https://ktb-ai-hackathon-team14.com/oauth2/authorization/kakao
# → redirect_uri=https://ktb-ai-hackathon-team14.com/api/v1/auth/kakao/callback
#   여기가 http:// 로 나오면 100% KOE006 이 납니다.

# 7) 세션 쿠키에 Secure 가 붙는가
curl -sI https://ktb-ai-hackathon-team14.com/oauth2/authorization/kakao | grep -i set-cookie
# → rt_session=...; Path=/; Secure; HttpOnly; SameSite=Lax

# 8) 로그인 진입점이 통과하는가  ← FRONTEND_BASE_URL 불일치를 잡아내는 검사
curl -s -o /dev/null -w '%{http_code}\n' \
  "https://ktb-ai-hackathon-team14.com/api/v1/auth/kakao/authorize?redirectUri=https%3A%2F%2Fktb-ai-hackathon-team14.com"
# → 302 여야 정상.
#   400 (INVALID_REQUEST) 이면 서버 .env 의 FRONTEND_BASE_URL 이 접속 주소와 다릅니다. 10장 참고.

# 9) 브라우저로 실제 로그인 (Cmd/Ctrl + Shift + R 로 강력 새로고침 후)
```

**검사 전에 백엔드 부팅을 기다리세요.** `dcp up -d` 직후 30~60초 동안은 nginx가
**502 Bad Gateway**를 돌려줍니다. 백엔드가 아직 안 뜬 것이지 설정 오류가 아닙니다.

```bash
# healthy 가 될 때까지 대기
until [ "$(docker inspect --format '{{.State.Health.Status}}' relationship-temperature-backend-1)" = "healthy" ]; do
  echo "부팅 중..."; sleep 5
done; echo "준비 완료"
```

설정을 고친 뒤에는 **브라우저 강력 새로고침**을 하세요. 실패한 응답이나 리다이렉트가
캐시돼 있으면 고쳐도 같은 에러 화면이 계속 보입니다.

---

## 8. 주의사항

### 8-1. `-f compose.prod.yaml`을 빼먹으면 DB가 인터넷에 열립니다

가장 위험한 실수입니다. 서버에서 `docker compose up -d`만 치면 개발용 설정이 적용돼 **postgres 5432와 mongo 27017이 `0.0.0.0`에 바인딩**됩니다. 계정이 `relationship_temperature`/`relationship_temperature`라 그대로 털립니다.

`ec2-sg`가 80만 열어두면 외부에서는 막히지만, 보안 그룹 하나만 잘못 건드려도 즉시 노출됩니다. 배포 서버에서는 **항상** 두 파일을 다 지정하세요.

### 8-2. 스택을 전환할 때는 `--build`를 붙이세요

`front`는 같은 Dockerfile에서 두 가지로 빌드됩니다 — dev는 vite 개발 서버, prod는 nginx.
`compose.prod.yaml`이 prod 빌드에 `relationship-temperature-front:prod` 전용 태그를 붙여 구분하지만,
Dockerfile이나 nginx 설정을 고친 뒤에는 `--build` 없이 올리면 **예전 이미지가 그대로 뜹니다.**

```bash
dcp up -d --build        # 설정을 바꿨다면 항상 --build
```

떠 있는 것이 nginx가 맞는지 확인하는 방법:

```bash
docker inspect relationship-temperature-front-1 --format '{{.Config.Cmd}}'
# → [nginx -g daemon off;]   가 나와야 정상
# → [npm run dev ...]        가 나오면 개발용 이미지가 떠 있는 것
```

### 8-3. 백엔드 메모리 제한은 선택이 아닙니다

`backend/Dockerfile`의 JVM 옵션이 `-XX:MaxRAMPercentage=75`입니다. 컨테이너에 `mem_limit`이 없으면 **호스트 전체 RAM의 75%**를 힙 상한으로 잡아, Mongo·Postgres가 OOM으로 죽습니다.

`compose.prod.yaml`에 제한이 들어 있습니다. 적용 여부는 이렇게 확인합니다.

```bash
dcp exec backend java -XX:MaxRAMPercentage=75 -XX:+PrintFlagsFinal -version | grep MaxRAM
#   MaxRAM = 1572864000   ← 호스트 RAM이 아니라 mem_limit 값이어야 정상
```

t3.small(2GB)이면 `.env`에서 낮추세요.

```
BACKEND_MEM=900m
MONGO_MEM=600m
POSTGRES_MEM=256m
FRONT_MEM=64m
```

### 8-4. ALB 유휴 시간 초과를 180초로 올리세요

상담 화면은 `GET /api/v1/consultations/{id}/events` **SSE 스트림**을 씁니다.

- `SseEmitter` 타임아웃이 **120초**인데 ALB 기본 유휴 시간은 60초입니다. 그대로 두면 AI 응답이 늦을 때 ALB가 먼저 연결을 끊어 스트림이 죽습니다.
- 주기적 heartbeat가 없고 구독 직후 1회만 보내므로, 조용한 구간이 60초를 넘길 수 있습니다.

nginx 쪽은 이미 처리돼 있습니다 — SSE 경로만 따로 `proxy_buffering off`, `proxy_read_timeout 180s`를 겁니다. 이게 없으면 delta 이벤트가 nginx 버퍼에 갇혀 **화면이 멈춘 것처럼** 보입니다.

### 8-5. 오리진 3종은 반드시 일치해야 합니다

| 값 | 위치 | 규칙 |
|---|---|---|
| `FRONTEND_BASE_URL` | 서버 `.env` | 브라우저가 실제로 치는 주소와 **완전히** 동일 |
| 카카오 Redirect URI | 카카오 콘솔 | `{FRONTEND_BASE_URL}/api/v1/auth/kakao/callback` |
| `SESSION_COOKIE_SECURE` | 서버 `.env` | https면 `true` |

- https인데 `SESSION_COOKIE_SECURE=false`로 두면 동작은 하지만 쿠키가 평문에도 실려 나갑니다. https면 `true`로 두세요.

**`FRONTEND_BASE_URL`이 틀리면 로그인 버튼을 누르는 순간 400이 납니다.**

프론트는 로그인 진입 시 **현재 오리진**을 쿼리로 붙여 호출합니다 (`front/src/api/auth.js`).

```js
const redirectUri = encodeURIComponent(window.location.origin);
return `/api/v1/auth/kakao/authorize?redirectUri=${redirectUri}`;
```

백엔드는 그 값을 `FRONTEND_BASE_URL`과 **scheme·host·port까지** 비교하고, 하나라도 다르면
`INVALID_REQUEST`를 던집니다 (`OAuthRedirectUriValidator`).

```json
{"error":{"code":"INVALID_REQUEST","message":"요청 형식이 올바르지 않습니다.", ...}}
```

`https`인데 `http`로 적었거나, 아직 `http://localhost:5173`이 남아 있거나,
apex 도메인으로 접속하는데 값은 `www.`로 적혀 있으면 전부 여기서 걸립니다.

### 8-6. 환경변수 변경은 `restart`로 반영되지 않습니다

환경변수는 **컨테이너를 만드는 시점에 박힙니다.** `.env`를 고치고 `restart`만 하면 예전 값이
그대로 살아 있어서, 파일은 맞는데 증상이 그대로인 상황이 됩니다.

```bash
dcp restart backend                      # ✗ .env 변경 반영 안 됨
dcp up -d backend                        # ✓ 컨테이너를 새로 만듦
dcp up -d --force-recreate backend       # ✓ 변경 감지가 안 될 때 강제
```

**파일이 아니라 컨테이너 안 값을 확인하세요.**

```bash
cat -A .env | grep FRONTEND_BASE_URL                    # 1) 파일 (숨은 문자까지)
dcp config | grep FRONTEND_BASE_URL                     # 2) compose 해석 결과
dcp exec backend printenv FRONTEND_BASE_URL             # 3) 실제 주입된 값  ← 결정적
```

1번에서 `cat -A`를 쓰는 이유는 눈에 안 보이는 문자를 드러내기 위해서입니다.

```
FRONTEND_BASE_URL=https://ktb-ai-hackathon-team14.com$      정상 ($ = 줄끝)
FRONTEND_BASE_URL=https://ktb-ai-hackathon-team14.com^M$    윈도우 줄바꿈 → 값에 \r 포함
FRONTEND_BASE_URL=https://ktb-ai-hackathon-team14.com $     끝에 공백
FRONTEND_BASE_URL="https://ktb-ai-hackathon-team14.com"$    따옴표
```

### 8-7. 데이터는 named volume에 있습니다

```
relationship-temperature-postgres    앱 DB + 세션 테이블
relationship-temperature-mongo       분석 원문
relationship-temperature-uploads     업로드 파일
```

- **`down -v`는 전부 삭제합니다.** 운영에서 절대 쓰지 마세요. 정지는 `down`으로 충분합니다.
- `up -d --build`로 컨테이너를 갈아끼워도 볼륨은 유지됩니다.
- 세션이 `spring_session` 테이블(JDBC)에 저장되므로 **백엔드를 재시작해도 로그인이 유지**됩니다. 나중에 EC2를 늘려도 sticky session이 필요 없습니다. 다만 postgres 볼륨을 날리면 전원 로그아웃됩니다.

### 8-8. Flyway 마이그레이션은 자동이고, 롤백이 없습니다

컨테이너가 뜰 때 `db/migration`의 SQL이 순서대로 적용됩니다. `ddl-auto: validate`라서 엔티티와 스키마가 어긋나면 **기동 자체가 실패**하고, 백엔드가 계속 재시작합니다.

- 배포 전 백업을 먼저 받으세요.
- 실패하면 `dcp logs backend`에서 Flyway 에러를 확인합니다.

```bash
dcp exec -T postgres pg_dump -U relationship_temperature relationship_temperature > backup_$(date +%F).sql
```

### 8-9. `.env`는 서버에서 직접 만듭니다

`.gitignore`에 있어서 clone해도 따라오지 않습니다.

> **지금 저장소의 `.env.example`에 실제 카카오 REST API 키와 Client Secret이 커밋돼 있습니다.** 노출된 값으로 보고 콘솔에서 재발급한 뒤, `.env.example`은 placeholder로 바꾸세요.

### 8-10. 디스크가 조용히 찹니다

- 배포를 반복하면 빌드 캐시가 쌓입니다. `docker builder prune -f`를 주기적으로 돌리세요.
- 컨테이너 로그(json-file)는 기본 설정으로 무한히 커집니다. `/etc/docker/daemon.json`에 로테이션을 걸어두세요.

```json
{ "log-driver": "json-file", "log-opts": { "max-size": "10m", "max-file": "3" } }
```

### 8-11. 그 밖에

- **재부팅**: 서비스는 `restart: unless-stopped`라 자동 복구되지만, `systemctl enable docker`가 안 돼 있으면 도커 자체가 안 뜹니다.
- **시간대**: backend 컨테이너는 `TZ=Asia/Seoul`입니다. 업로드 파일 보존 정책(`RAW_CONVERSATION_RETENTION=24h`)과 정리 cron(매시 15분)이 이 기준으로 돕니다.
- **AI 연동**: `AI_MODE=stub`이면 실제 분석이 아니라 더미 응답입니다.
- **무중단 배포가 아닙니다**: `up -d --build`는 컨테이너를 교체하므로 수십 초 다운타임이 있습니다.

---

## 9. 운영 명령어

```bash
dcp logs -f backend               # 로그
dcp ps                            # 상태
dcp restart backend               # 단일 서비스 재시작
dcp exec postgres psql -U relationship_temperature -d relationship_temperature
dcp down                          # 정지 (데이터 유지)

git pull && dcp up -d --build     # 코드 업데이트
```

---

## 10. 트러블슈팅

### 먼저: 누가 에러를 만들었는지 확인하세요

응답 헤더의 `server:` 하나로 어느 구간에서 끊겼는지 바로 알 수 있습니다. 이게 가장 빠릅니다.

```bash
curl -sI https://ktb-ai-hackathon-team14.com/ | head -3
```

| `server:` 값 | 만든 주체 | 끊긴 구간 |
|---|---|---|
| `awselb/2.0` | ALB | ALB → EC2 (대상 그룹이 unhealthy) |
| `nginx/1.27.5` | EC2의 nginx | nginx → backend (백엔드가 없거나 죽음) |
| 응답 자체가 없음 (`000`) | — | 브라우저 → ALB (보안 그룹 / DNS) |

### 바깥에서 한 번에 점검

```bash
D=ktb-ai-hackathon-team14.com
dig +short $D                                             # ALB IP 2개가 나와야 정상
curl -sI -m 10 https://$D/ | head -3                      # 상태 코드와 server 헤더
curl -s -o /dev/null -m 10 -w '%{http_code}\n' https://$D/healthz
curl -s -o /dev/null -m 10 -w '%{redirect_url}\n' https://$D/oauth2/authorization/kakao
```

### 증상별

| 증상 | 원인 / 해결 |
|---|---|
| `sudo: 'dnf': command not found` | 인스턴스가 Ubuntu입니다. `dnf`가 아니라 `apt`를 쓰세요 (4장) |
| `docker: 'compose' is not a docker command` | Compose v2 플러그인이 없습니다. `apt install docker.io`로 깔았다면 지우고 공식 저장소로 재설치하세요 (4장) |
| `permission denied ... /var/run/docker.sock` | `usermod -aG docker $USER` 후 재접속하지 않았습니다. `exit` 후 다시 ssh 하거나 `newgrp docker` |
| `docker: command not found` | 설치 실패 또는 `systemctl enable --now docker` 누락. `sudo systemctl status docker` |
| **`503 Service Temporarily Unavailable`** (`server: awselb/2.0`) | ALB에 healthy 대상이 0개. 대상 그룹 포트가 80인지, 대상이 등록됐는지, `ec2-sg` 80 소스가 `alb-sg`인지 확인 |
| **`502 Bad Gateway`** (`server: nginx/1.27.5`) | nginx는 살아 있고 백엔드가 없음. `dcp up -d` 직후 30~60초는 정상. 계속되면 `dcp logs backend` |
| **`INVALID_REQUEST` (400)** — 로그인 버튼 클릭 시 | `.env`의 `FRONTEND_BASE_URL`이 접속 주소와 불일치. 8-5, 8-6 참고 |
| **Whitelabel Error Page (500)** at `/error` | 필터 체인 내부 예외(대개 OAuth 콜백). `dcp logs --tail 100 backend \| grep -iE "ERROR\|Exception"` |
| ALB IP의 80/443이 **closed/filtered** | `alb-sg` 인바운드 소스가 `0.0.0.0/0`이 아님 (기본값 "내 IP"). 3-2 참고 |
| 나는 접속되는데 팀원은 안 됨 | 같은 원인. `alb-sg` 소스가 `x.x.x.x/32` |
| `.env`를 고쳤는데 증상 그대로 | `restart`로는 반영되지 않음. `dcp up -d --force-recreate backend`. 8-6 참고 |
| 설정을 고쳤는데 같은 에러 화면 | 브라우저 캐시. `Cmd/Ctrl + Shift + R` 강력 새로고침 |
| `www.` 붙이면 접속 안 됨 | Route53에 www 레코드 없음. 3-5 참고 |
| 화면은 뜨는데 API만 503 | 443 리스너에 `/api/*` → 별도 대상 그룹 규칙이 남아 있음. 3-4 참고 |
| ALB 대상이 계속 `unhealthy` | `ec2-sg` 인바운드 80이 `alb-sg`로 열려 있는지, `curl localhost/healthz`가 200인지 확인. 대상 그룹 포트가 3000/8080이면 절대 healthy가 되지 않음 |
| `504 Gateway Timeout` (상담 화면) | ALB 유휴 시간 초과가 60초. 180초로 올리세요 (8-4) |
| 상담 답변이 안 나오고 멈춤 | SSE 버퍼링. `dcp exec front nginx -T \| grep proxy_buffering`로 `off` 확인 |
| `KOE006` | Redirect URI 불일치. 검증 6번을 돌려 `redirect_uri`가 `https://`이고 `/api/v1`이 있는지 확인 |
| `KOE101` | REST API 키가 아닌 다른 키(JavaScript 키 등)를 넣었거나 오타 |
| `KOE004` | 콘솔에서 카카오 로그인이 비활성 |
| 로그인이 무한 반복 | 세션 쿠키가 저장되지 않는 상태. `SESSION_COOKIE_SECURE`와 실제 스킴이 어긋났는지 확인 |
| 로그인 후 localhost로 튕김 | `FRONTEND_BASE_URL`이 아직 localhost |
| 백엔드가 재시작 반복 | Flyway 마이그레이션 실패 가능성. `dcp logs backend` |
| ACM 인증서가 ALB 목록에 없음 | 인증서 리전이 `ap-northeast-2`가 아니거나 아직 `발급됨` 상태가 아님 |
