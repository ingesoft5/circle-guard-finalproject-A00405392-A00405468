#!/usr/bin/env bash
# HU-21: provoca cada alerta crítica en el compose de dev y espera a que Alertmanager la dispare y la resuelva.
# Correr desde la raíz del repo con: ./deploy/compose/compose.sh dev -f deploy/compose/observability.yml up -d --wait
#   bash docs/evidencias/sprint-2/hu-21-provocar-alertas.sh [identity|latencia|kafka|todas]
# Opcional: SHOT=<comando> se ejecuta con OUT=<archivo>.png mientras cada alerta está disparada (captura de Alertmanager).
set -uo pipefail
WHAT="${1:-todas}"
C="./deploy/compose/compose.sh dev -f deploy/compose/observability.yml"
PORT="$(grep -E '^WEB_PORT=' deploy/compose/dev/.env | cut -d= -f2-)"; B="http://localhost:${PORT:-80}/api"
AM="http://127.0.0.1:$(grep -E '^ALERTMANAGER_PORT=' deploy/compose/dev/.env | cut -d= -f2- || true)"; [ "$AM" = "http://127.0.0.1:" ] && AM="http://127.0.0.1:9093"
log() { echo "[$(date +%H:%M:%S)] $*"; }
active() { curl -s "$AM/api/v2/alerts?active=true&silenced=false&inhibited=false" | python -c "
import sys,json; print(' '.join(sorted({a['labels']['alertname'] for a in json.load(sys.stdin)})))"; }
wait_for() {  # wait_for <alerta> <present|absent> <segundos>
  local end=$(( $(date +%s) + $3 ))
  while [ "$(date +%s)" -lt "$end" ]; do
    if [ "$2" = present ] && [[ " $(active) " == *" $1 "* ]]; then log "Alertmanager: $1 DISPARADA"; return 0; fi
    if [ "$2" = absent ] && [[ " $(active) " != *" $1 "* ]]; then log "Alertmanager: $1 RESUELTA"; return 0; fi
    sleep 5
  done
  log "TIEMPO AGOTADO esperando $1 $2"; return 1
}
traffic() {  # logins en segundo plano (uno cada 2 s, sin esperar la respuesta)
  ( while true; do curl -s -o /dev/null --max-time 45 -H 'Content-Type: application/json' \
      -d '{"username":"health_user","password":"password"}' "$B/auth/api/v1/auth/login" & sleep 2; done ) &
  TRAFFIC=$!
}
stop_traffic() { kill "$TRAFFIC" 2>/dev/null; wait "$TRAFFIC" 2>/dev/null; }
shot() { [ -n "${SHOT:-}" ] && OUT="$1" bash -c "$SHOT"; }
# Antes de provocar una falla, su alerta tiene que estar inactiva: si no, la evidencia no prueba nada
# (en la primera corrida LatenciaP95Alta seguía disparada por la caída de identity del escenario anterior).
healthy_first() { for a in "$@"; do log "esperando que $a esté inactiva antes de provocarla"; wait_for "$a" absent 600 || exit 1; done; }

if [ "$WHAT" = identity ] || [ "$WHAT" = todas ]; then
  log "== 1. identity caído (ServicioCaido y CircuitoAbierto)"
  healthy_first ServicioCaido CircuitoAbierto
  traffic; sleep 10
  $C stop identity >/dev/null 2>&1; log "identity detenido"
  wait_for CircuitoAbierto present 240; wait_for ServicioCaido present 240
  shot hu-21-alertmanager-identity.png
  $C start identity >/dev/null 2>&1; log "identity encendido"
  wait_for ServicioCaido absent 300; wait_for CircuitoAbierto absent 300
  stop_traffic
fi

if [ "$WHAT" = latencia ] || [ "$WHAT" = todas ]; then
  log "== 2. base de datos congelada (LatenciaP95Alta)"
  healthy_first LatenciaP95Alta
  traffic; sleep 10
  docker pause "$($C ps -q postgres)" >/dev/null; log "postgres congelado (docker pause)"
  wait_for LatenciaP95Alta present 420
  shot hu-21-alertmanager-latencia.png
  docker unpause "$($C ps -q postgres)" >/dev/null; log "postgres reanudado"
  wait_for LatenciaP95Alta absent 420
  stop_traffic
fi

if [ "$WHAT" = kafka ] || [ "$WHAT" = todas ]; then
  log "== 3. promotion detenido con cuestionarios llegando (LagKafkaAlto)"
  healthy_first LagKafkaAlto
  QID=$(curl -s "$B/form/api/v1/questionnaires/active" | python -c "import sys,json;print(json.load(sys.stdin)['questions'][0]['id'])")
  $C stop promotion >/dev/null 2>&1; log "promotion detenido"
  for i in $(seq 1 150); do
    curl -s -o /dev/null -H 'Content-Type: application/json' \
      -d "{\"anonymousId\":\"$(python -c 'import uuid;print(uuid.uuid4())')\",\"responses\":{\"$QID\":\"NO\"}}" "$B/form/api/v1/surveys"
  done
  log "150 cuestionarios enviados sin consumidor"
  wait_for LagKafkaAlto present 300
  shot hu-21-alertmanager-kafka.png
  $C start promotion >/dev/null 2>&1; log "promotion encendido"
  wait_for LagKafkaAlto absent 420
fi

log "== notificaciones que recibió el receptor (alert-sink)"
$C logs --no-log-prefix alert-sink 2>/dev/null | grep -E "DISPARADA|RESUELTA"
