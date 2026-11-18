#!/usr/bin/env bash
# Escaneos de seguridad dentro de Docker con versiones fijas (como infra/terraform/run.sh): no hay que instalar nada.
#
#   ./security/run.sh secrets        gitleaks sobre TODO el historial de git (falla si hay un hallazgo no revisado)
#   ./security/run.sh secrets-tree   gitleaks sobre los archivos actuales (incluye lo que aún no se commitea)
#   ./security/run.sh iac            Trivy sobre Dockerfile, chart de Helm y Terraform (HIGH/CRITICAL bloquea)
#   ./security/run.sh deps           Trivy sobre las dependencias con lockfile (npm; HIGH/CRITICAL con arreglo bloquea)
#   ./security/run.sh image [ref..]  Trivy sobre imágenes (HIGH/CRITICAL con arreglo bloquea); sin argumentos, las local/circleguard-*:local
#   ./security/run.sh selftest       controles negativos: un secreto, un Dockerfile y un lockfile malos DEBEN hacer fallar el escaneo
#   ./security/run.sh check          todo lo anterior salvo `image` (que necesita las imágenes construidas)
#
# Sin red o con el registro de Trivy caído: TRIVY_SKIP_DB_UPDATE=true TRIVY_SKIP_JAVA_DB_UPDATE=true ./security/run.sh ...
# Las excepciones revisadas están en .gitleaks.toml (secretos) y .trivyignore.yaml (Trivy), cada una con su motivo.
set -euo pipefail

GITLEAKS_IMAGE="ghcr.io/gitleaks/gitleaks:v8.30.1"
TRIVY_IMAGE="aquasec/trivy:0.76.2"      # la misma versión que infra/terraform/run.sh
SEVERITY="HIGH,CRITICAL"                # umbral que bloquea
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
CACHE="${CIRCLEGUARD_CACHE:-$HOME/.cache/circleguard-iac}"   # la comparte con infra/terraform/run.sh: la base de Trivy pesa ~2 GB

die() { printf 'run.sh: %s\n' "$*" >&2; exit 1; }
command -v docker >/dev/null || die "Falta Docker"

gitleaks() { docker run --rm -v "$ROOT:/repo:ro" "$GITLEAKS_IMAGE" "$@"; }

# trivy_in <directorio> <argumentos>: el directorio queda en /work (solo lectura). Con el socket de Docker también ve
# las imágenes del equipo; el grupo del socket se agrega porque el contenedor corre con tu usuario, no como root.
trivy_in() {
  local dir="$1"; shift
  local sock=()
  [[ -S /var/run/docker.sock ]] && sock=(-v /var/run/docker.sock:/var/run/docker.sock --group-add "$(stat -c %g /var/run/docker.sock)")
  mkdir -p "$CACHE/trivy"
  docker run --rm -u "$(id -u):$(id -g)" "${sock[@]}" -e HOME=/tmp -e TRIVY_CACHE_DIR=/cache \
    -e TRIVY_SKIP_DB_UPDATE -e TRIVY_SKIP_JAVA_DB_UPDATE \
    -v "$CACHE/trivy:/cache" -v "$dir:/work:ro" -w /work "$TRIVY_IMAGE" "$@"
}
trivy() { trivy_in "$ROOT" "$@"; }

cmd_secrets() {
  echo "== gitleaks: historial completo de git (todas las ramas, incluidas las locales sin subir)"
  # En un worktree `.git` es un archivo que apunta fuera del directorio y dentro de Docker gitleaks no encuentra el
  # repositorio (y aun así dice «no leaks found» con 0 commits). Se escanea un clon espejo, que siempre es completo.
  local common tmp log
  common="$(git -C "$ROOT" rev-parse --git-common-dir)"; [[ "$common" == /* ]] || common="$ROOT/$common"
  tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' RETURN
  git clone -q --mirror "$common" "$tmp/repo.git"
  log="$tmp/gitleaks.log"
  docker run --rm -v "$tmp/repo.git:/repo.git:ro" -v "$ROOT/.gitleaks.toml:/cfg.toml:ro" "$GITLEAKS_IMAGE" \
    git /repo.git --config /cfg.toml --redact --no-banner --exit-code 1 2>&1 | tee "$log"
  # Un escaneo que no leyó ningún commit no demuestra nada: se exige que haya leído alguno.
  grep -Eq '[1-9][0-9]* commits scanned' "$log" || die "gitleaks escaneó 0 commits: el resultado no vale"
}

cmd_secrets_tree() {
  echo "== gitleaks: archivos actuales"
  gitleaks dir /repo --config /repo/.gitleaks.toml --redact --no-banner --exit-code 1
}

# Mismo criterio que en gitleaks: un escaneo que no miró lo que debía no vale. Se exige que el resumen de Trivy nombre
# cada cosa que debía revisar (si el chart no se puede renderizar, Trivy lo salta sin fallar).
cmd_iac() {
  echo "== trivy config: Dockerfile, chart de Helm y Terraform (HIGH/CRITICAL bloquea)"
  local log; log="$(mktemp)"; trap 'rm -f "$log"' RETURN
  trivy config --severity "$SEVERITY" --exit-code 1 --ignorefile .trivyignore.yaml \
    --helm-set global.imageTag=0.0.0 --helm-values deploy/helm/circleguard/values-secrets.example.yaml \
    --skip-dirs '**/.terraform' --skip-dirs '**/node_modules' --skip-dirs deploy/helm/.local . 2>&1 | tee "$log"
  local want
  for want in 'deploy/helm/circleguard' 'Dockerfile' 'infra/terraform'; do
    grep -q "$want" "$log" || die "trivy config no revisó nada de «$want»: el resultado no vale"
  done
}

cmd_deps() {
  echo "== trivy fs: dependencias con lockfile (HIGH/CRITICAL con arreglo bloquea)"
  # Solo npm tiene lockfile en el repo; las dependencias de Java van dentro de los jar y las cubre `image`.
  local log; log="$(mktemp)"; trap 'rm -f "$log"' RETURN
  trivy fs --scanners vuln --severity "$SEVERITY" --ignore-unfixed --exit-code 1 --ignorefile .trivyignore.yaml \
    --skip-dirs '**/node_modules' --skip-dirs '**/.terraform' . 2>&1 | tee "$log"
  grep -q 'package-lock.json' "$log" || die "trivy fs no leyó ningún package-lock.json: el resultado no vale"
}

cmd_image() {
  local img rc=0
  if [[ $# -eq 0 ]]; then
    mapfile -t imgs < <(docker images --format '{{.Repository}}:{{.Tag}}' | grep -E '^local/circleguard-.*:local$' | sort -u)
    set -- "${imgs[@]}"
  fi
  [[ $# -gt 0 && -n "$1" ]] || die "No hay imágenes que escanear (pasa las referencias, o construye local/circleguard-*:local)"
  for img in "$@"; do
    echo "== trivy image: $img (HIGH/CRITICAL con arreglo bloquea)"
    trivy image --severity "$SEVERITY" --ignore-unfixed --exit-code 1 --ignorefile .trivyignore.yaml "$img" || rc=1
  done
  return $rc
}

# Un control que nunca falla no demuestra nada: se arman entradas malas (un token falso generado al vuelo, un Dockerfile
# que corre como root, un lockfile con una dependencia crítica) y se comprueba que cada escaneo las rechaza, y que las
# buenas pasan.
cmd_selftest() {
  local tmp fake
  tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' RETURN
  fake="ghp_$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 36 || true)"
  mkdir "$tmp/limpio" "$tmp/con-secreto" "$tmp/malo" "$tmp/bueno"
  echo 'puerto: 8080' > "$tmp/limpio/config.yml"
  printf 'github_token: %s\n' "$fake" > "$tmp/con-secreto/config.yml"
  printf 'FROM alpine:3.20\nCMD ["sh"]\n' > "$tmp/malo/Dockerfile"                       # sin USER: corre como root (DS-0002, HIGH)
  printf 'FROM alpine:3.20\nUSER 1000\nCMD ["sh"]\n' > "$tmp/bueno/Dockerfile"
  cat > "$tmp/malo/package-lock.json" <<'EOF'
{"name":"x","lockfileVersion":3,"packages":{"":{"name":"x","dependencies":{"minimist":"1.2.5"}},"node_modules/minimist":{"version":"1.2.5"}}}
EOF

  scan() { docker run --rm -v "$ROOT/.gitleaks.toml:/cfg.toml:ro" -v "$1:/scan:ro" "$GITLEAKS_IMAGE" dir /scan --config /cfg.toml --redact --no-banner --exit-code 1 >/dev/null 2>&1; }
  expect() { # expect <fallar|pasar> <descripción> <comando...>
    local want="$1" what="$2"; shift 2
    if "$@" >/dev/null 2>&1; then
      [[ "$want" == pasar ]] && echo "ok   $what" || die "selftest FALLA: $what NO falló"
    else
      [[ "$want" == fallar ]] && echo "ok   $what" || die "selftest FALLA: $what falló y debía pasar"
    fi
  }

  expect fallar "un token falso hace fallar gitleaks" scan "$tmp/con-secreto"
  expect pasar  "un directorio sin secretos pasa gitleaks" scan "$tmp/limpio"
  expect fallar "un Dockerfile sin USER hace fallar trivy config" trivy_in "$tmp" config --severity "$SEVERITY" --exit-code 1 malo
  expect pasar  "un Dockerfile con USER pasa trivy config" trivy_in "$tmp" config --severity "$SEVERITY" --exit-code 1 bueno
  expect fallar "una dependencia crítica hace fallar trivy fs" trivy_in "$tmp" fs --scanners vuln --severity "$SEVERITY" --exit-code 1 malo
  expect pasar  "un directorio sin dependencias pasa trivy fs" trivy_in "$tmp" fs --scanners vuln --severity "$SEVERITY" --exit-code 1 bueno
}

case "${1:-}" in
  secrets)      cmd_secrets ;;
  secrets-tree) cmd_secrets_tree ;;
  iac)          cmd_iac ;;
  deps)         cmd_deps ;;
  image)        shift; cmd_image "$@" ;;
  selftest)     cmd_selftest ;;
  check)        cmd_secrets; cmd_secrets_tree; cmd_iac; cmd_deps; cmd_selftest ;;
  *) sed -n '2,10p' "$0"; exit 2 ;;
esac
