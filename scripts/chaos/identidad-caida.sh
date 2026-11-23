#!/usr/bin/env bash
# Falla provocada para la sustentación: se apaga `identity` y se ve cómo el login DEGRADA (503 claro con Retry-After, y con el
# circuito abierto responde en décimas de segundo) y cómo VUELVE SOLO al encenderlo, sin reiniciar `auth`.
#   KUBECONFIG_FILE=<ruta absoluta> BASE=<url del ambiente> [NS=circleguard-dev] ./demo-falla.sh
# Es lo mismo en el k3s local (BASE=http://127.0.0.1:8088) y en el de AWS (BASE=https://<ip-con-guiones>.sslip.io, con INSECURE=1
# si el certificado es el de staging). Restaura SIEMPRE el número de réplicas original, aunque algo falle.
set -uo pipefail
: "${KUBECONFIG_FILE:?Pasa KUBECONFIG_FILE=<ruta absoluta>}"; : "${BASE:?Pasa BASE=<url del ambiente>}"
NS="${NS:-circleguard-dev}"; K3S_IMAGE="rancher/k3s:v1.37.2-k3s1"; CURL=(curl -s -m 25 ${INSECURE:+-k})
kc() { docker run --rm --network host -v "$KUBECONFIG_FILE:/kube/config:ro" -e KUBECONFIG=/kube/config --entrypoint kubectl "$K3S_IMAGE" "$@"; }
login() { "${CURL[@]}" -o /dev/null -D /tmp/demo-h.$$ -w '%{http_code} %{time_total}s' -X POST -H 'Content-Type: application/json' \
  -d '{"username":"super_admin","password":"password"}' "$BASE/api/auth/api/v1/auth/login"; printf ' Retry-After=%s\n' "$(grep -i '^retry-after' /tmp/demo-h.$$ | tr -d '\r' | cut -d' ' -f2)"; }
say() { printf '\n\033[1m== %s\033[0m\n' "$*"; }

orig="$(kc -n "$NS" get deploy identity -o jsonpath='{.spec.replicas}')"; [[ -n "$orig" ]] || { echo "no encuentro deploy/identity en $NS"; exit 1; }
restore() { kc -n "$NS" scale deploy/identity --replicas="$orig" >/dev/null 2>&1; }; trap restore EXIT

say "1) Estado sano: identity con $orig réplica(s)"; kc -n "$NS" get pods -l app.kubernetes.io/name=identity 2>/dev/null | head -3 || kc -n "$NS" get pods | grep -E "identity|NAME"
printf 'login: '; login

say "2) FALLA: se apaga identity (scale a 0)"; kc -n "$NS" scale deploy/identity --replicas=0; sleep 8
kc -n "$NS" get pods | grep -E "NAME|identity" || echo "(identity ya no tiene pods)"

say "3) El login degrada, no se cae: 503 claro con Retry-After; con el circuito abierto responde rápido"
for i in 1 2 3 4 5 6; do printf 'intento %s: ' "$i"; login; done

say "4) RECUPERACIÓN: se vuelve a encender identity ($orig réplica(s))"; t0=$SECONDS; restore
kc -n "$NS" rollout status deploy/identity --timeout=180s | tail -1
until out="$(login)"; [[ "$out" == 200* ]]; do printf '  esperando... %s\n' "$out"; (( SECONDS - t0 > 240 )) && { echo "NO se recuperó en 4 min"; exit 1; }; sleep 4; done
printf 'login: %s\n' "$out"; echo "recuperado en $((SECONDS - t0)) s, sin reiniciar auth"
say "5) Estado final"; kc -n "$NS" get pods | grep -E "NAME|identity|auth"
