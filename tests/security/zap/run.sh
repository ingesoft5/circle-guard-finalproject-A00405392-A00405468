#!/usr/bin/env bash
# ZAP baseline (HU-24) contra el web del compose de un ambiente, con ZAP en un contenedor de la misma red.
#   tests/security/zap/run.sh [etiqueta]        informe en tests/security/zap/reports/<etiqueta>/
# Sale con 1 si alguna regla marcada FAIL en reglas.conf aparece (p. ej. si se pierde una cabecera de seguridad).
set -euo pipefail
LABEL="${1:-baseline}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; ROOT="$(cd "$HERE/../../.." && pwd)"
compose() { "$ROOT/deploy/compose/compose.sh" dev "$@"; }
NET="$(docker inspect -f '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}' "$(compose ps -q web)")"
OUT="$HERE/reports/$LABEL"; mkdir -p "$OUT"; chmod 777 "$OUT"; cp "$HERE/reglas.conf" "$OUT/"
MOUNT="$OUT"; command -v cygpath >/dev/null && MOUNT="$(cygpath -w "$OUT")"
MSYS_NO_PATHCONV=1 docker run --rm --network "$NET" -v "$MOUNT:/zap/wrk:rw" -u zap ghcr.io/zaproxy/zaproxy:2.15.0 \
  zap-baseline.py -t ${ZAP_TARGET:-http://web:8080} -c reglas.conf -r informe.html -J informe.json -w informe.md -I
