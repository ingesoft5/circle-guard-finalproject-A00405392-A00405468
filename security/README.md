# Seguridad: escaneos del repositorio (HU-23)

Todo corre en Docker con versiones fijas, como `infra/terraform/run.sh` y `deploy/helm/run.sh`: no hay que instalar nada.

```bash
./security/run.sh secrets        # gitleaks sobre TODO el historial (todas las ramas, también las locales sin subir)
./security/run.sh secrets-tree   # gitleaks sobre los archivos actuales
./security/run.sh iac            # Trivy: Dockerfile, chart de Helm (renderizado) y Terraform; HIGH/CRITICAL bloquea
./security/run.sh deps           # Trivy: dependencias npm con lockfile; HIGH/CRITICAL con arreglo bloquea
./security/run.sh image          # Trivy: las imágenes local/circleguard-*:local (o las referencias que pases)
./security/run.sh selftest       # controles negativos: un token, un Dockerfile y un lockfile malos DEBEN fallar y los buenos NO
./security/run.sh check          # todo menos `image` (que necesita las imágenes construidas)
```

Sin red o con el registro de Trivy caído: `TRIVY_SKIP_DB_UPDATE=true TRIVY_SKIP_JAVA_DB_UPDATE=true ./security/run.sh ...` usa la base que ya esté en la caché (`~/.cache/circleguard-iac/trivy`, ~2 GB, compartida con `infra/terraform/run.sh`).

## Cómo se decide qué es un secreto

[`.gitleaks.toml`](../.gitleaks.toml) extiende las reglas por defecto de gitleaks y solo agrega **excepciones revisadas una por una**, cada una con su motivo. **Cualquier hallazgo que no esté en esa lista hace fallar el escaneo.** Para agregar una excepción hay que:

1. Mirar el hallazgo (`gitleaks` imprime el archivo, la línea y la regla; los valores salen censurados).
2. Comprobar que no es una credencial: texto legible de prueba, una variable de entorno o un falso positivo de la regla.
3. Acotar la excepción lo más posible (archivo y commit concretos, no una carpeta entera) y escribir el motivo en la propia entrada.

Los secretos reales **nunca** van en el repositorio: cada ambiente genera los suyos con `openssl rand` en su propia VM ([`deploy.sh`](../jenkins/resources/scripts/deploy.sh)) o con `/dev/urandom` ([`lib.sh`](../infra/bootstrap/lib.sh)), y el chart de Kubernetes los recibe aparte ([`values-secrets.example.yaml`](../deploy/helm/circleguard/values-secrets.example.yaml) solo trae `CAMBIAR`).

## Por qué no se reescribió el historial

El único valor parecido a un secreto que trajo el código original es un texto de prueba (hexadecimal de «test-secret-…»), no una credencial. Reescribir el historial de un repositorio público con ramas protegidas, tags y releases habría cambiado todos los SHA que citan el informe y las evidencias de la Parte 1, y habría invalidado los PR abiertos. Se **rotó en la práctica** (ningún ambiente lo usó: cada uno genera los suyos al azar), se quitó el valor por defecto del código principal (`${VAULT_SECRET}` sin valor) y se agregó este escaneo para que no vuelva a pasar. Detalle en [`hu-23-secretos.md`](../docs/evidencias/sprint-2/hu-23-secretos.md).

## Trivy: umbral bloqueante y excepciones con vencimiento

`iac`, `deps` e `image` fallan con HIGH o CRITICAL (en imágenes y dependencias, solo si hay arreglo publicado: `--ignore-unfixed`). Las excepciones están en [`.trivyignore.yaml`](../.trivyignore.yaml), **cada una con motivo y fecha de vencimiento**, y se acotan por CVE o por archivo: un hallazgo nuevo, aunque sea del mismo paquete, vuelve a bloquear. Para agregar una, se aplican los mismos tres pasos de arriba (mirar, comprobar que no se puede arreglar ya, acotar y explicar).

Detalles que importan, todos aprendidos fallando:

- **El chart se renderiza antes de escanearlo** (`--helm-set global.imageTag=...`). Sin eso Trivy lo salta sin avisar: dio 0 hallazgos, y al renderizarlo salieron 44 HIGH. El runner exige que el resumen nombre el chart, los Dockerfile y Terraform.
- **Los controles negativos** (`selftest`) arman un Dockerfile que corre como root y un lockfile con una dependencia crítica y comprueban que el escaneo los rechaza; sin eso, un escaneo que nunca falla parece verde.
- **Para escanear imágenes locales** el contenedor de Trivy necesita el socket de Docker y el grupo que lo posee (`--group-add`), porque corre con tu usuario.

Resumen del modelo de seguridad completo (red, pods, RBAC, secretos, TLS): [`docs/arquitectura/seguridad.md`](../docs/arquitectura/seguridad.md). Evidencia: [`hu-23-seguridad.md`](../docs/evidencias/sprint-2/hu-23-seguridad.md).
