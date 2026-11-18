#!/usr/bin/env bash
# Clúster k3s local dentro de Docker para probar el chart sin gastar nada de AWS. Es la MISMA versión de k3s
# que instala Terraform en los nodos (infra/terraform), así que lo que funciona aquí funciona allá.
#
#   ./deploy/helm/local.sh up         crea el clúster y espera a que el nodo esté Ready
#   ./deploy/helm/local.sh images     carga en el clúster las imágenes local/circleguard-*:local (se construyen aparte)
#   ./deploy/helm/local.sh deploy [..] helm upgrade --install con values-local.yaml y secretos aleatorios;
#                                     los argumentos extra van a helm (p. ej. --set config.privacy.kAnonymity=8)
#   ./deploy/helm/local.sh smoke      comprueba la web, las sondas y el login a través del Ingress
#   ./deploy/helm/local.sh kubectl .. kubectl dentro del clúster (p. ej. kubectl get pods -A)
#   ./deploy/helm/local.sh down       destruye el clúster
set -euo pipefail

K3S_IMAGE="rancher/k3s:v1.37.2-k3s1"
NAME="cg-k3s"
NS="circleguard-local"
HTTP_PORT="${HOST_HTTP_PORT:-8088}"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATE="$HERE/.local"   # ignorado por git: kubeconfig y secretos de la prueba
CHART="deploy/helm/circleguard"

die() { printf 'local.sh: %s\n' "$*" >&2; exit 1; }
command -v docker >/dev/null || die "Falta Docker"
kc() { docker exec "$NAME" kubectl "$@"; }

cmd_up() {
  docker inspect "$NAME" >/dev/null 2>&1 && die "Ya existe el clúster $NAME (./deploy/helm/local.sh down para borrarlo)"
  docker run -d --name "$NAME" --privileged --tmpfs /run --tmpfs /var/run \
    -p 127.0.0.1:6443:6443 -p "127.0.0.1:$HTTP_PORT:80" \
    "$K3S_IMAGE" server --tls-san 127.0.0.1 >/dev/null
  echo "Esperando al nodo..."
  local i
  for i in $(seq 1 60); do
    if kc get nodes --no-headers 2>/dev/null | grep -q ' Ready'; then break; fi
    sleep 3
  done
  kc get nodes --no-headers | grep -q ' Ready' || die "El nodo no llegó a Ready"
  mkdir -p "$STATE"; chmod 700 "$STATE"
  docker exec "$NAME" cat /etc/rancher/k3s/k3s.yaml > "$STATE/kubeconfig"; chmod 600 "$STATE/kubeconfig"
  kc get nodes
}

cmd_images() {
  local img n=0
  while read -r img; do
    echo "cargando $img"
    docker save "$img" | docker exec -i "$NAME" ctr -n k8s.io images import - >/dev/null
    # ctr puede terminar sin error y no importar nada (visto con auth): se comprueba que la imagen quedó en el clúster.
    docker exec "$NAME" ctr -n k8s.io images ls -q | grep -qx "docker.io/$img" || die "No quedó cargada la imagen $img"
    n=$((n + 1))
  done < <(docker images --format '{{.Repository}}:{{.Tag}}' | grep -E '^local/circleguard-.*:local$' | sort -u)
  [[ $n -gt 0 ]] || die "No hay imágenes local/circleguard-*:local (constrúyelas con docker build -f services/<svc>/Dockerfile ...)"
  echo "$n imágenes cargadas"
}

secrets_file() {
  local f="$STATE/values-secrets.yaml"
  if [[ ! -f "$f" ]]; then
    mkdir -p "$STATE"; chmod 700 "$STATE"
    {
      echo "secrets:"
      for k in dbPassword neo4jPassword vaultSecret vaultSalt vaultHashSalt; do echo "  $k: \"$(openssl rand -hex 12)\""; done
      for k in jwtSecret qrSecret; do echo "  $k: \"$(openssl rand -hex 32)\""; done
    } > "$f"
    chmod 600 "$f"
  fi
  echo "$f"
}

cmd_deploy() {
  [[ -f "$STATE/kubeconfig" ]] || die "No hay clúster: corre primero ./deploy/helm/local.sh up"
  secrets_file >/dev/null
  KUBECONFIG_FILE="$STATE/kubeconfig" "$HERE/run.sh" helm upgrade --install circleguard "$CHART" \
    --namespace "$NS" --create-namespace \
    -f "$CHART/values-local.yaml" -f "deploy/helm/.local/values-secrets.yaml" \
    --wait --timeout 10m "$@"
  kc -n "$NS" get pods,hpa,ingress
}

cmd_smoke() {
  local base="http://127.0.0.1:$HTTP_PORT" fail=0 path code
  for path in /healthz /version /api/auth/actuator/health/readiness /api/identity/actuator/health/readiness \
              /api/form/actuator/health/readiness /api/promotion/actuator/health/readiness \
              /api/gateway/actuator/health/readiness; do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 --retry 6 --retry-delay 5 --retry-all-errors "$base$path" || true)"
    printf '[smoke] %-48s HTTP %s\n' "$path" "$code"
    [[ "$code" == 200 ]] || fail=1
  done
  printf '[smoke] version desplegada: %s\n' "$(curl -s --max-time 10 "$base/version")"
  # Login real: web (nginx) -> auth -> Postgres (y identity). super_admin/password es la cuenta semilla de la Parte 1.
  local body
  body="$(curl -s --max-time 20 -X POST -H 'Content-Type: application/json' \
    -d '{"username":"super_admin","password":"password"}' "$base/api/auth/api/v1/auth/login" || true)"
  if grep -q '"token"' <<<"$body"; then echo "[smoke] login super_admin: OK (devolvió token)"; else echo "[smoke] login super_admin: FALLÓ -> ${body:0:200}"; fail=1; fi
  [[ $fail -eq 0 ]] && echo "[smoke] TODO OK" || { echo "[smoke] HAY FALLAS"; return 1; }
}

cmd_down() {
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  rm -f "$STATE/kubeconfig"
  echo "Clúster $NAME destruido"
}

case "${1:-}" in
  up)      cmd_up ;;
  images)  cmd_images ;;
  deploy)  shift; cmd_deploy "$@" ;;
  smoke)   cmd_smoke ;;
  kubectl) shift; kc "$@" ;;
  down)    cmd_down ;;
  *) sed -n '2,10p' "$0"; exit 2 ;;
esac
