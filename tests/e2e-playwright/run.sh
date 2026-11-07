#!/usr/bin/env bash
# Corre los E2E en la imagen oficial de Playwright (trae Chromium), sin instalar nada en el agente.
#   BASE_URL=http://<ip-del-ambiente> ./tests/e2e-playwright/run.sh
# Deja los reportes en tests/e2e-playwright/reports/ (html/ y junit.xml). Sale con error si falla un E2E.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_URL="${BASE_URL:-http://host.docker.internal:8090}"
IMAGE="mcr.microsoft.com/playwright:v1.49.1-noble"

docker run --rm --ipc=host --add-host=host.docker.internal:host-gateway \
  -e BASE_URL="$BASE_URL" -e CI="${CI:-}" \
  -v "$DIR:/e2e" -w /e2e "$IMAGE" \
  sh -c 'npm ci --no-audit --no-fund --loglevel=error && npx playwright test'
