#!/usr/bin/env bash
# Simula el contenedor de Jenkins para probar los runners SIN encender la nube: una imagen con docker CLI + bash + git,
# el socket del Docker del host, un volumen jenkins_home donde vive el workspace y el mismo usuario/grupo que Jenkins.
#   sim-jenkins.sh <worktree> '<comando que corre dentro del workspace>'     p. ej. './security/run.sh iac'
# Copia HEAD del worktree (git archive: lo que no esté commiteado NO entra) y la caché de Trivy/Terraform del PC se monta
# donde Jenkins la tendría (/var/jenkins_home/.cache/circleguard-iac), así no se baja la base de datos otra vez.
set -euo pipefail
wt="$(cd "$1" && pwd)"; shift
V=cg-sim-jenkins-home; WS=/var/jenkins_home/workspace/circleguard_sim; IMG=cg-sim-jenkins
sock_gid="$(stat -c %g /var/run/docker.sock)"
docker image inspect "$IMG" >/dev/null 2>&1 || docker build -q -t "$IMG" - <<'DOCKERFILE' >/dev/null
FROM docker:29-cli
RUN apk add --no-cache bash git jq coreutils findutils tar curl
DOCKERFILE
docker volume rm -f "$V" >/dev/null 2>&1 || true
docker volume create "$V" >/dev/null
docker run --rm -v "$V:/v" alpine:3.20 sh -c "mkdir -p /v/workspace/circleguard_sim /v/.cache && chown -R $(id -u):$(id -g) /v"
git -C "$wt" archive "${SIM_REV:-HEAD}" | docker run -i --rm --user "$(id -u):$(id -g)" -v "$V:/var/jenkins_home" "$IMG" tar -x -C "$WS"
run() {
  docker run --rm --user "$(id -u):$(id -g)" --group-add "$sock_gid" \
    -e HOME=/var/jenkins_home -e TRIVY_SKIP_DB_UPDATE=true -e TRIVY_SKIP_JAVA_DB_UPDATE=true \
    -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e TF_VAR_admin_cidrs -e TF_STATE_BUCKET \
    -v /var/run/docker.sock:/var/run/docker.sock -v "$V:/var/jenkins_home" \
    -v "$HOME/.cache/circleguard-iac:/var/jenkins_home/.cache/circleguard-iac" \
    -w "$WS" "$IMG" bash -c "$*"
}
# El workspace de Jenkins es un checkout de git: se le crea uno para que `git ls-files` y el clon espejo funcionen.
run "git init -q && git config user.email sim@example.com && git config user.name sim && git add -A && git commit -qm sim && echo '[sim] workspace listo: '\$(pwd)"
run "$*"
rc=$?
docker volume rm -f "$V" >/dev/null 2>&1 || true
exit $rc
