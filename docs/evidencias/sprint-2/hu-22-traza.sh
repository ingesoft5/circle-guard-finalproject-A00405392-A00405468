#!/usr/bin/env bash
# HU-22: envía un cuestionario con síntomas con una cabecera W3C traceparent propia y busca esa traza en Jaeger y sus
# logs en Loki. Correr desde la raíz del repo con:
#   ./deploy/compose/compose.sh dev -f deploy/compose/observability.yml up -d --wait
#   bash docs/evidencias/sprint-2/hu-22-traza.sh
set -euo pipefail
envv() { grep -E "^$1=" deploy/compose/dev/.env | cut -d= -f2- || true; }
PORT="$(envv WEB_PORT)"; B="http://localhost:${PORT:-80}/api"
JAEGER="http://127.0.0.1:$(envv JAEGER_PORT)"; [ "$JAEGER" = "http://127.0.0.1:" ] && JAEGER="http://127.0.0.1:16686"
GRAFANA="http://127.0.0.1:$(envv GRAFANA_PORT)"; [ "$GRAFANA" = "http://127.0.0.1:" ] && GRAFANA="http://127.0.0.1:3000"
GAUTH="admin:$(envv GRAFANA_ADMIN_PASSWORD)"
compose() { ./deploy/compose/compose.sh dev -f deploy/compose/observability.yml "$@"; }
neo() { docker exec "$(compose ps -q neo4j)" cypher-shell -u neo4j -p "$(envv NEO4J_PASSWORD)" --format plain "$@"; }
py() { python -c "$1"; }

QID=$(curl -sf "$B/form/api/v1/questionnaires/active" | py "import sys,json;print(json.load(sys.stdin)['questions'][0]['id'])")
PERSON=$(py 'import uuid;print(uuid.uuid4())')
TRACE=$(py 'import secrets;print(secrets.token_hex(16))'); SPAN=$(py 'import secrets;print(secrets.token_hex(8))')
echo "== persona $PERSON con 2 contactos en el grafo"
neo "MERGE (p:User {anonymousId: '$PERSON'}) SET p.status = 'ACTIVE'
     WITH p UNWIND range(1, 2) AS k
     CREATE (c:User {anonymousId: randomUUID(), status: 'ACTIVE'})
     CREATE (p)-[:ENCOUNTERED {startTime: timestamp(), duration: 900, isValid: true, locationId: 'L-201'}]->(c)
     RETURN count(*)" | tail -1 >/dev/null

echo "== cuestionario con síntomas, traceparent 00-$TRACE-$SPAN-01"
curl -sf -o /dev/null -w "form respondió HTTP %{http_code}\n" -H 'Content-Type: application/json' \
  -H "traceparent: 00-$TRACE-$SPAN-01" \
  -d "{\"anonymousId\":\"$PERSON\",\"responses\":{\"$QID\":\"YES\"}}" "$B/form/api/v1/surveys"

echo "== esperando la traza en Jaeger (form -> promotion -> notification)"
for i in $(seq 1 30); do
  SERVICES=$(curl -s "$JAEGER/api/traces/$TRACE" | py "
import sys,json
d=json.load(sys.stdin).get('data') or []
print(' '.join(sorted({p['serviceName'] for t in d for p in t['processes'].values()})))" 2>/dev/null || true)
  # Cada servicio exporta por lotes (~5 s): se espera a que estén los tres y un lote más.
  [[ "$SERVICES" == *form* && "$SERVICES" == *promotion* && "$SERVICES" == *notification* ]] && { sleep 6; break; }
  sleep 2
done
echo "servicios en la traza: $SERVICES"
curl -s "$JAEGER/api/traces/$TRACE" | py "
import sys,json
t=json.load(sys.stdin)['data'][0]; procs=t['processes']
spans=sorted(t['spans'], key=lambda s: s['startTime']); t0=spans[0]['startTime']
print(f'{len(spans)} spans en la traza {t[\"traceID\"]}:')
for s in spans:
    print(f'  +{(s[\"startTime\"]-t0)/1000:8.1f} ms  {s[\"duration\"]/1000:8.1f} ms  {procs[s[\"processID\"]][\"serviceName\"]:32}  {s[\"operationName\"]}')"

echo "== logs con ese trace_id en Loki (vía Grafana)"
sleep 5
START=$(( ($(date +%s) - 600) * 1000000000 )); END=$(( $(date +%s) * 1000000000 ))
curl -s -u "$GAUTH" -G "$GRAFANA/api/datasources/proxy/uid/loki/loki/api/v1/query_range" \
  --data-urlencode "query={service=~\"circleguard-.+\"} |= \"$TRACE\"" --data-urlencode "start=$START" --data-urlencode "end=$END" \
  --data-urlencode "limit=50" | py "
import sys,json
res=json.load(sys.stdin)['data']['result']
lines=sorted((v[0], s['stream'].get('service','?'), json.loads(v[1])) for s in res for v in s['values'])
print(f'{len(lines)} líneas de log con trace_id:')
for _, svc, l in lines:
    print(f'  {svc:32} {l.get(\"level\"):5} span={l.get(\"span_id\")}  {l.get(\"message\")[:90]}')"
echo "trace_id=$TRACE"
