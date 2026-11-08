// Despliega una versión ya publicada en Nexus en un ambiente (dev, stage o prod) por SSH (HU-08, #15).
// Copia deploy/compose/ a la VM, fija REGISTRY e IMAGE_TAG en el .env del ambiente y corre
// `compose pull && up -d --wait` (jenkins/resources/scripts/deploy.sh). Es idempotente y sirve igual para
// avanzar o para volver a una versión anterior (rollback, HU-10): las imágenes son inmutables.
// Uso: deployCompose('dev', '1.2.0')     deployCompose('prod', '1.1.0', 'rollback')
def call(String envName, String version, String reason = 'deploy') {
    String host = env."${envName.toUpperCase()}_HOST"
    if (!host) {
        error "[deployCompose] Falta ${envName.toUpperCase()}_HOST (la define JCasC en infra/tools/jenkins/casc/jenkins.yaml)"
    }
    if (!version) {
        error '[deployCompose] Falta la versión a desplegar'
    }
    writeFile file: '.deploy.sh', text: libraryResource('scripts/deploy.sh')
    withCredentials([sshUserPrivateKey(credentialsId: 'deploy-ssh', keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
        withEnv(["D_HOST=${host}", "D_ENV=${envName}", "D_VERSION=${version}", "D_REASON=${reason}",
                 "D_REGISTRY=${env.REGISTRY}", "D_SHA=${(env.GIT_COMMIT ?: '').take(7)}", "D_BUILD=${env.BUILD_URL ?: ''}"]) {
            sh '''set -e
                mkdir -p "$HOME/.ssh"
                SSH="ssh -i $SSH_KEY -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=$HOME/.ssh/known_hosts -o ConnectTimeout=15 -o BatchMode=yes"
                echo "[deployCompose] $D_ENV ($D_HOST) <- v$D_VERSION ($D_REASON)"
                $SSH "$SSH_USER@$D_HOST" 'mkdir -p /opt/circleguard/compose'
                # El .env con los secretos del ambiente vive solo en la VM: nunca se sobreescribe ni se borra.
                rsync -az --delete --exclude='.env' -e "$SSH" deploy/compose/ "$SSH_USER@$D_HOST:/opt/circleguard/compose/"
                rsync -az -e "$SSH" .deploy.sh "$SSH_USER@$D_HOST:/opt/circleguard/deploy.sh"
                $SSH "$SSH_USER@$D_HOST" "bash /opt/circleguard/deploy.sh '$D_ENV' '$D_VERSION' '$D_REGISTRY' '$D_REASON' '$D_SHA' '$D_BUILD'"
                rm -f .deploy.sh'''
        }
    }
}
