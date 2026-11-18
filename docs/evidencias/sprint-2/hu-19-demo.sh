#!/usr/bin/env bash
# Demostración HU-19 (correr desde la raíz del repo con el compose de dev arriba): estado de un caso confirmado en la puerta, antes y después de reiniciar Redis.
set -u
C="./deploy/compose/compose.sh dev"; B=http://localhost:8090/api
j(){ python -c "import sys,json;print(json.load(sys.stdin)$1)"; }
login(){ curl -s -H 'Content-Type: application/json' -d "{\"username\":\"$1\",\"password\":\"password\"}" $B/auth/api/v1/auth/login; }
U=$(login health_user); UID_=$(echo "$U" | j "['anonymousId']"); UTOK=$(echo "$U" | j "['token']")
ATOK=$(login super_admin | j "['token']")
qr(){ curl -s -H "Authorization: Bearer $UTOK" $B/auth/api/v1/auth/qr/generate | j "['qrToken']"; }
gate(){ curl -s -H 'Content-Type: application/json' -d "{\"token\":\"$(qr)\"}" $B/gateway/api/v1/gate/validate; echo; }
redis(){ docker exec $($C ps -q redis) redis-cli "$@"; }
neo(){ docker exec $($C ps -q neo4j) cypher-shell -u neo4j -p "$(grep ^NEO4J_PASSWORD deploy/compose/dev/.env | cut -d= -f2-)" --format plain "$@"; }
echo "health_user = $UID_"
echo "## 1. el centro de salud confirma el caso (POST /api/v1/health/confirmed como super_admin)"
neo "MERGE (u:User {anonymousId: '$UID_'}) ON CREATE SET u.status = 'ACTIVE' RETURN u.status" | tail -1 | sed 's/^/neo4j antes: /'
curl -s -o /dev/null -w "HTTP %{http_code}\n" -H "Authorization: Bearer $ATOK" -H 'Content-Type: application/json' -d "{\"anonymousId\":\"$UID_\"}" $B/promotion/api/v1/health/confirmed
sleep 2
echo "neo4j: $(neo "MATCH (u:User {anonymousId: '$UID_'}) RETURN u.status" | tail -1) | redis: $(redis GET user:status:$UID_)"
echo -n "puerta: "; gate
echo "## 2. se reinicia Redis (sin persistencia en el compose: pierde los datos)"
$C restart redis >/dev/null 2>&1; until [ "$(redis ping 2>/dev/null)" = PONG ]; do sleep 1; done
echo "neo4j: $(neo "MATCH (u:User {anonymousId: '$UID_'}) RETURN u.status" | tail -1) | redis: '$(redis GET user:status:$UID_)'"
echo -n "puerta: "; gate
echo "redis despues de validar: '$(redis GET user:status:$UID_)' ttl=$(redis TTL user:status:$UID_)"
echo "## 3. la ruta interna no se publica por el nginx"
curl -s -o /dev/null -w "GET /api/promotion/internal/v1/users/<id>/status -> HTTP %{http_code}\n" $B/promotion/internal/v1/users/$UID_/status
echo "## 4. limpieza: el caso se resuelve"
curl -s -o /dev/null -w "resolve HTTP %{http_code}\n" -H "Authorization: Bearer $ATOK" -H 'Content-Type: application/json' -d "{\"anonymousId\":\"$UID_\",\"adminOverride\":true}" $B/promotion/api/v1/health/resolve
