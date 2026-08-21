#!/usr/bin/env bash
# 构建并推送镜像到 Docker Hub。用法：
#   ./build-push.sh                      # 默认 linux/amd64
#   PLATFORM=linux/amd64,linux/arm64 ./build-push.sh
# 推送前需先执行 docker login
set -euo pipefail
cd "$(dirname "$0")"

IMAGE=abu116/xianyu-help
PLATFORM=${PLATFORM:-linux/amd64}

# 取 pom.xml 里的项目版本（先删掉 <parent> 段，避免拿到 Spring Boot 的版本）
VERSION=$(sed '/<parent>/,/<\/parent>/d' pom.xml | grep -m1 '<version>' | sed 's/.*<version>\(.*\)<\/version>.*/\1/')

# 多平台构建需要 docker-container driver，默认 builder 不支持
docker buildx inspect xianyu >/dev/null 2>&1 || docker buildx create --name xianyu >/dev/null

echo ">>> building $IMAGE:$VERSION ($PLATFORM)"
docker buildx build --builder xianyu \
  --platform "$PLATFORM" \
  -t "$IMAGE:$VERSION" \
  -t "$IMAGE:latest" \
  --push .

echo ">>> pushed $IMAGE:$VERSION and $IMAGE:latest"
