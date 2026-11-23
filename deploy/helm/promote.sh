#!/usr/bin/env bash
# Promueve una versión a un ambiente de Kubernetes (HU-16). Es lo que el pipeline llama en dev, stage y prod y sirve igual
# a mano: deja la plataforma y los secretos si el cluster es nuevo, despliega con --atomic, comprueba y archiva el registro.
#
#   KUBECONFIG_FILE=<ruta absoluta> ./deploy/helm/promote.sh <ambiente> <versión> [--commit <sha>] [--build <n>]
#
#   1. platform.sh ensure   cert-manager + Sealed Secrets y los secretos del ambiente, SOLO si faltan (un cluster nuevo)
#   2. release.sh deploy    helm upgrade --install --atomic con los secretos de Sealed Secrets (SEALED=1)
#   3. smoke.sh             si hay SMOKE_URL: sondas, versión y login real
#   4. release.sh history   si hay HISTORY_OUT: el registro de cambios en Markdown (el pipeline lo archiva)
#
# Variables: KUBECONFIG_FILE (obligatoria), IMAGE_REGISTRY (ECR; en local, "local" con la versión "local"),
#   INGRESS_HOST (p. ej. 3-84-1-2.sslip.io: enciende TLS con cert-manager), TLS_ISSUER (letsencrypt-staging por defecto;
#   letsencrypt, o circleguard-ca en local), ACME_EMAIL (solo al instalar la plataforma: habilita los emisores de
#   Let's Encrypt), SMOKE_URL, SMOKE_INSECURE=1 (certificado que el sistema no conoce, como el de staging), HISTORY_OUT.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
env="${1:?Uso: promote.sh <ambiente> <versión> [--commit <sha>] [--build <n>]}"; version="${2:?Falta la versión}"; shift 2
[[ -n "${KUBECONFIG_FILE:-}" ]] || { echo "promote.sh: falta KUBECONFIG_FILE" >&2; exit 1; }

extra=()
if [[ -n "${INGRESS_HOST:-}" ]]; then
  extra=(--set "ingress.host=$INGRESS_HOST" --set ingress.tls.enabled=true --set "ingress.tls.clusterIssuer=${TLS_ISSUER:-letsencrypt-staging}")
fi

echo "== [promote] $env <- $version"
"$HERE/platform.sh" ensure "$env" "${ACME_EMAIL:-}"
SEALED=1 "$HERE/release.sh" deploy "$env" "$version" "$@" "${extra[@]}"

if [[ -n "${SMOKE_URL:-}" ]]; then
  "$HERE/smoke.sh" "$SMOKE_URL" ${SMOKE_INSECURE:+--insecure} --version "$version"
else
  echo "[promote] sin SMOKE_URL: se omite la prueba de humo"
fi
if [[ -n "${HISTORY_OUT:-}" ]]; then
  "$HERE/release.sh" history "$env" --md > "$HISTORY_OUT"
  echo "[promote] registro de cambios en $HISTORY_OUT"
fi
