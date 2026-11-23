// ZAP baseline (HU-24, #31) contra un ambiente desplegado: análisis pasivo de seguridad de la web (cabeceras,
// cookies, fugas de información). Las reglas viven en tests/security/zap/reglas.conf: las marcadas FAIL (las cabeceras
// que ya se corrigieron) hacen fallar el build; las IGNORE son falsos positivos revisados.
//
// Igual que e2eTests: docker.image().inside y no tests/security/zap/run.sh, porque Jenkins corre en un contenedor
// con el socket del host y la ruta del workspace no existe en el host. zap-baseline.py lee la configuración y
// escribe los informes en /zap/wrk, así que se enlaza esa carpeta al workspace.
// Uso: zapBaseline('dev')
void call(String envName) {
    String url = env."${envName.toUpperCase()}_URL"
    withEnv(["ZAP_TARGET=${url}"]) {
        docker.image('ghcr.io/zaproxy/zaproxy:2.15.0').inside('--entrypoint= -u root') {
            sh '''mkdir -p tests/security/zap/reports/pipeline
                  cp tests/security/zap/reglas.conf tests/security/zap/reports/pipeline/
                  rm -rf /zap/wrk && ln -s "$PWD/tests/security/zap/reports/pipeline" /zap/wrk
                  trap 'chown -R "$(stat -c %u:%g .)" tests/security/zap/reports' EXIT
                  echo "[zap] baseline contra $ZAP_TARGET"
                  zap-baseline.py -t "$ZAP_TARGET" -c reglas.conf -r informe.html -J informe.json -I'''
        }
    }
}
