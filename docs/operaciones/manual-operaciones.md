# Manual de operaciones (HU-26, E2.9)

Cómo operar CircleGuard sin romper nada ni gastar de más: qué corre dónde, cómo encender y apagar la nube, cómo desplegar y revertir, y qué hacer cuando algo falla. Todo lo que se cita aquí ocurrió de verdad durante el proyecto.

> Este manual describe el estado después de fusionar HU-23 (#30, seguridad) y HU-16 (#23, pipeline). Los scripts y documentos que nombra viven en el repositorio; los secretos, no.

## 1. Qué corre dónde

| Pieza | Dónde | Cómo se crea | Estado normal |
|---|---|---|---|
| **Plataforma de la Parte 1**: Jenkins, SonarQube y Nexus (VM `tools`) y las VMs `dev`, `stage` y `prod` con Docker Compose | AWS `us-east-1`, 4 VMs con IP elástica fija | `infra/bootstrap/up.sh` (Bash idempotente) | **apagadas** |
| **Plataforma de la Parte 2**: VPC por ambiente, nodo k3s, ECR, S3, alarmas | AWS, un estado de Terraform por ambiente (`tools`, `dev`, `stage`, `prod`) | job `circleguard-infra` o `infra/terraform/run.sh` | **destruida** (se crea en ventanas) |
| **Estado de Terraform**: bucket S3 versionado y cifrado + tabla de bloqueo | AWS | `infra/terraform/bootstrap` (una vez) | siempre existe (centavos) |
| **La aplicación** (8 servicios + web + middleware) | Docker Compose (Parte 1) o Kubernetes con Helm (Parte 2) | `deploy/compose/`, `deploy/helm/` | solo mientras haya ventana |

Roles en Jenkins: **admin** (`admin`, `jmrojas06`), **desarrollador** (`pierrecuevas`: lanza y cancela builds, lee logs y aprueba prod; no administra ni ve credenciales) y **lectura** (`docente`). El anónimo no ve nada.

## 2. Reglas que no se rompen

1. **Nada queda prendido.** Toda vez que se enciende algo es una *ventana* corta y planeada, y se apaga en la misma sesión (sección 3).
2. **Antes de encender, preguntar a quien paga:** `up.sh`, `terraform apply` y `StartInstances` cuestan dinero (presupuesto: USD 60; ver [`costos.md`](costos.md)).
3. **Un ambiente de Terraform a la vez:** la cuenta solo permite 5 IP elásticas y las 4 de la Parte 1 ya las ocupan.
4. **Validar en local primero** (`deploy/helm/local.sh`, `scripts/sim-jenkins.sh`) y usar la nube solo para lo que no se puede probar fuera.
5. **Un PR a la vez contra `main`**, con un solo issue por PR. Nada se fusiona sin el check `continuous-integration/jenkins/pr-merge` ni la aprobación del otro integrante.
6. **Ningún secreto en el repositorio.** `./security/run.sh check` lo vigila (gitleaks, con un control que debe fallar).

## 3. Ventana de Jenkins (la más frecuente)

Jenkins, SonarQube y Nexus viven en una sola VM (`circleguard-tools`, `t3.xlarge`, ≈ USD 0,17/h). Hace falta encenderla para el check de un PR y para construir `main`.

1. **Encender** desde el checkout de `main` (no desde una rama con cambios en `infra/tools/jenkins/casc/`: `up.sh` copia esa carpeta a la VM y reconstruye la imagen de Jenkins con ella, así que cambia los roles y las credenciales):
   ```bash
   git switch main && git pull
   ./infra/bootstrap/up.sh tools        # ~3-4 min; lee las contraseñas de infra/bootstrap/.state/secrets.env
   ```
2. **Esperar a que `up.sh` termine Y a que `GET /login` dé 200.** `up.sh` recrea el contenedor de Jenkins casi al final: un build que arranque antes muere con `exit code -1` o `No build record`.
3. **Lanzar el check** si hace falta: el webhook se pierde cuando Jenkins está apagado. Reescanear con `POST /job/circleguard/build?delay=0` (necesita *crumb* y la misma cookie) o relanzar un PR con `POST /job/circleguard/job/PR-<n>/build?delay=0`.
4. **Esperar el resultado por la API de Jenkins** (`/job/circleguard/job/PR-<n>/lastBuild/api/json?tree=building,result` y `/wfapi/describe`). Un PR de solo documentos tarda ~30 s; uno que toca código de servicios, 3 a 15 min.
5. **Apagar apenas pase el check**, no al terminar de escribir la documentación:
   ```bash
   aws ec2 stop-instances --instance-ids <id de circleguard-tools>
   aws ec2 describe-instances --query 'Reservations[].Instances[].[Tags[?Key==`Name`]|[0].Value,State.Name]' --output text   # todas stopped
   ```
6. Anotar cuántos minutos estuvo encendida (la ventana del 18 nov: 11,6 min ≈ USD 0,03).

## 4. Crear, desplegar y destruir un ambiente de Kubernetes

**Crear (Terraform).** Por Jenkins: job `circleguard-infra` con `ENV`, `ACTION=plan` y luego `apply` (en prod y en `destroy` pide aprobación y se aplica el *mismo* plan). A mano:

```bash
./infra/terraform/run.sh dev plan -out=tfplan      # lee backend.hcl; ver infra/terraform/README.md
./infra/terraform/run.sh dev apply tfplan
```

Los repositorios ECR están en `envs/tools`: `ENV=tools ACTION=apply TARGET=module.registry` los crea **sin** levantar la VM ni gastar una IP.

**Desplegar.** El pipeline de `main` con `TARGET=k8s` hace ECR → kubeconfig → `deploy/helm/promote.sh` en dev, stage y prod. Para levantar un ambiente recién creado, o devolverlo a una versión ya publicada, el job `circleguard-k8s` (`ENV` y `VERSION`) hace lo mismo sobre un solo ambiente. A mano, con el kubeconfig del nodo:

```bash
KUBECONFIG_FILE=/ruta/absoluta/kubeconfig IMAGE_REGISTRY=<cuenta>.dkr.ecr.us-east-1.amazonaws.com \
  INGRESS_HOST=<ip-con-guiones>.sslip.io SMOKE_URL=https://<ip-con-guiones>.sslip.io SMOKE_INSECURE=1 \
  ./deploy/helm/promote.sh dev 1.5.0 --build 1
```

`promote.sh` instala cert-manager y Sealed Secrets y sella los secretos **solo si faltan** (cluster nuevo), despliega con `--atomic` (si los pods no quedan listos, Helm revierte solo), prueba las sondas y un login real, y deja el registro de cambios.

**Revertir.** `./deploy/helm/release.sh rollback <ambiente> [revisión]` vuelve a la última revisión buena con *otra* versión (en prod pide `--yes`). En compose: job `circleguard-rollback` (`ENV`, `VERSION`). El historial de cada ambiente: `./deploy/helm/release.sh history <ambiente> --md`.

**Destruir (siempre al terminar).**

```bash
./infra/terraform/run.sh dev destroy      # o el job circleguard-infra con ACTION=destroy
./infra/terraform/run.sh dev plan -detailed-exitcode   # "hay que crear todo": no queda nada
```

El estado remoto (bootstrap) se conserva. Un `SealedSecret` no sobrevive a un cluster nuevo: cada cluster tiene su llave y se vuelve a sellar en cada ventana (lo hace `promote.sh`).

## 5. Secretos, TLS y seguridad

- **Secretos de la aplicación:** los genera `platform.sh seal <ambiente>` con `openssl rand` y los cifra con la llave pública del cluster; el texto plano nunca toca el disco. Si `circleguard-secrets` ya existe **no se regenera** (cambiaría la contraseña de una base ya inicializada). Para rotarlos hay que borrar el Secret y el `SealedSecret` a propósito y recrear la base.
- **TLS:** cert-manager emite el certificado del Ingress. En local, con una CA propia; en la nube, `letsencrypt-staging` (por defecto: sin límite de emisión, el navegador lo marca como no confiable) o `letsencrypt` (el válido). Verificar: `kubectl -n circleguard-<ambiente> get certificate`.
- **Escaneos:** `./security/run.sh check` (secretos, IaC, dependencias, controles). Con las imágenes construidas, `./security/run.sh image <imagen>...`. HIGH y CRITICAL con arreglo publicado **bloquean**. Una excepción se agrega en `.trivyignore.yaml` **por CVE, con motivo y fecha de vencimiento**; nunca por carpeta ni sin fecha. Deuda aceptada: 74 CVE de Spring Boot 3.2.4 (vence 2026-12-31) y 74 hallazgos npm de la cadena de Expo (vence 2027-01-31). Detalle: [`seguridad.md`](../arquitectura/seguridad.md).
- **Red:** todo el tráfico de entrada está cerrado salvo la tabla `networkPolicy.allow`. Un servicio nuevo que llame a otro debe agregarse ahí o `helm template` falla.

## 6. Cuando algo falla

| Síntoma | Causa | Qué hacer |
|---|---|---|
| El build de `main` muere con `script returned exit code -1` / `after Jenkins restart` | `up.sh` reinicia Jenkins al final y mata el build en curso | esperar a que `up.sh` termine y relanzar `main` (`POST /job/circleguard/job/main/build?delay=0`) |
| El check de un PR queda en `ERROR` o no existe el job `PR-<n>` | Jenkins estaba apagado y se perdió el webhook | reescanear el multibranch o relanzar el PR (sección 3) |
| `commitlint`: `header must not be longer than 100 characters` | un commit con la primera línea larga | `git log origin/main..HEAD --format=%s \| awk 'length>100'` antes de subir; reescribir ese commit |
| `AddressLimitExceeded` al aplicar Terraform | la cuenta permite 5 IP elásticas y hay 4 reservadas | aplicar un solo ambiente a la vez |
| El nodo k3s local no agenda pods (`disk-pressure`) | al disco del equipo le queda < 10 % | liberar disco; `local.sh up` ya baja el umbral a 1 % en el cluster de pruebas |
| `ignore file not found` o un escaneo «vacío» dentro de Jenkins | una ruta `-v` del contenedor de Jenkins que el host no ve | usar los runners (`scripts/hostpath.sh` traduce las rutas) y no `docker run -v $PWD` a pelo |
| `Trivy: connection reset` al bajar la base de datos de Java | red inestable con `ghcr.io` | bajarla con `curl` y usar `TRIVY_SKIP_DB_UPDATE=true TRIVY_SKIP_JAVA_DB_UPDATE=true` |
| `circleguard-secrets ya existe` al sellar | es la protección: no se regenera | nada que arreglar; para rotar, ver sección 5 |
| `helm upgrade --atomic` falla y revierte | los pods nuevos no quedaron listos (imagen inexistente, sonda, recursos) | `kubectl -n circleguard-<ambiente> describe pod`; el ambiente sigue en la versión anterior |
| `ImagePullBackOff` en un nodo de AWS | la versión no está en ECR o el tag ya existe (inmutable) | `deployK8s` sube las imágenes con `ecrPublish`; los tags de ECR no se pisan |
| `terraform plan` dice que hay cambios sin que nadie tocara el código | alguien cambió algo a mano en la consola (*drift*) | mirar el detalle; el job `circleguard-drift` lo avisa cada día |

## 7. Qué no cubre este manual

- La **observabilidad** (Prometheus, Grafana, Alertmanager, Jaeger, Loki) y las **pruebas de carga y seguridad** (Locust, OWASP ZAP) son las historias HU-20 a HU-22 y HU-24; su operación se agrega cuando se fusionen.
- Un **dominio propio con TLS válido de producción**: se usa `sslip.io` con Let's Encrypt.
- **Alta disponibilidad**: cada ambiente es un solo nodo (D2-03: aislamiento y costo por encima de redundancia).
