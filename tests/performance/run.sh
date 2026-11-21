#!/usr/bin/env bash
# Corre la prueba de carga (HU-24) contra el compose de un ambiente, con Locust en un contenedor de la misma red.
#   tests/performance/run.sh [usuarios] [duración] [etiqueta]      p. ej. tests/performance/run.sh 60 3m base
# CLASSES=LoginUser mide solo el login (escenario aparte). SPAWN_RATE cambia la rampa (usuarios/s, por defecto 2).
# Deja el informe HTML y los CSV en tests/performance/reports/<etiqueta>/ y sale con 1 si no se cumple el objetivo.
set -euo pipefail
USERS="${1:-60}"; DURATION="${2:-3m}"; LABEL="${3:-run}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; ROOT="$(cd "$HERE/../.." && pwd)"
compose() { "$ROOT/deploy/compose/compose.sh" dev "$@"; }   # función: la ruta del repo puede tener espacios
NET="$(docker inspect -f '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}' "$(compose ps -q web)")"
mkdir -p "$HERE/reports/$LABEL"
MOUNT="$HERE"; command -v cygpath >/dev/null && MOUNT="$(cygpath -w "$HERE")"   # Git Bash en Windows
MSYS_NO_PATHCONV=1 docker run --rm --network "$NET" -v "$MOUNT:/mnt/locust" -w /mnt/locust locustio/locust:2.32.3 \
  -f locustfile.py --headless --host http://web:8080 -u "$USERS" -r "${SPAWN_RATE:-2}" -t "$DURATION" --only-summary \
  --html "reports/$LABEL/informe.html" --csv "reports/$LABEL/locust" ${CLASSES:-GateUser SurveyUser}
