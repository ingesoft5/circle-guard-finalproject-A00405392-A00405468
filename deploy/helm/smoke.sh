#!/usr/bin/env bash
# Prueba de humo de un ambiente de Kubernetes por su Ingress: la web, las sondas de los 5 servicios núcleo, la versión
# desplegada y un login real (web -> auth -> Postgres). La usan local.sh (clúster local) y promote.sh (el pipeline).
#
#   ./deploy/helm/smoke.sh <url-base> [--insecure] [--version <esperada>]
#
# --insecure acepta un certificado que el sistema no conoce (CA propia local o Let's Encrypt *staging*).
# --version falla si /version no menciona esa versión: así se comprueba que corre lo que se pidió desplegar.
set -euo pipefail

base="${1:?Uso: smoke.sh <url-base> [--insecure] [--version <esperada>]}"; shift
base="${base%/}"
curl_opts=(-s --max-time 10); expected=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --insecure) curl_opts+=(-k); shift ;;
    --version)  expected="${2:?}"; shift 2 ;;
    *) echo "smoke.sh: argumento desconocido: $1" >&2; exit 2 ;;
  esac
done

fail=0
for path in /healthz /version /api/auth/actuator/health/readiness /api/identity/actuator/health/readiness \
            /api/form/actuator/health/readiness /api/promotion/actuator/health/readiness \
            /api/gateway/actuator/health/readiness; do
  code="$(curl "${curl_opts[@]}" -o /dev/null -w '%{http_code}' --retry 6 --retry-delay 5 --retry-all-errors "$base$path" || true)"
  printf '[smoke] %-48s HTTP %s\n' "$path" "$code"
  [[ "$code" == 200 ]] || fail=1
done

version="$(curl "${curl_opts[@]}" "$base/version" || true)"
printf '[smoke] version desplegada: %s\n' "$version"
if [[ -n "$expected" ]]; then
  if [[ "$version" == *"$expected"* ]]; then echo "[smoke] la versión coincide con la pedida ($expected)"
  else echo "[smoke] FALLÓ: /version no menciona $expected"; fail=1; fi
fi

# Login real: super_admin/password es la cuenta semilla de la Parte 1.
body="$(curl "${curl_opts[@]}" --max-time 20 -X POST -H 'Content-Type: application/json' \
  -d '{"username":"super_admin","password":"password"}' "$base/api/auth/api/v1/auth/login" || true)"
if grep -q '"token"' <<<"$body"; then echo "[smoke] login super_admin: OK (devolvió token)"; else echo "[smoke] login super_admin: FALLÓ -> ${body:0:200}"; fail=1; fi

[[ $fail -eq 0 ]] && echo "[smoke] TODO OK" || { echo "[smoke] HAY FALLAS"; exit 1; }
