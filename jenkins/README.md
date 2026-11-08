# Pipeline de Jenkins y *Shared Library*

Todo el CI/CD de CircleGuard vive en el repositorio: el [`Jenkinsfile`](../Jenkinsfile) de la raíz, esta carpeta (la *Shared Library*) y la configuración de Jenkins como código en [`infra/tools/jenkins/casc/jenkins.yaml`](../infra/tools/jenkins/casc/jenkins.yaml). Nada se configura a mano en la interfaz.

## Cómo se conecta todo

| Pieza | Dónde | Qué hace |
|---|---|---|
| Job multibranch `circleguard` | JCasC (Job DSL) | Descubre `main` y cada PR; lo disparan los *webhooks* de GitHub (`push` y `pull_request`). Un escaneo diario queda solo de respaldo si se pierde un *webhook*. |
| `Jenkinsfile` | raíz del repo | Pipeline declarativo. Empieza con `library "circleguard@${env.CHANGE_BRANCH ?: env.BRANCH_NAME}"`: carga la librería **de la misma rama**, así un PR prueba sus propios cambios en los pasos. |
| Librería `circleguard` | esta carpeta (`libraryPath: jenkins/`) | Los pasos reutilizables de `vars/` y los scripts de `resources/scripts/`. |
| Job `circleguard-rollback` | JCasC + [`rollback.Jenkinsfile`](rollback.Jenkinsfile) | Devuelve un ambiente a una versión anterior (ver [Rollback](#rollback)). |

## Qué corre y cuándo

| Etapa | PR | `main` | Paso de la librería |
|---|---|---|---|
| Preparar | ✅ | ✅ | `changedServices` |
| Commitlint | título del PR y cada commit | último commit | `commitLint` |
| Build | servicios afectados | servicios afectados | `runGradle` |
| Unit tests + JaCoCo | servicios afectados | servicios afectados | `runGradle` |
| SonarQube + Quality Gate | servicios Java afectados | servicios Java afectados | `sonarScan` |
| Integration tests | servicios con tarea `integrationTest` | ídem | `runGradle` |
| Release (semantic-release) | — | ✅ | `semanticRelease` |
| Package + Publish (las 9 imágenes) | — | solo si hay versión nueva | `dockerPublish` |
| Deploy dev + prueba de humo | — | solo si hay versión nueva | `deployCompose`, `smokeTest` |
| E2E contra dev (Playwright) | — | solo si hay versión nueva | `e2eTests` |
| Deploy stage + prueba de humo + aviso | — | solo si hay versión nueva | `deployCompose`, `smokeTest`, `notifyTeam` |
| Aprobación para prod (`input`) | — | solo si hay versión nueva | — |
| Deploy prod + prueba de humo | — | solo si hay versión nueva | `deployCompose`, `smokeTest` |

Un `main` sin versión nueva (solo commits `docs`, `ci`, `test`, `chore`…) corre únicamente las etapas de CI de los servicios que cambiaron: no publica ni despliega. Un E2E o una prueba de humo en rojo frena la promoción al ambiente siguiente.

### Ejecución selectiva (`changedServices`)

- **PR:** lo que cambió respecto a la rama destino (`git merge-base`).
- **`main`:** lo que cambió desde el último build exitoso; si no lo hay, desde el commit anterior.
- Si cambia algo **común** (`build.gradle.kts`, `settings.gradle.kts`, `gradle/`, `gradlew`, `Jenkinsfile` o cualquier archivo de `jenkins/`), se construyen **todos** los servicios y el cliente web.
- Si no hay referencia para comparar, también se construyen todos.
- En la publicación de `main` las 9 imágenes se construyen siempre, con la misma versión, porque el *compose* de cada ambiente usa un único `IMAGE_TAG`.

## Referencia de pasos (`vars/`)

| Paso | Uso | Qué hace |
|---|---|---|
| `changedServices` | `changedServices()` · `changedServices.all()` | Devuelve la lista de servicios a construir (reglas de arriba). `all()` son los 8 servicios de `services/` más `web`. |
| `commitLint` | `commitLint()` | Valida [Conventional Commits](../CONTRIBUTING.md#2-conventional-commits) con commitlint (`commitlint.config.cjs`) dentro de un contenedor de Node. En PR valida también el título del PR, que es el mensaje que queda en `main` tras el *squash*. |
| `runGradle` | `runGradle(['circleguard-auth-service'], ['test'])` | Corre tareas de Gradle solo en esos servicios (`--no-daemon --continue`). El cliente web no es un proyecto Gradle y se ignora. `runGradle.withTask(servicios, 'integrationTest')` filtra los que definen esa tarea. |
| `sonarScan` | `sonarScan('circleguard-auth-service')` | Analiza el servicio en SonarQube (un proyecto por servicio, con la cobertura de JaCoCo) y espera el *quality gate*: si no pasa, el build falla. |
| `semanticRelease` | `String v = semanticRelease()` | En `main`, lee los commits desde el último tag, crea el tag `vX.Y.Z` y la *GitHub Release* con las notas, y deja `CHANGELOG.md` como artefacto. Devuelve la versión, o vacío si ningún commit la amerita. Configuración en [`.releaserc.json`](../.releaserc.json). |
| `dockerPublish` | `dockerPublish('circleguard-auth-service', '1.0.0')` | Construye con la raíz del repo como contexto y publica en Nexus con dos *tags*: la versión y `sha-<commit>`. Nunca usa `latest`. |
| `deployCompose` | `deployCompose('prod', '1.0.0')` · `deployCompose('prod', '0.9.0', 'rollback')` | Copia `deploy/compose/` a la VM por SSH y ejecuta `deploy.sh`. Sirve igual para avanzar o para volver atrás: las imágenes son inmutables. |
| `smokeTest` | `smokeTest('dev')` | Comprueba que el ambiente responde por su nginx: `/healthz` y el *readiness* de los 5 servicios núcleo, con reintentos. Si algo no da 200, la etapa falla. |
| `e2eTests` | `e2eTests('dev')` | Corre la suite de Playwright ([`tests/e2e-playwright/`](../tests/e2e-playwright/)) contra el ambiente, dentro de la imagen oficial de Playwright. Deja `reports/junit.xml` y `reports/html/`. |
| `releaseHistory` | `releaseHistory('prod')` | Imprime el historial de versiones desplegadas en un ambiente (`/opt/circleguard/RELEASES`). |
| `notifyTeam` | `notifyTeam('FAILURE')` · `notifyTeam('APPROVAL', 'detalle')` | Avisa por Discord y WhatsApp. Estados: `SUCCESS`, `FAILURE`, `UNSTABLE`, `ABORTED`, `APPROVAL`. Un canal que falla no tumba el pipeline. |

Scripts que usan los pasos (`resources/scripts/`):

- **`deploy.sh`** corre **en la VM** del ambiente. Genera los secretos del ambiente la primera vez (viven solo en la VM, nunca en el repo ni en Jenkins), ejecuta `compose pull && up -d --wait` y anota cada despliegue en `RELEASES`. Es idempotente.
- **`notify.sh`** envía los avisos. Se puede probar a mano: `./notify.sh FAILURE "circleguard #12" "Falló Unit tests" "<url>"`.

## Credenciales y variables

Las define JCasC al arrancar Jenkins a partir de `infra/bootstrap/.state/secrets.env` (fuera del repo):

| ID de credencial | Uso |
|---|---|
| `github-token` | Checkout, estado de los PR, *tags* y *Releases* |
| `nexus-ci` | Publicar y descargar imágenes del registro |
| `sonar-token` | Análisis en SonarQube |
| `deploy-ssh` | Llave SSH para desplegar en dev, stage y prod |
| `discord-webhook`, `whatsapp-recipients` | Avisos del pipeline |

Variables globales del pipeline: `REGISTRY`, `DEV_HOST`, `STAGE_HOST`, `PROD_HOST`, `DEV_URL`, `STAGE_URL` y `PROD_URL`.

## Rollback

El job `circleguard-rollback` pide `ENV` (`dev`, `stage` o `prod`) y `VERSION` (sin la `v`, p. ej. `1.0.0`). Imprime el historial del ambiente, pide confirmación solo si es `prod` y vuelve a desplegar esa versión con `deployCompose(..., 'rollback')`, seguido de la prueba de humo. Las versiones disponibles están en [GitHub Releases](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/releases) y en el historial que imprime la primera etapa.

## Aprobación y concurrencia

- El `input` de prod lo pueden aprobar `jmrojas06` y `pierrecuevas`, con un plazo de 2 horas. El pipeline usa `agent none` para que esa espera no ocupe uno de los 2 ejecutores de Jenkins.
- El `Jenkinsfile` usa `disableConcurrentBuilds(abortPrevious: true)`: un build nuevo de una rama **aborta el anterior de esa misma rama**, incluido uno que espera la aprobación de prod. Mientras una versión espera aprobación no se debe fusionar nada más en `main`.

## Dónde ver los resultados

| Qué | Dónde |
|---|---|
| Pruebas unitarias e integración | Pestaña de resultados de pruebas del build (JUnit) |
| Cobertura | Informe «Cobertura JaCoCo» del build y el proyecto del servicio en SonarQube |
| Calidad | SonarQube, un proyecto `circleguard-<servicio>` por servicio, con el *quality gate* «CircleGuard» |
| E2E | Informe «Playwright E2E (dev)» del build |
| Versión y notas | *GitHub Release* `vX.Y.Z` y `CHANGELOG.md` como artefacto del build |
| Imágenes | Repositorio Docker `circleguard` de Nexus, con *tags* `X.Y.Z` y `sha-<commit>`. Su política es `allow_once`: un *tag* publicado no se puede sobrescribir |
