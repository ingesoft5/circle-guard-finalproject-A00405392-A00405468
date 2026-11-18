// Copia a ECR las 9 imágenes ya publicadas en Nexus con la versión indicada (HU-16). Los nodos k3s de AWS bajan de ECR con el
// rol IAM de su EC2 (sin contraseñas guardadas en los nodos), no de Nexus. Los tags de ECR son inmutables: si la versión ya
// está en un repositorio, se omite. Los repositorios los crea el módulo `registry` de Terraform (envs/tools).
// Uso: ecrPublish('1.5.0')
def call(String version) {
    String ecr = env.ECR_REGISTRY
    if (!ecr) {
        error '[ecrPublish] Falta ECR_REGISTRY (<cuenta>.dkr.ecr.<región>.amazonaws.com): la define JCasC en infra/tools/jenkins/casc/jenkins.yaml'
    }
    if (!version) {
        error '[ecrPublish] Falta la versión'
    }
    List names = changedServices.all().collect { it == 'web' ? 'circleguard-web' : it }
    withCredentials([usernamePassword(credentialsId: 'aws-terraform', usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY'),
                     usernamePassword(credentialsId: 'nexus-ci', usernameVariable: 'NX_USER', passwordVariable: 'NX_PASS')]) {
        withEnv(["ECR=${ecr}", "VERSION=${version}", "NAMES=${names.join(' ')}", "AWS_REGION=${env.AWS_REGION ?: 'us-east-1'}"]) {
            sh '''#!/usr/bin/env bash
                set -euo pipefail
                aws() { docker run --rm -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_REGION amazon/aws-cli:2.27.0 "$@"; }
                aws ecr get-login-password | docker login --username AWS --password-stdin "$ECR" >/dev/null
                echo "$NX_PASS" | docker login --username "$NX_USER" --password-stdin "$REGISTRY" >/dev/null
                for name in $NAMES; do
                  src="$REGISTRY/circleguard/$name:$VERSION"; dst="$ECR/$name:$VERSION"
                  if aws ecr describe-images --repository-name "$name" --image-ids "imageTag=$VERSION" >/dev/null 2>&1; then
                    echo "[ecr] $name:$VERSION ya está en ECR"; continue
                  fi
                  docker image inspect "$src" >/dev/null 2>&1 || docker pull -q "$src"
                  docker tag "$src" "$dst"
                  docker push -q "$dst"
                  echo "[ecr] $name:$VERSION publicada"
                done'''
        }
    }
}
