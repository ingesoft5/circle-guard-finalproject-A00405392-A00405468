#!/usr/bin/env bash
# Clúster k3s local dentro de Docker para probar el chart sin gastar nada de AWS. Es la MISMA versión de k3s
# que instala Terraform en los nodos (infra/terraform), así que lo que funciona aquí funciona allá.
#
#   ./deploy/helm/local.sh up         crea el clúster y espera a que el nodo esté Ready
#   ./deploy/helm/local.sh images [etiqueta]  carga en el clúster las imágenes local/circleguard-*:<etiqueta> (por defecto local; se construyen aparte)
#   ./deploy/helm/local.sh deploy [..] helm upgrade --install con values-local.yaml y secretos aleatorios;
#                                     los argumentos extra van a helm (p. ej. --set config.privacy.kAnonymity=8)
#   ./deploy/helm/local.sh secrets    imprime la ruta del archivo de secretos aleatorios de la prueba (lo crea si falta)
#   ./deploy/helm/local.sh smoke      comprueba la web, las sondas y el login a través del Ingress
#   ./deploy/helm/local.sh platform   instala cert-manager y Sealed Secrets (platform.sh); antes de `deploy` si se usa TLS o SEALED=1
#   ./deploy/helm/local.sh seal       genera y sella los secretos de la prueba (Secret circleguard-secrets); luego SEALED=1 ... deploy
#   ./deploy/helm/local.sh tls-smoke  comprueba HTTPS con la CA propia, la redireccion de HTTP y que sin la CA se rechaza
#   ./deploy/helm/local.sh security-smoke  comprueba RBAC, NetworkPolicies y el endurecimiento de los pods en el cluster real
#   ./deploy/helm/local.sh kubectl .. kubectl dentro del clúster (p. ej. kubectl get pods -A)
#   ./deploy/helm/local.sh down       destruye el clúster
set -euo pipefail

K3S_IMAGE="rancher/k3s:v1.37.2-k3s1"
NAME="cg-k3s"
NS="circleguard-local"
HTTP_PORT="${HOST_HTTP_PORT:-8088}"
HTTPS_PORT="${HOST_HTTPS_PORT:-8443}"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATE="$HERE/.local"   # ignorado por git: kubeconfig y secretos de la prueba
CHART="deploy/helm/circleguard"

die() { printf 'local.sh: %s\n' "$*" >&2; exit 1; }
command -v docker >/dev/null || die "Falta Docker"
kc() { docker exec "$NAME" kubectl "$@"; }

cmd_up() {
  docker inspect "$NAME" >/dev/null 2>&1 && die "Ya existe el clúster $NAME (./deploy/helm/local.sh down para borrarlo)"
  docker run -d --name "$NAME" --privileged --tmpfs /run --tmpfs /var/run \
    -p 127.0.0.1:6443:6443 -p "127.0.0.1:$HTTP_PORT:80" -p "127.0.0.1:$HTTPS_PORT:443" \
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
  local img n=0 tag="${1:-local}"
  while read -r img; do
    echo "cargando $img"
    docker save "$img" | docker exec -i "$NAME" ctr -n k8s.io images import - >/dev/null
    # ctr puede terminar sin error y no importar nada (visto con auth): se comprueba que la imagen quedó en el clúster.
    docker exec "$NAME" ctr -n k8s.io images ls -q | grep -qx "docker.io/$img" || die "No quedó cargada la imagen $img"
    n=$((n + 1))
  done < <(docker images --format '{{.Repository}}:{{.Tag}}' | grep -E "^local/circleguard-.*:${tag}\$" | sort -u)
  [[ $n -gt 0 ]] || die "No hay imágenes local/circleguard-*:$tag (constrúyelas con docker build -f services/<svc>/Dockerfile ...)"
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
  # SEALED=1: los secretos los crea Sealed Secrets (local.sh seal) y el chart usa el Secret que ya existe.
  local secrets=(-f "deploy/helm/.local/values-secrets.yaml")
  if [[ "${SEALED:-}" == 1 ]]; then secrets=(--set existingSecret=circleguard-secrets); else secrets_file >/dev/null; fi
  KUBECONFIG_FILE="$STATE/kubeconfig" "$HERE/run.sh" helm upgrade --install circleguard "$CHART" \
    --namespace "$NS" --create-namespace \
    -f "$CHART/values-local.yaml" "${secrets[@]}" \
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

cmd_platform() { KUBECONFIG_FILE="$STATE/kubeconfig" "$HERE/platform.sh" install; }
cmd_seal() { SEALED_OUT="$STATE/sealed.yaml" KUBECONFIG_FILE="$STATE/kubeconfig" "$HERE/platform.sh" seal local "$NS"; }

# Un resultado esperado por linea: "pasa" si el comando termina bien, "falla" si termina mal (un bloqueo cuenta como exito).
SEC_FAIL=0
check() {
  local want="$1" what="$2" rc=0; shift 2
  "$@" >/dev/null 2>&1 || rc=$?
  if [[ ( "$want" == pasa && $rc -eq 0 ) || ( "$want" == falla && $rc -ne 0 ) ]]; then printf '[ok]    %s\n' "$what"
  else printf '[FALLA] %s\n' "$what"; SEC_FAIL=1; fi
}
inpod() { local d="$1"; shift; kc -n "$NS" exec "deploy/$d" -- sh -c "$*"; }

# TLS de verdad: la CA propia firma el certificado; con ella se verifica, sin ella se rechaza.
# Requiere desplegar con: --set ingress.tls.enabled=true --set ingress.host=circleguard.local
cmd_tls_smoke() {
  local host=circleguard.local ca="$STATE/ca.crt" code loc
  kc -n cert-manager get secret circleguard-ca -o jsonpath='{.data.ca\.crt}' | base64 -d > "$ca"
  kc -n "$NS" wait --for=condition=Ready certificate/circleguard-tls --timeout=120s >/dev/null
  check pasa  "HTTPS con la CA propia responde 200 (certificado verificado)" \
    curl -sf --max-time 15 --retry 5 --retry-delay 3 --retry-all-errors --cacert "$ca" --resolve "$host:$HTTPS_PORT:127.0.0.1" "https://$host:$HTTPS_PORT/healthz"
  echo "[tls] certificado entregado por el Ingress (verificado contra la CA propia):"
  echo | openssl s_client -connect "127.0.0.1:$HTTPS_PORT" -servername "$host" -CAfile "$ca" -verify_return_error 2>/dev/null \
    | { openssl x509 -noout -subject -issuer -dates -ext subjectAltName; } 2>/dev/null | sed 's/^/[tls]   /'
  echo | openssl s_client -connect "127.0.0.1:$HTTPS_PORT" -servername "$host" -CAfile "$ca" -verify_return_error 2>&1 \
    | grep -E "Verification:" | sed 's/^/[tls]   /'
  check falla "HTTPS sin la CA se rechaza (el certificado no es de una CA publica)" \
    curl -sf --max-time 15 --resolve "$host:$HTTPS_PORT:127.0.0.1" "https://$host:$HTTPS_PORT/healthz"
  loc="$(curl -s -o /dev/null -w '%{http_code} %{redirect_url}' --max-time 15 -H "Host: $host" "http://127.0.0.1:$HTTP_PORT/")"
  echo "[tls] HTTP -> $loc"
  [[ "$loc" == 30[18]\ https://"$host"/* ]] && echo "[ok]    HTTP redirige a HTTPS (permanente)" || { echo "[FALLA] HTTP no redirige a HTTPS"; SEC_FAIL=1; }
  [[ $SEC_FAIL -eq 0 ]] && echo "[tls] TODO OK" || { echo "[tls] HAY FALLAS"; return 1; }
}

# RBAC, NetworkPolicies y endurecimiento comprobados contra el cluster real (no contra el YAML).
cmd_security_smoke() {
  local viewer="--as=system:serviceaccount:$NS:circleguard-viewer" workload="--as=system:serviceaccount:$NS:circleguard-workload"
  echo "== RBAC"
  check pasa  "viewer puede leer pods"                  kc -n "$NS" auth can-i get pods "$viewer"
  check falla "viewer NO puede leer Secrets"            kc -n "$NS" auth can-i get secrets "$viewer"
  check falla "viewer NO puede borrar pods"             kc -n "$NS" auth can-i delete pods "$viewer"
  check falla "viewer NO puede crear Deployments"       kc -n "$NS" auth can-i create deployments "$viewer"
  check falla "los pods (circleguard-workload) NO pueden leer pods" kc -n "$NS" auth can-i get pods "$workload"
  check falla "los pods NO pueden leer Secrets"         kc -n "$NS" auth can-i get secrets "$workload"
  echo "== Endurecimiento de los pods"
  check falla "auth no tiene el token de la API de Kubernetes montado" inpod auth 'ls /var/run/secrets/kubernetes.io/serviceaccount'
  check falla "auth no puede escribir en su imagen (disco de solo lectura)" inpod auth 'touch /app/x'
  check pasa  "auth si puede escribir en /tmp (volumen efimero)"        inpod auth 'touch /tmp/x'
  check pasa  "auth corre como usuario 100 (no root)"   inpod auth '[ "$(id -u)" = 100 ]'
  echo "== NetworkPolicies (quien puede llamar a quien)"
  check pasa  "auth -> postgres:5432 (permitido)"       inpod auth 'nc -z -w 3 postgres 5432'
  check falla "gateway -> postgres:5432 (bloqueado)"    inpod gateway 'nc -z -w 3 postgres 5432'
  check pasa  "gateway -> promotion:8088 (permitido: llama a /internal)" inpod gateway 'nc -z -w 3 promotion 8088'
  check falla "auth -> promotion:8088 (bloqueado: /internal no es suyo)" inpod auth 'nc -z -w 3 promotion 8088'
  # La ruta interna sin autenticacion (/internal/v1/...): solo la alcanza quien la necesita.
  check pasa  "gateway -> GET promotion/internal/v1/users/x/status (HTTP real, permitido)" \
    inpod gateway 'wget -q -T 5 -O /dev/null http://promotion:8088/internal/v1/users/x/status'
  check falla "auth -> GET promotion/internal/v1/users/x/status (HTTP real, bloqueado)" \
    inpod auth 'wget -q -T 5 -O /dev/null http://promotion:8088/internal/v1/users/x/status'
  # Mismo pod de prueba, solo cambia la etiqueta: con la de web llega a auth, sin ella no (el control demuestra que el pod funciona).
  check pasa  "pod con la etiqueta de web -> auth:8180 (permitido)" \
    kc -n "$NS" run np-web --rm -i --restart=Never --labels=app.kubernetes.io/name=web --image=busybox:1.37.0 -- nc -z -w 3 auth 8180
  check falla "pod sin etiqueta -> auth:8180 (bloqueado)" \
    kc -n "$NS" run np-otro --rm -i --restart=Never --image=busybox:1.37.0 -- nc -z -w 3 auth 8180
  [[ $SEC_FAIL -eq 0 ]] && echo "[seguridad] TODO OK" || { echo "[seguridad] HAY FALLAS"; return 1; }
}

cmd_down() {
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  rm -f "$STATE/kubeconfig"
  echo "Clúster $NAME destruido"
}

case "${1:-}" in
  up)      cmd_up ;;
  images)  shift; cmd_images "$@" ;;
  secrets) secrets_file ;;
  deploy)  shift; cmd_deploy "$@" ;;
  smoke)   cmd_smoke ;;
  platform) cmd_platform ;;
  seal)    cmd_seal ;;
  tls-smoke) cmd_tls_smoke ;;
  security-smoke) cmd_security_smoke ;;
  kubectl) shift; kc "$@" ;;
  down)    cmd_down ;;
  *) sed -n '2,/^set -euo/p' "$0" | sed '$d'; exit 2 ;;
esac
