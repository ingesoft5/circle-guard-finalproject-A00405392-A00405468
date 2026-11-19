// Job "circleguard-k8s" (HU-16, #23): despliega una versión YA PUBLICADA en Nexus en un ambiente de Kubernetes en AWS. Sirve para
// levantar un ambiente recién creado con circleguard-infra y para volver a una versión anterior (las imágenes son inmutables:
// un rollback es desplegar otra versión). Hace lo mismo que las etapas de despliegue de `main` con TARGET=k8s (deployK8s): copia
// las imágenes a ECR, saca el kubeconfig del nodo, y deploy/helm/promote.sh deja la plataforma, los secretos, helm --atomic y la
// prueba de humo. Lo crea JCasC y corre jenkins/k8s.Jenkinsfile de main.
library 'circleguard@main'

pipeline {
    agent any

    parameters {
        choice(name: 'ENV', choices: ['dev', 'stage', 'prod'], description: 'Ambiente de Kubernetes (debe existir: job circleguard-infra)')
        string(name: 'VERSION', defaultValue: '', trim: true,
               description: 'Versión a desplegar, sin la v (p. ej. 1.4.0). Tiene que estar publicada en Nexus (ver GitHub Releases).')
        choice(name: 'TLS_ISSUER', choices: ['letsencrypt-staging', 'letsencrypt'],
               description: 'Emisor del certificado (staging no tiene límite de emisión; letsencrypt es el válido)')
    }

    options {
        timestamps()
        ansiColor('xterm')
        disableConcurrentBuilds()
        timeout(time: 1, unit: 'HOURS')   // incluye hasta 15 min de espera de la aprobación de prod
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    stages {
        stage('Validar') {
            steps {
                script {
                    if (!(params.VERSION ==~ /\d+\.\d+\.\d+/)) {
                        error 'Indica VERSION con formato 1.4.0 (sin la v)'
                    }
                    currentBuild.description = "${params.ENV} <- v${params.VERSION}"
                }
            }
        }

        stage('Confirmar (solo prod)') {
            when { expression { params.ENV == 'prod' } }
            steps {
                timeout(time: 15, unit: 'MINUTES') {
                    input message: "¿Desplegar v${params.VERSION} en PROD (Kubernetes)?", ok: 'Desplegar', submitter: 'jmrojas06,pierrecuevas'
                }
            }
        }

        stage('Desplegar en Kubernetes') {
            steps { deployK8s(params.ENV, params.VERSION) }
        }
    }

    post {
        success { notifyTeam('SUCCESS', "Kubernetes: ${params.ENV} corre v${params.VERSION}") }
        failure { notifyTeam('FAILURE', "Falló el despliegue de v${params.VERSION} en Kubernetes (${params.ENV})") }
        cleanup { sh 'rm -f .notify.sh infra/terraform/backend.hcl history-*.md || true' }
    }
}
