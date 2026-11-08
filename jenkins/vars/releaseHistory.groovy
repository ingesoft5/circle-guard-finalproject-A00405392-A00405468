// Muestra el historial de versiones desplegadas en un ambiente: /opt/circleguard/RELEASES en la VM,
// que escribe deploy.sh en cada despliegue o rollback (fecha, versión, motivo, commit, build).
// Uso: releaseHistory('prod')
def call(String envName) {
    String host = env."${envName.toUpperCase()}_HOST"
    if (!host) {
        error "[releaseHistory] Falta ${envName.toUpperCase()}_HOST (JCasC)"
    }
    withCredentials([sshUserPrivateKey(credentialsId: 'deploy-ssh', keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
        withEnv(["H_HOST=${host}", "H_ENV=${envName}"]) {
            sh '''mkdir -p "$HOME/.ssh"
                  echo "[releaseHistory] Versiones desplegadas en $H_ENV (fecha, versión, motivo, commit, build):"
                  ssh -i "$SSH_KEY" -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile="$HOME/.ssh/known_hosts" \
                      -o ConnectTimeout=15 -o BatchMode=yes "$SSH_USER@$H_HOST" \
                      'cat /opt/circleguard/RELEASES 2>/dev/null || echo "(sin despliegues todavía)"' '''
        }
    }
}
