// Job "circleguard-infra" (HU-16, #23): Terraform gobernado por Jenkins. Valida, escanea, hace `plan` y, si se pidió,
// `apply` o `destroy` de UN ambiente; en prod y en cualquier destroy un humano aprueba el plan antes de aplicarlo.
// Lo crea JCasC (infra/tools/jenkins/casc/jenkins.yaml). Las credenciales de AWS (credencial aws-terraform) y las variables del
// ambiente (TF_VAR_*, TF_STATE_BUCKET) también salen de JCasC: nada de eso se versiona. Se aplica de a un ambiente porque la
// cuenta solo permite 5 IP elásticas (las 4 de la Parte 1 más una). Los pasos reales los hace infra/terraform/run.sh (Docker).
library 'circleguard@main'

pipeline {
    agent any

    parameters {
        choice(name: 'ENV', choices: ['dev', 'stage', 'prod', 'tools'], description: 'Ambiente de Terraform (cada uno tiene su propio estado)')
        choice(name: 'ACTION', choices: ['plan', 'apply', 'destroy'],
               description: 'plan solo muestra qué cambiaría; apply crea o cambia; destroy borra TODO el ambiente')
        string(name: 'TARGET', defaultValue: '', trim: true,
               description: 'Opcional: limita la operación a un recurso o módulo (-target). Ej.: module.registry en tools crea solo los repositorios ECR, sin la VM')
    }

    options {
        timestamps()
        ansiColor('xterm')
        disableConcurrentBuilds()
        timeout(time: 2, unit: 'HOURS')   // incluye hasta 1 h de espera de la aprobación
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    stages {
        stage('Validar el código') {
            steps {
                script { currentBuild.description = "${params.ENV} · ${params.ACTION}${params.TARGET ? ' · ' + params.TARGET : ''}" }
                sh './infra/terraform/run.sh fmt'
                sh './infra/terraform/run.sh validate'
            }
        }

        // La misma compuerta de Trivy que la CI: un hallazgo HIGH/CRITICAL en el Terraform detiene el plan.
        stage('Trivy sobre la infraestructura') {
            steps { sh './security/run.sh iac' }
        }

        stage('Plan') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'aws-terraform', usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    withEnv(["T_ENV=${params.ENV}", "T_ACTION=${params.ACTION}", "T_TARGET=${params.TARGET}"]) {
                        sh '''#!/usr/bin/env bash
                            set -euo pipefail
                            args=(-input=false -out=tfplan); target=()
                            [ "$T_ACTION" = destroy ] && args+=(-destroy)
                            [ -n "${T_TARGET:-}" ] && target=("-target=${T_TARGET}")
                            ./infra/terraform/run.sh "$T_ENV" plan "${args[@]}" ${target[@]+"${target[@]}"}
                            ./infra/terraform/run.sh "$T_ENV" show -no-color tfplan > "plan-$T_ENV.txt"
                            echo "[infra] plan de $T_ENV ($T_ACTION) guardado en plan-$T_ENV.txt"'''
                    }
                }
            }
            post {
                always { archiveArtifacts artifacts: 'plan-*.txt', allowEmptyArchive: true }
            }
        }

        stage('Aprobación') {
            when { allOf { expression { params.ACTION != 'plan' }; expression { params.ENV == 'prod' || params.ACTION == 'destroy' } } }
            steps {
                script {
                    notifyTeam('APPROVAL', "${params.ACTION} en ${params.ENV} espera aprobación: ${env.BUILD_URL}input/")
                    timeout(time: 1, unit: 'HOURS') {
                        input message: "¿Aplicar el plan (${params.ACTION}) en ${params.ENV.toUpperCase()}? Revisa plan-${params.ENV}.txt en los artefactos.",
                              ok: 'Aplicar', submitter: 'jmrojas06,pierrecuevas'
                    }
                }
            }
        }

        stage('Apply') {
            when { expression { params.ACTION != 'plan' } }
            steps {
                withCredentials([usernamePassword(credentialsId: 'aws-terraform', usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    // Se aplica el MISMO plan que se revisó (tfplan): si el mundo cambió entre el plan y el apply, Terraform se niega.
                    sh "./infra/terraform/run.sh ${params.ENV} apply -input=false tfplan"   // ENV es un choice: solo toma valores de la lista
                }
            }
        }
    }

    post {
        success { notifyTeam('SUCCESS', "Terraform ${params.ACTION} de ${params.ENV} terminó bien") }
        failure { notifyTeam('FAILURE', "Falló Terraform ${params.ACTION} de ${params.ENV}") }
        cleanup { sh 'rm -f infra/terraform/envs/*/tfplan plan-*.txt infra/terraform/backend.hcl .notify.sh || true' }
    }
}
