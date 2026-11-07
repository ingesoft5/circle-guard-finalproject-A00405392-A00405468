// Pipeline de CircleGuard (HU-04 CI · HU-08/09/10 CD). Multibranch: en cada PR corre la CI; en main, además,
// calcula la versión con semantic-release, publica las 9 imágenes en Nexus y las promueve
// dev -> E2E -> stage -> aprobación manual -> prod. Discord y WhatsApp avisan fallos, la espera y el resultado.
// Las etapas reutilizables viven en la Shared Library de jenkins/ (cargada desde la misma rama).
library "circleguard@${env.CHANGE_BRANCH ?: env.BRANCH_NAME}"

// Variables del binding del script: visibles desde todas las etapas.
SERVICES = []          // servicios tocados por el cambio (ejecución selectiva de la CI)
RELEASE_VERSION = ''   // versión calculada en main; vacía = ningún commit la amerita y no se despliega

pipeline {
    // Sin agente global: la espera de aprobación para prod no ocupa un ejecutor (solo hay 2).
    agent none

    options {
        timestamps()
        ansiColor('xterm')
        disableConcurrentBuilds(abortPrevious: true)
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 4, unit: 'HOURS')   // CI + publicación (~30 min) y hasta 2 h de espera de aprobación
    }

    environment {
        GRADLE_OPTS = '-Dorg.gradle.daemon=false -Dorg.gradle.jvmargs=-Xmx1536m'
    }

    stages {
        stage('CI y entrega a dev y stage') {
            agent any
            stages {
                stage('Preparar') {
                    steps {
                        script {
                            SERVICES = changedServices()
                            currentBuild.description = SERVICES ? "Servicios: ${SERVICES.join(', ')}" : 'Sin cambios en servicios'
                        }
                    }
                }

                stage('Commitlint') {
                    steps { commitLint() }
                }

                stage('Build') {
                    when { expression { !SERVICES.isEmpty() } }
                    steps { runGradle(SERVICES, ['classes', 'testClasses']) }
                }

                stage('Unit tests + JaCoCo') {
                    when { expression { !SERVICES.isEmpty() } }
                    steps { runGradle(SERVICES, ['test', 'jacocoTestReport']) }
                    post {
                        always {
                            junit allowEmptyResults: true, testResults: 'services/*/build/test-results/test/*.xml'
                            recordCoverage tools: [[parser: 'JACOCO', pattern: 'services/*/build/reports/jacoco/test/jacocoTestReport.xml']],
                                           id: 'jacoco', name: 'Cobertura JaCoCo', sourceCodeRetention: 'EVERY_BUILD',
                                           sourceDirectories: SERVICES.findAll { it != 'web' }.collect { [path: "services/${it}/src/main/java"] }
                        }
                    }
                }

                stage('SonarQube + Quality Gate') {
                    when { expression { SERVICES.any { it != 'web' } } }
                    steps {
                        script {
                            SERVICES.findAll { it != 'web' }.each { svc -> sonarScan(svc) }
                        }
                    }
                }

                stage('Integration tests') {
                    when { expression { !runGradle.withTask(SERVICES, 'integrationTest').isEmpty() } }
                    steps { runGradle(runGradle.withTask(SERVICES, 'integrationTest'), ['integrationTest']) }
                    post {
                        always { junit allowEmptyResults: true, testResults: 'services/*/build/test-results/integrationTest/*.xml' }
                    }
                }

                // ------------------------------------------------ solo en main: versión, imágenes y despliegue
                stage('Release (semantic-release)') {
                    when { branch 'main' }
                    steps {
                        script {
                            RELEASE_VERSION = semanticRelease()
                            currentBuild.description = (RELEASE_VERSION ? "v${RELEASE_VERSION}" : 'Sin versión nueva') + " · ${currentBuild.description}"
                        }
                    }
                    post {
                        success { archiveArtifacts artifacts: 'CHANGELOG.md', allowEmptyArchive: true }
                    }
                }

                stage('Package + Publish') {
                    when { allOf { branch 'main'; expression { RELEASE_VERSION as boolean } } }
                    steps {
                        script {
                            // Las 9 imágenes llevan la misma versión: el compose de cada ambiente usa un solo IMAGE_TAG.
                            def published = changedServices.all().collect { svc -> dockerPublish(svc, RELEASE_VERSION) }.findAll { it }
                            echo "Imágenes publicadas (${published.size()}):\n${published.join('\n')}"
                        }
                    }
                }

                stage('Deploy dev') {
                    when { allOf { branch 'main'; expression { RELEASE_VERSION as boolean } } }
                    steps {
                        deployCompose('dev', RELEASE_VERSION)
                        smokeTest('dev')
                    }
                }

                stage('E2E contra dev') {
                    when { allOf { branch 'main'; expression { RELEASE_VERSION as boolean } } }
                    steps { e2eTests('dev') }
                    post {
                        always {
                            junit allowEmptyResults: true, testResults: 'tests/e2e-playwright/results/*.xml'
                            publishHTML([reportDir: 'tests/e2e-playwright/playwright-report', reportFiles: 'index.html',
                                         reportName: 'Playwright E2E (dev)', allowMissing: true, keepAll: true, alwaysLinkToLastBuild: true])
                        }
                    }
                }

                stage('Deploy stage') {
                    when { allOf { branch 'main'; expression { RELEASE_VERSION as boolean } } }
                    steps {
                        deployCompose('stage', RELEASE_VERSION)
                        smokeTest('stage')
                        notifyTeam('APPROVAL', "v${RELEASE_VERSION} ya está en dev y stage. Espera aprobación para prod: ${env.BUILD_URL}input/")
                    }
                }
            }
            post {
                failure { notifyTeam('FAILURE') }
                fixed { notifyTeam('SUCCESS', "El pipeline volvió a verde en ${env.BRANCH_NAME}.") }
                cleanup { sh 'rm -rf .commitlint .sonar .scannerwork .release .deploy.sh .notify.sh || true' }
            }
        }

        stage('Aprobación para prod') {
            when { allOf { branch 'main'; expression { RELEASE_VERSION as boolean } } }
            steps {
                timeout(time: 2, unit: 'HOURS') {
                    input message: "¿Promover v${RELEASE_VERSION} a PROD?", ok: 'Desplegar en prod', submitter: 'jmrojas06,pierrecuevas'
                }
            }
        }

        stage('Deploy prod') {
            agent any
            when { beforeAgent true; allOf { branch 'main'; expression { RELEASE_VERSION as boolean } } }
            steps {
                deployCompose('prod', RELEASE_VERSION)
                smokeTest('prod')
            }
            post {
                success { notifyTeam('SUCCESS', "v${RELEASE_VERSION} desplegada en prod: ${env.PROD_URL}") }
                failure { notifyTeam('FAILURE', "Falló el despliegue de v${RELEASE_VERSION} en prod") }
                cleanup { sh 'rm -f .deploy.sh .notify.sh || true' }
            }
        }
    }

    post {
        aborted {
            script {
                if (RELEASE_VERSION) {
                    node { notifyTeam('ABORTED', "v${RELEASE_VERSION} quedó en dev y stage: no se aprobó para prod a tiempo") }
                }
            }
        }
    }
}
