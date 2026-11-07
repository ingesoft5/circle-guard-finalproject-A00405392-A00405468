// Prueba de humo tras un despliegue: el ambiente responde a través de su nginx (web y readiness
// de los 5 servicios núcleo). Si algo no da 200 tras varios reintentos, la etapa falla y no se promueve.
// Uso: smokeTest('dev')
def call(String envName) {
    String url = env."${envName.toUpperCase()}_URL"
    if (!url) {
        error "[smokeTest] Falta ${envName.toUpperCase()}_URL (la define JCasC en infra/tools/jenkins/casc/jenkins.yaml)"
    }
    withEnv(["S_URL=${url}", "S_ENV=${envName}"]) {
        sh '''set -e
            for path in /healthz /api/auth/actuator/health/readiness /api/identity/actuator/health/readiness \
                        /api/form/actuator/health/readiness /api/promotion/actuator/health/readiness \
                        /api/gateway/actuator/health/readiness; do
              code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 --retry 6 --retry-delay 5 --retry-all-errors "$S_URL$path" || true)"
              printf '[smoke %s] %-46s HTTP %s\\n' "$S_ENV" "$path" "$code"
              [ "$code" = "200" ] || { echo "[smoke $S_ENV] FALLÓ $path"; exit 1; }
            done
            echo "[smoke $S_ENV] OK: $S_URL responde"'''
    }
}
