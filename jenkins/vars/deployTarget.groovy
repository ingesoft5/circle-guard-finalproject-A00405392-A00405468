// Despliega con el destino que eligió el build (parámetro TARGET del Jenkinsfile): `compose` (por defecto) son las VMs de la
// Parte 1 por SSH; `k8s` es Kubernetes en AWS con Helm (HU-16). Los dos terminan con una prueba de humo del ambiente.
// Uso: deployTarget('dev', '1.5.0')
def call(String envName, String version) {
    if ((params.TARGET ?: 'compose') == 'k8s') {
        deployK8s(envName, version)
    } else {
        deployCompose(envName, version)
        smokeTest(envName)
    }
}
