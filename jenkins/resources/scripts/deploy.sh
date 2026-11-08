#!/usr/bin/env bash
# Corre EN la VM del ambiente: lo copia y ejecuta jenkins/vars/deployCompose.groovy por SSH.
# Despliega la versión indicada con docker compose, o vuelve a una anterior (rollback): es el mismo paso,
# porque las imágenes son inmutables y solo cambia IMAGE_TAG.
#   bash deploy.sh <dev|stage|prod> <version> <registro> [motivo] [commit] [url-del-build]
# Idempotente: repetirlo con la misma versión no cambia nada (compose converge al estado deseado).
set -euo pipefail

ENV_NAME="${1:?Ambiente: dev, stage o prod}"
VERSION="${2:?Versión a desplegar, p. ej. 1.2.0}"
REGISTRY="${3:?Registro Docker, p. ej. 172.31.32.157:8082}"
REASON="${4:-deploy}"
SHA="${5:-}"
BUILD="${6:-}"
BASE="${CIRCLEGUARD_HOME:-/opt/circleguard}"
COMPOSE_DIR="$BASE/compose"
ENV_FILE="$COMPOSE_DIR/$ENV_NAME/.env"
log() { printf '[deploy %s] %s\n' "$ENV_NAME" "$*"; }

[[ -d "$COMPOSE_DIR/$ENV_NAME" ]] || { echo "No existe $COMPOSE_DIR/$ENV_NAME (¿se copió deploy/compose/?)" >&2; exit 1; }
chmod +x "$COMPOSE_DIR/compose.sh"

# 1. Secretos del ambiente: se generan UNA vez en la VM y no salen de ella (ni al repo ni a Jenkins).
if [[ ! -f "$ENV_FILE" ]]; then
  log "Primer despliegue: generando los secretos del ambiente en $ENV_FILE"
  (
    umask 077
    cat >"$ENV_FILE" <<EOF
# Secretos del ambiente $ENV_NAME, generados por deploy.sh el $(date -Is). NUNCA se versionan.
# JWT_SECRET y QR_SECRET son los mismos para todos los servicios del ambiente (firman y verifican entre sí).
DB_USER=circleguard
DB_PASSWORD=$(openssl rand -hex 24)
NEO4J_PASSWORD=$(openssl rand -hex 16)
JWT_SECRET=$(openssl rand -hex 32)
QR_SECRET=$(openssl rand -hex 32)
VAULT_SECRET=$(openssl rand -hex 32)
VAULT_SALT=$(openssl rand -hex 8)
VAULT_HASH_SALT=$(openssl rand -hex 8)
WEB_PORT=80
EOF
  )
fi

# 2. Qué imagen desplegar. Si la variable ya existe se reemplaza la línea (idempotente).
set_var() {
  if grep -q "^$1=" "$ENV_FILE"; then sed -i "s|^$1=.*|$1=$2|" "$ENV_FILE"; else echo "$1=$2" >>"$ENV_FILE"; fi
}
set_var REGISTRY "$REGISTRY"
set_var IMAGE_TAG "$VERSION"
set_var CIRCLEGUARD_ENV "$ENV_NAME"

# 3. Despliegue: la MISMA imagen que en los demás ambientes; solo cambian IMAGE_TAG y la configuración.
cd "$COMPOSE_DIR"
log "Descargando imágenes $REGISTRY/circleguard/*:$VERSION"
./compose.sh "$ENV_NAME" pull --quiet
log "Aplicando el compose (up -d --wait): espera a que todo quede healthy"
./compose.sh "$ENV_NAME" up -d --wait --wait-timeout 300 --remove-orphans

# 4. Historial de versiones del ambiente (lo consulta el job de rollback).
printf '%s\t%s\t%s\t%s\t%s\n' "$(date -Is)" "$VERSION" "$REASON" "${SHA:-?}" "${BUILD:-?}" >>"$BASE/RELEASES"
log "Versión $VERSION desplegada ($REASON). Últimos despliegues:"
tail -5 "$BASE/RELEASES"
./compose.sh "$ENV_NAME" ps
