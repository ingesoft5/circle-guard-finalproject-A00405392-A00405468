#!/usr/bin/env bash
# Corre Terraform, tflint y Trivy dentro de Docker con versiones fijas: el equipo y Jenkins usan las mismas.
#
#   ./infra/terraform/run.sh version
#   ./infra/terraform/run.sh fmt | fmt-fix | validate | test | lint | scan | check
#   ./infra/terraform/run.sh bootstrap <comando de terraform>        p. ej. bootstrap apply
#   ./infra/terraform/run.sh <tools|dev|stage|prod> <comando>        p. ej. dev plan
#   ./infra/terraform/run.sh drift [ambiente...]                     detecta cambios hechos a mano
#
# Credenciales de AWS: las de ~/.aws (perfil default), montadas en solo lectura.
set -euo pipefail

TF_VERSION="1.17.2"
TFLINT_VERSION="v0.66.0"
TRIVY_VERSION="0.76.2"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
CACHE="${CIRCLEGUARD_CACHE:-$HOME/.cache/circleguard-iac}"
mkdir -p "$CACHE/terraform-plugins" "$CACHE/tflint" "$CACHE/trivy"

die() { printf 'run.sh: %s\n' "$*" >&2; exit 1; }
command -v docker >/dev/null || die "Falta Docker"

# Terraform en un contenedor. $1 = carpeta relativa al repo; el resto, argumentos de terraform.
tf() {
  local dir="$1"; shift
  local tty=()
  [[ -t 0 && -t 1 ]] && tty=(-it)
  docker run --rm "${tty[@]}" -u "$(id -u):$(id -g)" \
    -e HOME=/tmp -e TF_IN_AUTOMATION=1 -e TF_PLUGIN_CACHE_DIR=/plugin-cache \
    -e AWS_SHARED_CREDENTIALS_FILE=/aws/credentials -e AWS_CONFIG_FILE=/aws/config \
    -e AWS_REGION="${AWS_REGION:-us-east-1}" \
    -v "$ROOT:/work" -v "$HOME/.aws:/aws:ro" -v "$CACHE/terraform-plugins:/plugin-cache" \
    -w "/work/$dir" "hashicorp/terraform:$TF_VERSION" "$@"
}

modules() { find "$HERE/modules" -mindepth 1 -maxdepth 1 -type d -printf 'infra/terraform/modules/%f\n' 2>/dev/null | sort; }
envs()    { find "$HERE/envs" -mindepth 1 -maxdepth 1 -type d -printf 'infra/terraform/envs/%f\n' 2>/dev/null | sort; }

cmd_fmt()     { tf infra/terraform fmt -check -recursive -diff; }
cmd_fmt_fix() { tf infra/terraform fmt -recursive; }

cmd_validate() {
  local d
  for d in infra/terraform/bootstrap $(modules) $(envs); do
    [[ -d "$ROOT/$d" ]] || continue
    echo "== validate $d"
    tf "$d" init -backend=false -input=false >/dev/null
    tf "$d" validate
  done
}

cmd_test() {
  local d
  for d in $(modules); do
    [[ -d "$ROOT/$d/tests" ]] || continue
    echo "== test $d"
    tf "$d" init -backend=false -input=false >/dev/null
    tf "$d" test
  done
}

cmd_lint() {
  local run=(docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -e TFLINT_PLUGIN_DIR=/plugins
    -v "$ROOT:/data" -v "$CACHE/tflint:/plugins" -w /data/infra/terraform
    "ghcr.io/terraform-linters/tflint:$TFLINT_VERSION")
  "${run[@]}" --init --config=/data/.tflint.hcl
  "${run[@]}" --recursive --config=/data/.tflint.hcl
}

cmd_scan() {
  docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -e TRIVY_CACHE_DIR=/cache \
    -v "$ROOT:/work" -v "$CACHE/trivy:/cache" -w /work \
    "aquasec/trivy:$TRIVY_VERSION" config --severity HIGH,CRITICAL --exit-code 1 \
    --skip-dirs '**/.terraform' infra/terraform
}

cmd_check() { cmd_fmt; cmd_validate; cmd_test; cmd_lint; cmd_scan; }

# Operación real sobre bootstrap/ o sobre un ambiente (usa el backend remoto de backend.hcl).
run_env() {
  local rel="$1"; shift
  local dir="infra/terraform/$rel"
  [[ -d "$ROOT/$dir" ]] || die "No existe $dir"
  if [[ "$rel" == bootstrap ]]; then
    tf "$dir" init -input=false >/dev/null
  else
    [[ -f "$HERE/backend.hcl" ]] || die "Falta infra/terraform/backend.hcl. Crealo con: ./infra/terraform/run.sh bootstrap output -raw backend_config > infra/terraform/backend.hcl"
    tf "$dir" init -input=false -reconfigure -backend-config=../../backend.hcl >/dev/null
  fi
  tf "$dir" "$@"
}

# Drift: compara lo desplegado con el código. Sale 0 si coincide, 2 si algo cambió a mano y 1 si no pudo planificar.
cmd_drift() {
  local targets=("$@") rc=0 code env
  [[ ${#targets[@]} -gt 0 ]] || targets=(tools dev stage prod)
  for env in "${targets[@]}"; do
    echo "== drift $env"
    code=0
    run_env "envs/$env" plan -detailed-exitcode -input=false -lock=false >/dev/null || code=$?
    case $code in
      0) echo "OK: $env coincide con el codigo" ;;
      2) echo "DRIFT: $env difiere del codigo (mira el detalle con: ./infra/terraform/run.sh $env plan)"; rc=2 ;;
      *) echo "ERROR: no se pudo planificar $env"; [[ $rc -ge 1 ]] || rc=1 ;;
    esac
  done
  return "$rc"
}

case "${1:-}" in
  version)   tf infra/terraform version ;;
  fmt)       cmd_fmt ;;
  fmt-fix)   cmd_fmt_fix ;;
  validate)  cmd_validate ;;
  test)      cmd_test ;;
  lint)      cmd_lint ;;
  scan)      cmd_scan ;;
  check)     cmd_check ;;
  drift)     shift; cmd_drift "$@" ;;
  bootstrap) shift; run_env bootstrap "$@" ;;
  tools|dev|stage|prod) env="$1"; shift; run_env "envs/$env" "$@" ;;
  *) sed -n '2,11p' "$0"; exit 2 ;;
esac
