#!/usr/bin/env bash
# Despliegue, rollback y registro de cambios de un ambiente en Kubernetes (HU-25). Lo usa a mano el operador y,
# desde HU-16, el pipeline. Habla con el clúster que indique KUBECONFIG_FILE (ruta absoluta).
#
#   ./deploy/helm/release.sh deploy <ambiente> <versión> [--commit <sha>] [--build <n>]
#         helm upgrade --install de esa versión con --atomic: si los pods no quedan listos en HELM_TIMEOUT,
#         Helm revierte solo a la revisión anterior. Cada despliegue deja su motivo en el historial de Helm.
#   ./deploy/helm/release.sh rollback <ambiente> [<revisión>] [--yes]
#         Vuelve a esa revisión; sin número, a la última revisión buena que tenga una versión distinta de la activa.
#         En prod pide --yes.
#   ./deploy/helm/release.sh history <ambiente> [--md]
#         Registro de cambios del ambiente: revisión, fecha, estado, versión y motivo (--md lo imprime en Markdown).
#   ./deploy/helm/release.sh selftest
#         Pruebas de la lógica que elige la revisión de destino (no necesitan clúster).
#
# Variables: KUBECONFIG_FILE (obligatoria salvo en selftest), SECRETS_FILE (valores secretos, ruta dentro del repo),
#            IMAGE_REGISTRY (p. ej. <cuenta>.dkr.ecr.us-east-1.amazonaws.com; en local no se usa), HELM_TIMEOUT (10m).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
CHART="deploy/helm/circleguard"
RELEASE="circleguard"
TIMEOUT="${HELM_TIMEOUT:-10m}"
HISTORY_MAX=50   # el valor por defecto de Helm (10) borraría el registro de cambios demasiado pronto

die() { printf 'release.sh: %s\n' "$*" >&2; exit 1; }
helm() { "$HERE/run.sh" helm "$@"; }

need_cluster() {
  [[ -n "${KUBECONFIG_FILE:-}" ]] || die "Falta KUBECONFIG_FILE (ruta absoluta al kubeconfig del ambiente)"
  [[ "$KUBECONFIG_FILE" == /* && -f "$KUBECONFIG_FILE" ]] || die "KUBECONFIG_FILE debe ser una ruta absoluta a un archivo existente"
  export KUBECONFIG_FILE
}

env_ok() {
  local env="$1"
  [[ -f "$HERE/circleguard/values-$env.yaml" ]] || die "Ambiente desconocido '$env' (no existe values-$env.yaml)"
}

# --- Lógica pura (se prueba en selftest) --------------------------------------------------------------------------

# stdin: salida de `helm history` (tabla separada por tabuladores). stdout: las revisiones a las que se puede volver, de la
# más nueva a la más vieja: las anteriores a la actual que quedaron `superseded` y no fueron un intento fallido.
rollback_candidates() {
  awk -F'\t' 'NR > 1 {
      gsub(/^ +| +$/, "", $1); gsub(/^ +| +$/, "", $3)
      r = $1 + 0; status[r] = $3; desc[r] = $NF
      if (r > cur) cur = r
    }
    END { for (r = cur - 1; r >= 1; r--) if (status[r] == "superseded" && desc[r] !~ /failed/) print r }'
}

# stdin: salida de `helm history`. stdout: la revisión actual (la más alta).
current_revision() {
  awk -F'\t' 'NR > 1 { gsub(/^ +| +$/, "", $1); if ($1 + 0 > m) m = $1 + 0 } END { print m + 0 }'
}

# --- Comandos -----------------------------------------------------------------------------------------------------

cmd_deploy() {
  local env="${1:?Uso: deploy <ambiente> <versión> [--commit sha] [--build n] [--set clave=valor]...}" version="${2:?Falta la versión}"; shift 2
  local commit="" build="" extra=()
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --commit) commit="${2:?}"; shift 2 ;;
      --build)  build="${2:?}"; shift 2 ;;
      --set)    extra+=(--set "${2:?}"); shift 2 ;;   # valores propios del ambiente (host del Ingress, TLS...)
      *) die "Argumento desconocido: $1" ;;
    esac
  done
  need_cluster; env_ok "$env"
  # SEALED=1: Sealed Secrets (platform.sh seal) ya creó el Secret en el cluster y el chart lo usa; no hay archivo de secretos.
  local secrets=()
  if [[ "${SEALED:-}" == 1 ]]; then
    secrets=(--set existingSecret=circleguard-secrets)
  else
    local file="${SECRETS_FILE:-}"
    [[ -n "$file" ]] || die "Falta SECRETS_FILE (valores secretos; ruta dentro del repo y fuera de git) o SEALED=1"
    [[ -f "$ROOT/$file" ]] || die "No existe $file dentro del repo"
    secrets=(-f "$file")
  fi

  local desc="deploy $version"
  [[ -n "$commit" ]] && desc+=" · commit ${commit:0:7}"
  [[ -n "$build" ]] && desc+=" · build #$build"

  local registry=()
  [[ -n "${IMAGE_REGISTRY:-}" ]] && registry=(--set "global.imageRegistry=$IMAGE_REGISTRY")

  echo "== $env: desplegando $version (--atomic, espera hasta $TIMEOUT)"
  helm upgrade --install "$RELEASE" "$CHART" --namespace "circleguard-$env" --create-namespace \
    -f "$CHART/values-$env.yaml" "${secrets[@]}" --set "global.imageTag=$version" "${registry[@]}" "${extra[@]}" \
    --atomic --timeout "$TIMEOUT" --history-max "$HISTORY_MAX" --description "$desc"
}

cmd_rollback() {
  local env="${1:?Uso: rollback <ambiente> [revisión] [--yes]}"; shift
  local target="" yes=0
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --yes) yes=1; shift ;;
      [0-9]*) target="$1"; shift ;;
      *) die "Argumento desconocido: $1" ;;
    esac
  done
  need_cluster; env_ok "$env"
  local ns="circleguard-$env" hist current
  hist="$(helm history "$RELEASE" -n "$ns" --max 256)" || die "No hay release '$RELEASE' en $ns"
  current="$(current_revision <<<"$hist")"
  if [[ -z "$target" ]]; then
    # La primera revisión buena cuya versión sea DISTINTA de la activa: tras un rollback, la revisión anterior suele ser
    # la misma versión que ya está corriendo y volver a ella no arreglaría nada.
    local active r v; active="$(active_version "$ns")"
    for r in $(rollback_candidates <<<"$hist"); do
      v="$(revision_version "$ns" "$r")"
      if [[ "$v" == "-" || "$v" != "$active" ]]; then target="$r"; break; fi
    done
  fi
  [[ -n "$target" ]] || die "No hay una revisión anterior buena a la que volver (revisión actual: $current)"
  [[ "$target" -lt "$current" ]] || die "La revisión de destino ($target) debe ser anterior a la actual ($current)"

  echo "== $env: revisión actual $current -> volver a la $target"
  if [[ "$env" == prod && $yes -ne 1 ]]; then
    die "prod pide confirmación explícita: repite el comando con --yes (o aprueba el paso de rollback en el pipeline)"
  fi
  helm rollback "$RELEASE" "$target" -n "$ns" --wait --timeout "$TIMEOUT" --history-max "$HISTORY_MAX"
  echo "== $env: versión activa: $(active_version "$ns")"
  helm history "$RELEASE" -n "$ns" --max 3
}

active_version() { helm get values "$RELEASE" -n "$1" 2>/dev/null | awk '/imageTag:/ { print $2; f = 1 } END { if (!f) print "-" }'; }

# Versión que desplegó una revisión concreta (helm history no la trae; está en los valores de esa revisión).
revision_version() {
  helm get values "$RELEASE" -n "$1" --revision "$2" 2>/dev/null | awk '/imageTag:/ { print $2; f = 1 } END { if (!f) print "-" }'
}

cmd_history() {
  local env="${1:?Uso: history <ambiente> [--md]}"; shift
  local md=0; [[ "${1:-}" == "--md" ]] && md=1
  need_cluster; env_ok "$env"
  local ns="circleguard-$env" hist
  hist="$(helm history "$RELEASE" -n "$ns" --max 256)" || die "No hay release '$RELEASE' en $ns"
  if [[ $md -eq 0 ]]; then printf '%s\n' "$hist"; return; fi

  echo "# Registro de cambios: $env"
  echo
  echo "Namespace \`$ns\` · release \`$RELEASE\` · generado con \`release.sh history $env --md\` el $(date -u +%F\ %H:%MZ)."
  echo
  echo "| Revisión | Fecha | Estado | Versión | Motivo |"
  echo "|---|---|---|---|---|"
  local rev date status desc ver
  while IFS=$'\t' read -r rev date status _ _ desc; do
    rev="$(sed -E 's/^ +| +$//g' <<<"$rev")"; date="$(sed -E 's/^ +| +$//g' <<<"$date")"
    status="$(sed -E 's/^ +| +$//g' <<<"$status")"; desc="$(sed -E 's/^ +| +$//g' <<<"$desc")"
    ver="$(revision_version "$ns" "$rev")"
    echo "| $rev | $date | $status | $ver | $desc |"
  done < <(tail -n +2 <<<"$hist")
}

# --- Pruebas de la lógica de selección ----------------------------------------------------------------------------

cmd_selftest() {
  local fails=0 got
  t() {  # nombre esperado tabla
    got="$(rollback_candidates <<<"$3" | head -1)"
    if [[ "$got" == "$2" ]]; then echo "ok   $1"; else echo "FALLA $1: esperaba '$2' y salió '$got'"; fails=$((fails + 1)); fi
  }
  local H=$'REVISION\tUPDATED\tSTATUS\tCHART\tAPP VERSION\tDESCRIPTION'
  row() { printf '%s\t%s\t%s\tcircleguard-0.1.0\t0.0.0\t%s\n' "$1" "Tue Nov 17 19:00:00 2026" "$2" "$3"; }

  t "vuelve a la revisión inmediata anterior" 2 "$H
$(row 1 superseded 'deploy v1.0.0')
$(row 2 superseded 'deploy v1.1.0')
$(row 3 deployed 'deploy v1.2.0')"
  t "salta el primer intento fallido y toma la última buena" 3 "$H
$(row 1 superseded 'Release "circleguard" failed: context deadline exceeded')
$(row 2 superseded 'Upgrade complete')
$(row 3 superseded 'Upgrade complete')
$(row 4 deployed 'Rollback to 2')"
  t "no toma una revisión fallida" 1 "$H
$(row 1 superseded 'deploy v1.0.0')
$(row 2 failed 'deploy v1.1.0')
$(row 3 deployed 'deploy v1.2.0')"
  t "con una sola revisión no hay a dónde volver" "" "$H
$(row 1 deployed 'deploy v1.0.0')"
  t "con la primera fallida y la segunda activa no hay a dónde volver" "" "$H
$(row 1 superseded 'Release "circleguard" failed: timed out waiting for the condition')
$(row 2 deployed 'deploy v1.0.0')"
  t "ordena por número y no por posición" 9 "$H
$(row 10 deployed 'deploy v1.3.0')
$(row 9 superseded 'deploy v1.2.0')
$(row 8 superseded 'deploy v1.1.0')"

  got="$(rollback_candidates <<<"$H
$(row 1 superseded 'deploy v1.0.0')
$(row 2 superseded 'deploy v1.1.0')
$(row 3 deployed 'deploy v1.2.0')" | paste -sd' ')"
  if [[ "$got" == "2 1" ]]; then echo "ok   lista los candidatos de la más nueva a la más vieja"; else echo "FALLA candidatos: '$got'"; fails=$((fails + 1)); fi

  got="$(current_revision <<<"$H
$(row 1 superseded 'a')
$(row 12 deployed 'b')
$(row 5 superseded 'c')")"
  if [[ "$got" == 12 ]]; then echo "ok   la revisión actual es la más alta"; else echo "FALLA revisión actual: $got"; fails=$((fails + 1)); fi

  [[ $fails -eq 0 ]] && echo "selftest: todo OK" || { echo "selftest: $fails fallas"; return 1; }
}

case "${1:-}" in
  deploy)   shift; cmd_deploy "$@" ;;
  rollback) shift; cmd_rollback "$@" ;;
  history)  shift; cmd_history "$@" ;;
  selftest) cmd_selftest ;;
  *) sed -n '2,19p' "$0"; exit 2 ;;
esac
