#!/usr/bin/env bash
# Escaneos de seguridad dentro de Docker con versiones fijas (como infra/terraform/run.sh): no hay que instalar nada.
#
#   ./security/run.sh secrets        gitleaks sobre TODO el historial de git (falla si hay un hallazgo no revisado)
#   ./security/run.sh secrets-tree   gitleaks sobre los archivos actuales (incluye lo que aún no se commitea)
#   ./security/run.sh selftest       control negativo: un secreto falso DEBE hacer fallar el escaneo y un directorio limpio no
#   ./security/run.sh check          secrets + secrets-tree + selftest
set -euo pipefail

GITLEAKS_IMAGE="ghcr.io/gitleaks/gitleaks:v8.30.1"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"

die() { printf 'run.sh: %s\n' "$*" >&2; exit 1; }
command -v docker >/dev/null || die "Falta Docker"

gitleaks() { docker run --rm -v "$ROOT:/repo:ro" "$GITLEAKS_IMAGE" "$@"; }

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

# Un control que nunca falla no demuestra nada: se escribe un token falso (generado al vuelo, no queda en ningún archivo
# del repo) y se comprueba que el escaneo lo detecta, y que un directorio sin secretos pasa.
cmd_selftest() {
  local tmp rc fake
  tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' RETURN
  fake="ghp_$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 36 || true)"
  mkdir "$tmp/limpio" "$tmp/con-secreto"
  echo 'puerto: 8080' > "$tmp/limpio/config.yml"
  printf 'github_token: %s\n' "$fake" > "$tmp/con-secreto/config.yml"

  scan() { docker run --rm -v "$ROOT/.gitleaks.toml:/cfg.toml:ro" -v "$1:/scan:ro" "$GITLEAKS_IMAGE" dir /scan --config /cfg.toml --redact --no-banner --exit-code 1 >/dev/null 2>&1; }

  if scan "$tmp/con-secreto"; then die "selftest FALLA: un token falso NO hizo fallar el escaneo"; else echo "ok   un token falso hace fallar el escaneo"; fi
  if scan "$tmp/limpio"; then echo "ok   un directorio sin secretos pasa"; else die "selftest FALLA: un directorio limpio hizo fallar el escaneo"; fi
}

case "${1:-}" in
  secrets)      cmd_secrets ;;
  secrets-tree) cmd_secrets_tree ;;
  selftest)     cmd_selftest ;;
  check)        cmd_secrets; cmd_secrets_tree; cmd_selftest ;;
  *) sed -n '2,7p' "$0"; exit 2 ;;
esac
