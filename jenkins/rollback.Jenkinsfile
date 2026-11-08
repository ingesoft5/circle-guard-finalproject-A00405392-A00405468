// Job "circleguard-rollback" (HU-10, #17): devuelve dev, stage o prod a una versión anterior ya publicada en Nexus.
// Lo crea JCasC (infra/tools/jenkins/casc/jenkins.yaml) y usa los mismos pasos de la Shared Library que el
// despliegue normal: como las imágenes son inmutables, un rollback es volver a desplegar un IMAGE_TAG anterior.
library 'circleguard@main'

pipeline {
    agent any

    parameters {
        choice(name: 'ENV', choices: ['dev', 'stage', 'prod'], description: 'Ambiente a devolver')
        string(name: 'VERSION', defaultValue: '', trim: true,
               description: 'Versión a restaurar, sin la v (p. ej. 1.1.0). Ver GitHub Releases o el historial que imprime la primera etapa.')
    }

    options {
        timestamps()
        ansiColor('xterm')
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    stages {
        stage('Historial del ambiente') {
            steps {
                releaseHistory(params.ENV)
                script {
                    if (!params.VERSION) {
                        error 'Indica VERSION (p. ej. 1.1.0): la versión a la que se vuelve'
                    }
                    currentBuild.description = "${params.ENV} -> v${params.VERSION}"
                }
            }
        }

        stage('Confirmar (solo prod)') {
            when { expression { params.ENV == 'prod' } }
            steps {
                timeout(time: 15, unit: 'MINUTES') {
                    input message: "¿Devolver PROD a v${params.VERSION}?", ok: 'Sí, hacer rollback', submitter: 'jmrojas06,pierrecuevas'
                }
            }
        }

        stage('Rollback') {
            steps {
                deployCompose(params.ENV, params.VERSION, 'rollback')
                smokeTest(params.ENV)
            }
        }
    }

    post {
        success { notifyTeam('SUCCESS', "Rollback: ${params.ENV} volvió a v${params.VERSION}") }
        failure { notifyTeam('FAILURE', "Falló el rollback de ${params.ENV} a v${params.VERSION}") }
        cleanup { sh 'rm -f .deploy.sh .notify.sh || true' }
    }
}
