#!/usr/bin/env bash
# Lo que el cluster necesita ademas de la aplicacion (HU-23): cert-manager (certificados TLS) y Sealed Secrets (secretos que
# pueden vivir en git porque solo ese cluster sabe descifrarlos). Helm y kubectl corren en Docker, como en run.sh.
#
#   KUBECONFIG_FILE=<ruta> ./deploy/helm/platform.sh install [correo]   instala cert-manager, Sealed Secrets y los emisores;
#                                                                       con correo agrega los de Let's Encrypt
#   KUBECONFIG_FILE=<ruta> ./deploy/helm/platform.sh seal <ambiente> [namespace]
#                                                                       genera los secretos de la aplicacion al azar, los sella
#                                                                       con la llave publica del cluster, escribe
#                                                                       deploy/sealed/<ambiente>.yaml (o $SEALED_OUT) y lo aplica
#
# El texto plano de los secretos solo existe en una tuberia: nunca se escribe en disco ni se imprime. Con el Secret
# circleguard-secrets ya en el cluster, el chart se despliega con --set existingSecret=circleguard-secrets.
set -euo pipefail

CERT_MANAGER_VERSION="v1.21.1"
SEALED_SECRETS_CHART="2.22.0"                 # controlador 0.41.2
KUBESEAL_VERSION="0.41.2"
KUBESEAL_SHA256="666b5db6788ad1dedb56e2d97064e1cc3779aa77bbd6f4b56b62beeb1941ed79"   # kubeseal-0.41.2-linux-amd64.tar.gz
K3S_IMAGE="rancher/k3s:v1.37.2-k3s1"          # trae kubectl; es la misma version que instala Terraform

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
CACHE="${CIRCLEGUARD_CACHE:-$HOME/.cache/circleguard-iac}"

die() { printf 'platform.sh: %s\n' "$*" >&2; exit 1; }
command -v docker >/dev/null || die "Falta Docker"
[[ -n "${KUBECONFIG_FILE:-}" && -f "$KUBECONFIG_FILE" ]] || die "Pasa KUBECONFIG_FILE=<ruta al kubeconfig del cluster>"
# Jenkins corre en un contenedor: las rutas de los -v se traducen a las del host (scripts/hostpath.sh).
source "$ROOT/scripts/hostpath.sh"

helm() { "$HERE/run.sh" helm "$@"; }
kubectl() {
  docker run --rm -i --network host -v "$(hostpath "$KUBECONFIG_FILE"):/kube/config:ro" -e KUBECONFIG=/kube/config \
    -v "$(hostpath "$ROOT"):/work:ro" -w /work --entrypoint kubectl "$K3S_IMAGE" "$@"
}

cmd_install() {
  local email="${1:-}"
  echo "== cert-manager $CERT_MANAGER_VERSION"
  helm upgrade --install cert-manager cert-manager --repo https://charts.jetstack.io --version "$CERT_MANAGER_VERSION" \
    --namespace cert-manager --create-namespace --set crds.enabled=true --wait --timeout 5m
  echo "== Sealed Secrets (chart $SEALED_SECRETS_CHART)"
  helm upgrade --install sealed-secrets sealed-secrets --repo https://bitnami.github.io/sealed-secrets --version "$SEALED_SECRETS_CHART" \
    --namespace kube-system --set-string fullnameOverride=sealed-secrets-controller --wait --timeout 5m
  echo "== emisores de certificados${email:+ (con ACME de letsencrypt para $email)}"
  helm upgrade --install circleguard-platform deploy/helm/platform --namespace cert-manager --wait --timeout 5m \
    ${email:+--set "acme.email=$email"}
  kubectl get clusterissuers
}

# kubeseal se baja una vez (la suma de verificacion esta fijada arriba) y queda en la cache.
kubeseal_bin() {
  local bin="$CACHE/bin/kubeseal-$KUBESEAL_VERSION"
  if [[ ! -x "$bin" ]]; then
    local tmp; tmp="$(mktemp -d)"; mkdir -p "$CACHE/bin"
    curl -fsSL -o "$tmp/k.tgz" "https://github.com/bitnami/sealed-secrets/releases/download/v$KUBESEAL_VERSION/kubeseal-$KUBESEAL_VERSION-linux-amd64.tar.gz"
    echo "$KUBESEAL_SHA256  $tmp/k.tgz" | sha256sum -c --quiet - || die "La suma de verificacion de kubeseal no coincide"
    tar -xzf "$tmp/k.tgz" -C "$tmp" kubeseal && install -m 0755 "$tmp/kubeseal" "$bin"; rm -rf "$tmp"
  fi
  echo "$bin"
}

cmd_seal() {
  local env="${1:?Uso: seal <ambiente> [namespace]}" ns="${2:-circleguard-${1}}"
  local out="${SEALED_OUT:-$ROOT/deploy/sealed/$env.yaml}" bin cert
  bin="$(kubeseal_bin)"
  # Regenerar cambiaria la contrasena de una base ya inicializada: si el Secret existe no se toca.
  # exists <recurso>: 0 si existe, 1 si no. Si no pudo preguntar (kubeconfig malo, API caida) NO se asume que no existe.
  exists() {
    local out; out="$(kubectl -n "$ns" get "$1" circleguard-secrets 2>&1)" && return 0
    grep -qi 'not found' <<<"$out" && return 1
    die "No pude consultar $1 en $ns (no se genera nada): $out"
  }
  if exists secret || exists sealedsecret; then
    die "circleguard-secrets ya existe en $ns (Secret o SealedSecret): no se regenera (cambiaria la contrasena de la base ya inicializada)"
  fi
  cert="$(mktemp)"; trap 'rm -f "$cert"' RETURN
  kubectl get --raw "/api/v1/namespaces/kube-system/services/http:sealed-secrets-controller:8080/proxy/v1/cert.pem" > "$cert" \
    || die "No pude leer la llave publica del controlador (corre primero: platform.sh install)"
  mkdir -p "$(dirname "$out")"
  {
    printf 'apiVersion: v1\nkind: Secret\nmetadata:\n  name: circleguard-secrets\n  namespace: %s\ntype: Opaque\nstringData:\n' "$ns"
    for k in DB_PASSWORD NEO4J_PASSWORD VAULT_SECRET VAULT_SALT VAULT_HASH_SALT; do printf '  %s: "%s"\n' "$k" "$(openssl rand -hex 12)"; done
    for k in JWT_SECRET QR_SECRET; do printf '  %s: "%s"\n' "$k" "$(openssl rand -hex 32)"; done
    printf '  LDAP_PASSWORD: "sin-ldap"\n'
  } | "$bin" --cert "$cert" --format yaml > "$out"
  grep -q '^kind: SealedSecret' "$out" || die "kubeseal no produjo un SealedSecret"
  echo "Escrito $out (cifrado: solo este cluster puede abrirlo)"
  kubectl create namespace "$ns" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  kubectl apply -f - < "$out"
  echo "Esperando a que el controlador cree el Secret..."
  local i
  for i in $(seq 1 30); do
    kubectl -n "$ns" get secret circleguard-secrets >/dev/null 2>&1 && { echo "Secret circleguard-secrets listo en $ns"; return; }
    sleep 2
  done
  die "El controlador no creo el Secret (kubectl -n kube-system logs deploy/sealed-secrets-controller)"
}

case "${1:-}" in
  install) shift; cmd_install "$@" ;;
  seal)    shift; cmd_seal "$@" ;;
  *) sed -n '2,12p' "$0"; exit 2 ;;
esac
