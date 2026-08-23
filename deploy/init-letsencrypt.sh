#!/usr/bin/env bash
# Let's Encrypt 인증서 최초 발급 스크립트. EC2에서 저장소 루트 기준으로 실행한다.
#
#   EMAIL=you@example.com ./deploy/init-letsencrypt.sh
#
# 전제:
#   - 도메인 A레코드가 이 서버 IP를 가리킬 것
#   - 보안 그룹에서 80, 443 인바운드가 열려 있을 것 (HTTP-01 챌린지는 80을 쓴다)
set -euo pipefail

DOMAIN="${DOMAIN:-ktb-ai-hackathon-team14.com}"
EMAIL="${EMAIL:-}"
COMPOSE="docker compose -f compose.yaml -f compose.prod.yaml"

cd "$(dirname "$0")/.."

if [ -z "$EMAIL" ]; then
  echo "EMAIL 환경변수가 필요합니다 (만료 알림 수신용)." >&2
  echo "  예: EMAIL=you@example.com $0" >&2
  exit 1
fi

mkdir -p deploy/certbot/www deploy/certbot/conf

echo "[1/4] HTTP 설정으로 nginx 기동 (챌린지 응답용)"
NGINX_CONF=nginx.conf $COMPOSE up -d --build front

echo "[2/4] 챌린지 경로 도달 확인"
for i in $(seq 1 20); do
  code=$(curl -s -o /dev/null -m 5 -w '%{http_code}' "http://${DOMAIN}/.well-known/acme-challenge/ping" || true)
  # 404면 nginx는 살아있고 파일만 없는 것 → 정상
  if [ "$code" = "404" ] || [ "$code" = "200" ]; then
    echo "  ok (HTTP $code)"
    break
  fi
  if [ "$i" = "20" ]; then
    echo "  ${DOMAIN}:80 에 도달하지 못했습니다. DNS와 보안 그룹(80 인바운드)을 확인하세요." >&2
    exit 1
  fi
  sleep 3
done

echo "[3/4] 인증서 발급"
$COMPOSE --profile certbot run --rm certbot certonly \
  --webroot -w /var/www/certbot \
  -d "$DOMAIN" \
  --email "$EMAIL" \
  --agree-tos --no-eff-email --non-interactive

echo "[4/4] TLS 설정으로 전환"
cat <<MSG

발급 완료. 이제 .env 를 아래처럼 바꾸고 다시 올리세요.

  FRONTEND_BASE_URL=https://${DOMAIN}
  SESSION_COOKIE_SECURE=true
  NGINX_CONF=nginx.tls.conf

  $COMPOSE up -d

카카오 콘솔 Redirect URI 에 아래 값이 등록돼 있어야 합니다.

  https://${DOMAIN}/api/v1/auth/kakao/callback

갱신은 아래를 cron 에 걸어두면 됩니다 (인증서는 90일 만료).

  0 4 * * 1 cd $(pwd) && $COMPOSE --profile certbot run --rm certbot renew && $COMPOSE exec front nginx -s reload
MSG
