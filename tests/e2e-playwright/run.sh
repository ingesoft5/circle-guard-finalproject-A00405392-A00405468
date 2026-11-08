#!/usr/bin/env bash
# Corre los E2E de Playwright contra un ambiente. Contrato con el pipeline (jenkins/vars/e2eTests.groovy):
#   bash tests/e2e-playwright/run.sh <BASE_URL>
#   - termina con código != 0 si falla algún E2E
#   - deja tests/e2e-playwright/playwright-report/index.html (HTML) y tests/e2e-playwright/results/junit.xml (JUnit)
#
# Dentro de la imagen oficial de Playwright (trae Chromium en /ms-playwright) corre directo: así lo usa Jenkins
# con docker.image(...).inside, que comparte el workspace con el contenedor.
# Fuera de ella (en local) lanza esa misma imagen con docker run montando esta carpeta.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_URL="${1:-${BASE_URL:-http://host.docker.internal:8090}}"
IMAGE="mcr.microsoft.com/playwright:v1.49.1-noble"

if [[ -d /ms-playwright ]]; then
  cd "$DIR"
  export BASE_URL HOME="${HOME:-/tmp}" npm_config_cache="$DIR/.npm"
  npm ci --no-audit --no-fund --loglevel=error
  exec npx playwright test
fi

exec docker run --rm --ipc=host --add-host=host.docker.internal:host-gateway \
  -u "$(id -u):$(id -g)" -e HOME=/tmp -e CI="${CI:-}" \
  -v "$DIR:/e2e" -w /e2e "$IMAGE" \
  bash /e2e/run.sh "$BASE_URL"
