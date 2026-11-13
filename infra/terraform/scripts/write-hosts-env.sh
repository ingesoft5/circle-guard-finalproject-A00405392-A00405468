#!/usr/bin/env bash
# Escribe infra/bootstrap/.state/hosts.env con las IP de los ambientes que Terraform creó, en el
# mismo formato que bootstrap-cloud.sh, para que bootstrap-tools.sh funcione sin cambios.
#
#   ./infra/terraform/scripts/write-hosts-env.sh tools dev stage prod
#
# HOSTS_OUT cambia el archivo de salida (útil para probar sin pisar el real).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN="$HERE/../run.sh"
OUT="${HOSTS_OUT:-$HERE/../../bootstrap/.state/hosts.env}"

[[ $# -gt 0 ]] || { echo "Uso: $0 <tools|dev|stage|prod>..." >&2; exit 2; }
command -v jq >/dev/null || { echo "Falta jq" >&2; exit 1; }

mkdir -p "$(dirname "$OUT")"
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

for env in "$@"; do
  json="$("$RUN" "$env" output -json)"
  key="${env^^}"
  {
    printf '%s_INSTANCE_ID=%s\n' "$key" "$(jq -r .instance_id.value <<<"$json")"
    printf '%s_PUBLIC_IP=%s\n' "$key" "$(jq -r .public_ip.value <<<"$json")"
    printf '%s_PRIVATE_IP=%s\n' "$key" "$(jq -r .private_ip.value <<<"$json")"
  } >>"$tmp"
done

chmod 600 "$tmp"
mv "$tmp" "$OUT"
trap - EXIT
echo "Escribi $OUT con: $*"
