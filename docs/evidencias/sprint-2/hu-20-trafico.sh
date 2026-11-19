#!/usr/bin/env bash
# HU-20: genera tráfico real contra el compose de dev para que las métricas y los dashboards tengan datos.
# Correr desde la raíz del repo con el compose de dev arriba (con observability.yml):
#   bash docs/evidencias/sprint-2/hu-20-trafico.sh [personas] [minutos]
# Con [minutos] > 0, después de la ronda inicial sigue generando carga (cuestionarios y check-ins) cada 10 s
# durante ese tiempo, para que los paneles de tasa (rate) y p95 tengan una curva y no un solo pico.
# Todo pasa por las APIs públicas (nginx en WEB_PORT), salvo la semilla del grafo de contactos, que se
# escribe en Neo4j porque no hay una API pública para crear encuentros en lote.
set -euo pipefail
N="${1:-8}"
MINUTES="${2:-0}"
C="./deploy/compose/compose.sh dev"
PORT="$(grep -E '^WEB_PORT=' deploy/compose/dev/.env | cut -d= -f2-)"; PORT="${PORT:-80}"
B="http://localhost:${PORT}/api"
j() { python -c "import sys,json;d=json.load(sys.stdin);print($1)"; }
login() { curl -sf -H 'Content-Type: application/json' -d "{\"username\":\"$1\",\"password\":\"password\"}" "$B/auth/api/v1/auth/login"; }
neo() { docker exec "$($C ps -q neo4j)" cypher-shell -u neo4j -p "$(grep ^NEO4J_PASSWORD deploy/compose/dev/.env | cut -d= -f2-)" --format plain "$@"; }

echo "## cuestionario activo"
if ! curl -sf "$B/form/api/v1/questionnaires/active" >/dev/null; then
  # Sin tildes en el JSON: Git Bash en Windows no las envía en UTF-8 (el detector de síntomas las normaliza igual).
  id=$(curl -sf -H 'Content-Type: application/json' -d '{"title":"Autoevaluacion de salud","version":1,"isActive":false,
    "questions":[{"text":"Tienes fiebre (38 C o mas)?","type":"YES_NO","orderIndex":1},
                 {"text":"Tienes tos o dificultad para respirar?","type":"YES_NO","orderIndex":2}]}' \
    "$B/form/api/v1/questionnaires" | j "d['id']")
  curl -sf -X POST "$B/form/api/v1/questionnaires/$id/activate" >/dev/null
fi
QID=$(curl -sf "$B/form/api/v1/questionnaires/active" | j "d['questions'][0]['id']")

echo "## grafo de contactos: $N personas, cada una con 3 contactos"
PEOPLE=$(python -c "import uuid;print(' '.join(str(uuid.uuid4()) for _ in range($N)))")
neo "UNWIND split('$PEOPLE', ' ') AS id
     MERGE (p:User {anonymousId: id}) SET p.status = 'ACTIVE'
     WITH p UNWIND range(1, 3) AS k
     CREATE (c:User {anonymousId: randomUUID(), status: 'ACTIVE'})
     CREATE (p)-[:ENCOUNTERED {startTime: timestamp(), duration: 900, isValid: true, locationId: 'L-201'}]->(c)
     RETURN count(*) AS encuentros" | tail -1

echo "## cuestionarios: $N con síntomas y $N sin síntomas"
for id in $PEOPLE; do
  curl -sf -o /dev/null -H 'Content-Type: application/json' \
    -d "{\"anonymousId\":\"$id\",\"responses\":{\"$QID\":\"YES\"}}" "$B/form/api/v1/surveys"
  curl -sf -o /dev/null -H 'Content-Type: application/json' \
    -d "{\"anonymousId\":\"$(python -c 'import uuid;print(uuid.uuid4())')\",\"responses\":{\"$QID\":\"NO\"}}" "$B/form/api/v1/surveys"
done

echo "## puerta: health_user entra, QR falsos, y staff_guard confirmado no entra"
UTOK=$(login health_user | j "d['token']")
G=$(login staff_guard); GID=$(echo "$G" | j "d['anonymousId']"); GTOK=$(echo "$G" | j "d['token']")
ATOK=$(login super_admin | j "d['token']")
qr() { curl -sf -H "Authorization: Bearer $1" "$B/auth/api/v1/auth/qr/generate" | j "d['qrToken']"; }
gate() { curl -sf -H 'Content-Type: application/json' -d "{\"token\":\"$1\"}" "$B/gateway/api/v1/gate/validate" | j "d['status']"; }
neo "MERGE (u:User {anonymousId: '$GID'}) SET u.status = 'ACTIVE' RETURN u.status" >/dev/null
curl -sf -o /dev/null -H "Authorization: Bearer $ATOK" -H 'Content-Type: application/json' \
  -d "{\"anonymousId\":\"$GID\"}" "$B/promotion/api/v1/health/confirmed"
for i in $(seq 1 20); do r1=$(gate "$(qr "$UTOK")"); done; echo "health_user: $r1 (x20)"
for i in $(seq 1 6);  do r2=$(gate "qr-falso-$i"); done;   echo "QR falso: $r2 (x6)"
for i in $(seq 1 4);  do r3=$(gate "$(qr "$GTOK")"); done; echo "staff_guard confirmado: $r3 (x4)"

if [ "$MINUTES" -gt 0 ]; then
  echo "## carga sostenida durante $MINUTES min"
  end=$(( $(date +%s) + MINUTES * 60 )); rounds=0
  while [ "$(date +%s)" -lt "$end" ]; do
    id=$(python -c 'import uuid;print(uuid.uuid4())')
    neo "MERGE (p:User {anonymousId: '$id'}) SET p.status = 'ACTIVE'
         CREATE (c:User {anonymousId: randomUUID(), status: 'ACTIVE'})
         CREATE (p)-[:ENCOUNTERED {startTime: timestamp(), duration: 900, isValid: true, locationId: 'L-201'}]->(c)" >/dev/null
    curl -sf -o /dev/null -H 'Content-Type: application/json' \
      -d "{\"anonymousId\":\"$id\",\"responses\":{\"$QID\":\"YES\"}}" "$B/form/api/v1/surveys"
    for i in 1 2 3 4 5; do gate "$(qr "$UTOK")" >/dev/null; done
    gate "qr-falso" >/dev/null; gate "$(qr "$GTOK")" >/dev/null
    rounds=$((rounds + 1)); sleep 10
  done
  echo "rondas: $rounds (cada una: 1 cuestionario con síntomas, 7 check-ins)"
fi

echo "## limpieza: se resuelve el caso de staff_guard"
curl -sf -o /dev/null -w "resolve HTTP %{http_code}\n" -H "Authorization: Bearer $ATOK" -H 'Content-Type: application/json' \
  -d "{\"anonymousId\":\"$GID\",\"adminOverride\":true}" "$B/promotion/api/v1/health/resolve"
