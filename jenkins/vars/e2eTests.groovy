// Pruebas E2E con Playwright contra un ambiente (HU-07, #14). La suite vive en tests/e2e-playwright/ y toma
// el ambiente de la variable BASE_URL; deja el reporte HTML en reports/html/ y el JUnit en reports/junit.xml.
// Un E2E en rojo frena la promoción a stage. Mientras la suite no exista en la rama, el paso lo avisa y no bloquea.
//
// Se corre con docker.image().inside y no con tests/e2e-playwright/run.sh: run.sh hace `docker run -v $PWD:/e2e`,
// y Jenkins corre en un contenedor con el socket del host, donde la ruta del workspace no existe en el host.
// `inside` monta el workspace correctamente. La imagen debe coincidir con la versión de @playwright/test.
// Uso: e2eTests('dev')
boolean call(String envName) {
    String url = env."${envName.toUpperCase()}_URL"
    if (!fileExists('tests/e2e-playwright/package.json')) {
        echo "[e2e] tests/e2e-playwright no existe todavía (HU-07, #14): se omite el E2E contra ${envName}"
        return false
    }
    withEnv(["BASE_URL=${url}", "E2E_ENV=${envName}"]) {
        docker.image('mcr.microsoft.com/playwright:v1.49.1-noble').inside('--ipc=host --entrypoint= -u root') {
            // Como root dentro del contenedor (Chromium lo necesita); al salir se devuelve la propiedad de los
            // archivos al usuario de Jenkins para que el workspace se pueda limpiar.
            sh '''cd tests/e2e-playwright
                  trap 'chown -R "$(stat -c %u:%g .)" .' EXIT
                  echo "[e2e] Playwright contra $E2E_ENV ($BASE_URL)"
                  CI=true npm ci --no-audit --no-fund --loglevel=error
                  CI=true npx playwright test'''
        }
    }
    return true
}
