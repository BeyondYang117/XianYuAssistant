#!/usr/bin/env bash
set -euo pipefail
IMAGE="${IMAGE_REPOSITORY:-abu116/xianyu-help}:${IMAGE_TAG:-latest}"
CONTAINER_NAME="${CONTAINER_NAME:-xianyu-assistant}"
PORT="${PORT:-12400}"
DATA_DIR="${DATA_DIR:-$(pwd)/data}"
LOG_DIR="${LOG_DIR:-$DATA_DIR/logs}"
JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx1024m}"
TZ="${TZ:-Asia/Shanghai}"
mkdir -p "$DATA_DIR/dbdata" "$LOG_DIR"

# Keep the signing key outside the container so it survives upgrades.
JWT_SECRET_FILE="${JWT_SECRET_FILE:-$DATA_DIR/.jwt_secret}"
if [[ -z "${JWT_SECRET:-}" ]]; then
  if [[ -s "$JWT_SECRET_FILE" ]]; then
    JWT_SECRET="$(<"$JWT_SECRET_FILE")"
  else
    JWT_SECRET="$(openssl rand -base64 48 2>/dev/null || od -An -N48 -tx1 /dev/urandom | tr -d ' \n')"
    [[ -n "$JWT_SECRET" ]] || { echo "无法生成 JWT_SECRET，请手动设置 JWT_SECRET" >&2; exit 1; }
    umask 077
    printf '%s' "$JWT_SECRET" > "$JWT_SECRET_FILE"
    chmod 600 "$JWT_SECRET_FILE"
  fi
fi

docker pull "$IMAGE"
docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
docker run -d --name "$CONTAINER_NAME" -p "${PORT}:12400" \
  -e "SERVER_PORT=12400" -e "JAVA_OPTS=$JAVA_OPTS" -e "TZ=$TZ" -e "JWT_SECRET=$JWT_SECRET" \
  -v "$DATA_DIR/dbdata:/app/dbdata" -v "$LOG_DIR:/app/logs" \
  --restart unless-stopped "$IMAGE"
echo "XianYuAssistant 已启动: http://localhost:${PORT}"
