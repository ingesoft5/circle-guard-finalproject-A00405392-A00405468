// Pruebas E2E con Playwright contra un ambiente (HU-07, #14). Contrato con tests/e2e-playwright/:
//   bash tests/e2e-playwright/run.sh <BASE_URL>   -> termina con código != 0 si algún E2E falla
//   deja tests/e2e-playwright/playwright-report/index.html (reporte HTML) y tests/e2e-playwright/results/*.xml (JUnit)
// Un E2E en rojo frena la promoción a stage. Mientras la suite no exista en la rama, el paso lo avisa y no bloquea.
// Uso: e2eTests('dev')
boolean call(String envName) {
    String url = env."${envName.toUpperCase()}_URL"
    if (!fileExists('tests/e2e-playwright/run.sh')) {
        echo "[e2e] tests/e2e-playwright/run.sh no existe todavía (HU-07, #14): se omite el E2E contra ${envName}"
        return false
    }
    withEnv(["E2E_URL=${url}", "E2E_ENV=${envName}"]) {
        sh 'echo "[e2e] Playwright contra $E2E_ENV ($E2E_URL)"; bash tests/e2e-playwright/run.sh "$E2E_URL"'
    }
    return true
}
