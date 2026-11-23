// Despliega una versión en un ambiente de Kubernetes en AWS (HU-16, #23): sube las imágenes a ECR, saca el kubeconfig del
// nodo k3s por SSH y deja a deploy/helm/promote.sh el resto: plataforma y secretos si el cluster es nuevo (cert-manager,
// Sealed Secrets), helm upgrade --atomic, prueba de humo por https://<ip-con-guiones>.sslip.io y el registro de cambios
// (release.sh history --md), que se archiva con el build.
// El ambiente tiene que existir: lo crea el job circleguard-infra (terraform apply). Si no, falla aquí diciéndolo.
// Uso: deployK8s('dev', '1.5.0')
def call(String envName, String version) {
    if (!(envName in ['dev', 'stage', 'prod'])) {
        error "[deployK8s] Ambiente desconocido: ${envName}"
    }
    if (!version) {
        error '[deployK8s] Falta la versión a desplegar'
    }
    ecrPublish(version)
    String issuer = params.TLS_ISSUER ?: 'letsencrypt-staging'
    String kube = "${env.WORKSPACE}@tmp/kubeconfig-${envName}.yaml"   // junto al workspace, fuera del árbol que escanean gitleaks y Trivy
    withCredentials([usernamePassword(credentialsId: 'aws-terraform', usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY'),
                     sshUserPrivateKey(credentialsId: 'deploy-ssh', keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
        withEnv(["K_ENV=${envName}", "K_VERSION=${version}", "K_KUBE=${kube}", "K_ISSUER=${issuer}",
                 "K_SHA=${env.GIT_COMMIT ?: ''}", "K_BUILD=${env.BUILD_NUMBER ?: ''}",
                 "HISTORY_OUT=${env.WORKSPACE}/history-${envName}.md"]) {
            try {
                sh '''#!/usr/bin/env bash
                    set -euo pipefail
                    ip="$(./infra/terraform/run.sh "$K_ENV" output -raw public_ip)" || { echo "[deployK8s] $K_ENV no existe: corre primero circleguard-infra (apply)"; exit 1; }
                    mkdir -p "$HOME/.ssh" "$(dirname "$K_KUBE")"
                    SSH="ssh -i $SSH_KEY -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=$HOME/.ssh/known_hosts -o ConnectTimeout=15 -o BatchMode=yes"
                    # El nodo acaba de arrancar k3s con cloud-init: se espera a que escriba su kubeconfig (hasta 5 min).
                    for i in $(seq 1 30); do
                      $SSH "$SSH_USER@$ip" 'sudo cat /etc/rancher/k3s/k3s.yaml' 2>/dev/null | sed "s#https://127.0.0.1:6443#https://$ip:6443#" > "$K_KUBE" || true
                      grep -q 'server: https://' "$K_KUBE" && break
                      echo "[deployK8s] esperando el kubeconfig de $K_ENV ($ip)... $i/30"; sleep 10
                    done
                    grep -q 'server: https://' "$K_KUBE" || { echo "[deployK8s] no se pudo leer el kubeconfig de $K_ENV"; exit 1; }
                    chmod 600 "$K_KUBE"
                    host="$(echo "$ip" | tr . -).sslip.io"
                    export KUBECONFIG_FILE="$K_KUBE" IMAGE_REGISTRY="$ECR_REGISTRY" INGRESS_HOST="$host" TLS_ISSUER="$K_ISSUER"
                    export SMOKE_URL="https://$host" ACME_EMAIL="${ACME_EMAIL:-}"
                    # Let's Encrypt *staging* firma con una CA que ningún sistema conoce: la prueba de humo la acepta solo en ese caso.
                    [ "$K_ISSUER" = "letsencrypt-staging" ] && export SMOKE_INSECURE=1
                    echo "[deployK8s] $K_ENV ($ip) <- v$K_VERSION con $K_ISSUER en https://$host"
                    ./deploy/helm/promote.sh "$K_ENV" "$K_VERSION" --commit "$K_SHA" --build "$K_BUILD"'''
            } finally {
                sh 'rm -f "$K_KUBE"'
                archiveArtifacts artifacts: "history-${envName}.md", allowEmptyArchive: true
            }
        }
    }
}
