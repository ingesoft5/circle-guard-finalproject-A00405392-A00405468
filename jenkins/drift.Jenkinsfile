// Job "circleguard-drift" (HU-16, #23; el criterio pasó de HU-14): plan programado de Terraform que detecta cambios hechos a
// mano en la consola de AWS. Corre cada día y mira cada ambiente que ESTÉ desplegado (los demás se omiten: sin recursos no
// hay nada que comparar). Si algo difiere el build queda INESTABLE y avisa; si no pudo planificar, falla. No cambia nada: solo
// `plan -detailed-exitcode -lock=false` (infra/terraform/run.sh drift).
library 'circleguard@main'

pipeline {
    agent any

    triggers { cron('H 6 * * *') }   // una vez al día, hacia la 1 a. m. de Colombia

    options {
        timestamps()
        ansiColor('xterm')
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }

    stages {
        stage('Detectar drift') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'aws-terraform', usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    script {
                        int rc = sh(returnStatus: true, script: '''#!/usr/bin/env bash
                            set -uo pipefail
                            rc=0; checked=0
                            for e in tools dev stage prod; do
                              # Con las credenciales o el backend rotos `state list` falla (código != 0): eso NO es «no desplegado».
                              out="$(./infra/terraform/run.sh "$e" state list 2>&1)" || { echo "ERROR: no pude leer el estado de $e: ${out:0:200}"; rc=1; continue; }
                              if [ -z "$out" ]; then echo "== $e: sin recursos (no desplegado): no hay drift que buscar"; continue; fi
                              checked=$((checked + 1))
                              ./infra/terraform/run.sh drift "$e" || { c=$?; [ "$c" -gt "$rc" ] && rc=$c; }
                            done
                            echo "[drift] ambientes comparados: $checked"
                            exit "$rc"''')
                        if (rc == 2) {
                            unstable 'Drift: hay ambientes que difieren del código (mira el log)'
                            notifyTeam('UNSTABLE', "Terraform detectó drift: ${env.BUILD_URL}console")
                        } else if (rc != 0) {
                            error "No se pudo planificar algún ambiente (código ${rc})"
                        }
                    }
                }
            }
        }
    }

    post {
        failure { notifyTeam('FAILURE', "El job de drift no pudo planificar: ${env.BUILD_URL}console") }
        cleanup { sh 'rm -f infra/terraform/backend.hcl .notify.sh || true' }
    }
}
