#!/bin/sh
set -eu
cd "$(dirname "$0")"
export CHAYEON_DOMAIN=localhost
export CHAYEON_ALLOWED_ROOMS='*'
export CHAYEON_NEW_ACCOUNTS_PER_HOUR=1000
export CHAYEON_HTTP_PORT="${CHAYEON_HTTP_PORT:-18080}"
export CHAYEON_HTTPS_PORT="${CHAYEON_HTTPS_PORT:-18443}"
export COMPOSE_PROJECT_NAME="chayeon-proof-$(date +%s)-$$"
proof_dir=$(mktemp -d)
compose() { docker compose -f docker-compose.yml -f compose.local.yaml "$@"; }
cleanup() { compose down --volumes; rm -rf "$proof_dir"; }
trap cleanup EXIT HUP INT TERM
export CHAYEON_SERVER_IMAGE="$COMPOSE_PROJECT_NAME:proof"
docker build -f ../server/Dockerfile -t "$CHAYEON_SERVER_IMAGE" ..
compose up -d --no-build
attempt=0
until compose cp caddy:/data/caddy/pki/authorities/local/root.crt "$proof_dir/root.crt"; do
  attempt=$((attempt + 1))
  [ "$attempt" -lt 30 ] || exit 1
  sleep 1
done
SSL_CERT_FILE="$proof_dir/root.crt" python3 verify.py "https://localhost:$CHAYEON_HTTPS_PORT"
