#!/usr/bin/env bash
# Helm y helm-unittest dentro de Docker con versiones fijas (como infra/terraform/run.sh): no hay que instalar nada.
#
#   ./deploy/helm/run.sh lint                       helm lint del chart con cada values-<ambiente>.yaml
#   ./deploy/helm/run.sh test                       pruebas de las plantillas (helm-unittest)
#   ./deploy/helm/run.sh template <ambiente> [...]  renderiza el chart (stdout); argumentos extra van a helm
#   ./deploy/helm/run.sh check                      lint + test + selftest de release.sh
#   ./deploy/helm/run.sh helm <argumentos>          cualquier comando de helm (para el pipeline y las pruebas locales)
set -euo pipefail

HELM_VERSION="3.22.1"
UNITTEST_IMAGE="helmunittest/helm-unittest:3.21.0-1.0.3"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
CHART="deploy/helm/circleguard"
SECRETS_EXAMPLE="$CHART/values-secrets.example.yaml"

die() { printf 'run.sh: %s\n' "$*" >&2; exit 1; }
command -v docker >/dev/null || die "Falta Docker"
# Jenkins corre en un contenedor: las rutas de los -v se traducen a las del host (scripts/hostpath.sh).
source "$ROOT/scripts/hostpath.sh"

# Con KUBECONFIG_FILE=<ruta> helm habla con ese clúster (el local o el de AWS); sin él, solo renderiza y valida.
helm() {
  local net=()
  [[ -n "${KUBECONFIG_FILE:-}" ]] && net=(--network host -v "$(hostpath "$KUBECONFIG_FILE"):/kube/config:ro" -e KUBECONFIG=/kube/config)
  docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp "${net[@]}" -v "$(hostpath "$ROOT"):/work" -w /work \
    "alpine/helm:$HELM_VERSION" "$@"
}

envs() { find "$HERE/circleguard" -maxdepth 1 -name 'values-*.yaml' ! -name 'values-secrets*' -printf '%f\n' | sed -E 's/^values-(.*)\.yaml$/\1/' | sort; }

cmd_lint() {
  local e
  for e in $(envs); do
    echo "== lint $e"
    helm lint "$CHART" -f "$CHART/values-$e.yaml" -f "$SECRETS_EXAMPLE" --set global.imageTag=0.0.0 --strict
  done
  echo "== lint platform"
  helm lint deploy/helm/platform --strict
  helm lint deploy/helm/platform --strict --set acme.email=equipo@example.com
}

cmd_test() {   # el chart de la aplicacion y el de los emisores de certificados
  docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -v "$(hostpath "$ROOT"):/apps" -w /apps "$UNITTEST_IMAGE" "$CHART" deploy/helm/platform
}

cmd_template() {
  local env="${1:?Uso: template <ambiente>}"; shift
  helm template circleguard "$CHART" --namespace "circleguard-$env" \
    -f "$CHART/values-$env.yaml" -f "$SECRETS_EXAMPLE" --set global.imageTag=0.0.0 "$@"
}

case "${1:-}" in
  lint)     cmd_lint ;;
  test)     cmd_test ;;
  template) shift; cmd_template "$@" ;;
  check)    cmd_lint; cmd_test; "$HERE/release.sh" selftest ;;
  helm)     shift; helm "$@" ;;
  *) sed -n '2,9p' "$0"; exit 2 ;;
esac
