# Seguridad: escaneos del repositorio (HU-23)

Todo corre en Docker con versiones fijas, como `infra/terraform/run.sh` y `deploy/helm/run.sh`: no hay que instalar nada.

```bash
./security/run.sh secrets        # gitleaks sobre TODO el historial (todas las ramas, también las locales sin subir)
./security/run.sh secrets-tree   # gitleaks sobre los archivos actuales
./security/run.sh selftest       # control negativo: un token falso DEBE fallar y un directorio limpio NO
./security/run.sh check          # los tres
```

## Cómo se decide qué es un secreto

[`.gitleaks.toml`](../.gitleaks.toml) extiende las reglas por defecto de gitleaks y solo agrega **excepciones revisadas una por una**, cada una con su motivo. **Cualquier hallazgo que no esté en esa lista hace fallar el escaneo.** Para agregar una excepción hay que:

1. Mirar el hallazgo (`gitleaks` imprime el archivo, la línea y la regla; los valores salen censurados).
2. Comprobar que no es una credencial: texto legible de prueba, una variable de entorno o un falso positivo de la regla.
3. Acotar la excepción lo más posible (archivo y commit concretos, no una carpeta entera) y escribir el motivo en la propia entrada.

Los secretos reales **nunca** van en el repositorio: cada ambiente genera los suyos con `openssl rand` en su propia VM ([`deploy.sh`](../jenkins/resources/scripts/deploy.sh)) o con `/dev/urandom` ([`lib.sh`](../infra/bootstrap/lib.sh)), y el chart de Kubernetes los recibe aparte ([`values-secrets.example.yaml`](../deploy/helm/circleguard/values-secrets.example.yaml) solo trae `CAMBIAR`).

## Por qué no se reescribió el historial

El único valor parecido a un secreto que trajo el código original es un texto de prueba (hexadecimal de «test-secret-…»), no una credencial. Reescribir el historial de un repositorio público con ramas protegidas, tags y releases habría cambiado todos los SHA que citan el informe y las evidencias de la Parte 1, y habría invalidado los PR abiertos. Se **rotó en la práctica** (ningún ambiente lo usó: cada uno genera los suyos al azar), se quitó el valor por defecto del código principal (`${VAULT_SECRET}` sin valor) y se agregó este escaneo para que no vuelva a pasar. Detalle en [`hu-23-secretos.md`](../docs/evidencias/sprint-2/hu-23-secretos.md).

## Lo que falta de HU-23

Trivy sobre imágenes y dependencias, Sealed Secrets, RBAC, NetworkPolicies y TLS con cert-manager. Este tramo es el de los secretos del repositorio.
