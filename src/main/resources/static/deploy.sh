#!/usr/bin/env bash
set -euo pipefail
IMAGE="${IMAGE_REPOSITORY:-abu116/xianyu-help}:${IMAGE_TAG:-latest}"
CONTAINER_NAME="${CONTAINER_NAME:-xianyu-assistant}"
PORT="${PORT:-12400}"
DATA_DIR="${DATA_DIR:-$(pwd)/data}"
mkdir -p "$DATA_DIR/dbdata" "$DATA_DIR/logs"
docker pull "$IMAGE"
docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
docker run -d --name "$CONTAINER_NAME" -p "${PORT}:12400" -v "$DATA_DIR/dbdata:/app/dbdata" -v "$DATA_DIR/logs:/app/logs" --restart unless-stopped "$IMAGE"
echo "XianYuAssistant 已启动: http://localhost:${PORT}"
