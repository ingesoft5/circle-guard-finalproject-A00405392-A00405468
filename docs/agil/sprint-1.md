# Sprint 1 — jueves 5 a domingo 8 de noviembre de 2026

> Cierre del Sprint 1: *sprint review* y retrospectiva del domingo 8 de noviembre. Cada historia enlaza su evidencia real en [`docs/evidencias/sprint-1/`](../evidencias/sprint-1/).

| | |
|---|---|
| **Objetivo** | *"Un commit en una rama llega a producción de forma automática, probada, versionada y con aprobación, sobre los 3 ambientes."* (Parte 1, entregables E1.1-E1.8) |
| **Equipo** | `jmrojas06` (A, plataforma) · `pierrecuevas` (B, aplicación y calidad) |
| **Capacidad / comprometido** | 2 personas × 4 días · **61 puntos** (14 historias) |
| **Tablero** | [GitHub Projects #8](https://github.com/orgs/ingesoft5/projects/8) · [milestone Sprint 1](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/milestone/2) |
| **Burndown** | [`metricas/sprint-1-burndown.md`](metricas/sprint-1-burndown.md) (foto diaria con `metricas/burndown.sh`) |
| **Planning** | [`sprint-0.md` §5](sprint-0.md#5-planning-del-sprint-1-jue-5--dom-8-nov) |
| **Releases del sprint** | [`v1.0.0`](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/releases/tag/v1.0.0) y [`v1.1.0`](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/releases/tag/v1.1.0), publicadas por el pipeline |

## 1. Sprint review: historias contra sus criterios

| Issue | Historia | Resp. | Pts | Estado al 8 nov | Evidencia |
|---|---|---|---|---|---|
| [#8](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/8) | HU-01 Nube con Bash idempotente | A | 5 | **Hecho** (PR #44, #45). El 8 nov `up.sh` reconstruyó las 4 VMs y Jenkins, SonarQube y Nexus en 3,5 min | [`bootstrap-up-1.txt`](../evidencias/sprint-1/bootstrap-up-1.txt), [`bootstrap-up-2-idempotente.txt`](../evidencias/sprint-1/bootstrap-up-2-idempotente.txt), [`ensayo-pipeline-real.md`](../evidencias/sprint-1/ensayo-pipeline-real.md) |
| [#9](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/9) | HU-02 Imágenes Docker inmutables | B | 5 | **Hecho** (PR #48). Las 9 imágenes están en Nexus con `1.0.0` y `sha-f2b7707` (política `allow_once`) | [`imagenes-y-compose-dev.md`](../evidencias/sprint-1/imagenes-y-compose-dev.md), [`ensayo-pipeline-real.md` §3](../evidencias/sprint-1/ensayo-pipeline-real.md) |
| [#10](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/10) | HU-03 Compose por ambiente | B | 3 | **Hecho** (PR #48). dev, stage y prod con 10/10 contenedores `healthy` | [`ensayo-pipeline-real.md` §4](../evidencias/sprint-1/ensayo-pipeline-real.md) |
| [#11](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/11) | HU-04 CI en Jenkins | A | 8 | **Hecho** (PR #46). La CI completa corrió sobre el código final en el PR #56: 121 pruebas, SonarQube con *quality gate* y pruebas de integración, en 9,5 min | [`ci-pipeline-verde.txt`](../evidencias/sprint-1/ci-pipeline-verde.txt), [`ci-quality-gate-bloquea-pr.txt`](../evidencias/sprint-1/ci-quality-gate-bloquea-pr.txt), [`ensayo-pipeline-real.md` §2](../evidencias/sprint-1/ensayo-pipeline-real.md) |
| [#12](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/12) | HU-05 Cobertura ≥ 60 % | B | 8 | **Hecho** (PR #49). Cobertura de líneas en SonarQube: auth 88,6 %, identity 84,6 %, form 88,6 %, promotion 70,4 %, gateway 81,8 % | [`cobertura-servicios-nucleo.md`](../evidencias/sprint-1/cobertura-servicios-nucleo.md), [`ensayo-pipeline-real.md` §2](../evidencias/sprint-1/ensayo-pipeline-real.md) |
| [#13](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/13) | HU-06 3 pruebas de integración | B | 5 | **Hecho** (PR #54). 11 casos con Testcontainers que encontraron 3 errores reales del código base | [`pruebas-integracion.md`](../evidencias/sprint-1/pruebas-integracion.md) |
| [#14](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/14) | HU-07 E2E con Playwright | B | 5 | **Hecho** (PR #55, #57). Los E2E corren dentro del pipeline contra dev y frenan la promoción: 5/5 en el build #2 y 6/6 con la prueba de versión | [`pruebas-e2e-playwright.md`](../evidencias/sprint-1/pruebas-e2e-playwright.md), [`ensayo-pipeline-real.md` §1](../evidencias/sprint-1/ensayo-pipeline-real.md) |
| [#15](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/15) | HU-08 Promoción entre ambientes | A | 8 | **Hecho** (PR #51). dev → E2E → stage → aprobación manual → prod demostrado en los builds #2 y #3 | [`cd-validacion-local.md`](../evidencias/sprint-1/cd-validacion-local.md), [`ensayo-pipeline-real.md`](../evidencias/sprint-1/ensayo-pipeline-real.md) |
| [#16](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/16) | HU-09 Semver y release notes | A | 3 | **Hecho** (PR #51). `v1.0.0` y `v1.1.0` salieron solas, con tag, *GitHub Release*, notas por tipo y `CHANGELOG.md`; cada PR recibe un comentario con su versión | [`ensayo-pipeline-real.md`](../evidencias/sprint-1/ensayo-pipeline-real.md), [`docs/release-notes/`](../release-notes/README.md) |
| [#17](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/17) | HU-10 Rollback | A | 2 | **Hecho.** `circleguard-rollback` #2 devolvió prod de `1.1.0` a `1.0.0` en 3 min 48 s, tras confirmación manual; los 10 contenedores quedaron `healthy` | [`ensayo-pipeline-real.md`](../evidencias/sprint-1/ensayo-pipeline-real.md) |
| [#18](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/18) | HU-11 Change management | A+B | 2 | **Hecho** (PR #53). Política, guía de solicitud de cambio y el [RFC #52](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/52) del primer despliegue a prod _(al cierre: aprobado y enlazado al release)_ | [`docs/change-management/`](../change-management/politica.md) |
| [#19](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/19) | HU-12 Documentación e IAG | A+B | 3 | **Hecho** (PR #53, #56). README operativo, diagramas, referencia del pipeline y bitácora de IAG con revisiones y reflexión | [`README.md`](../../README.md), [`docs/arquitectura/`](../arquitectura/README.md), [`jenkins/README.md`](../../jenkins/README.md), [`IAG_REGISTRO.md`](../../IAG_REGISTRO.md) |
| [#20](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/20) | HU-13 Cierre del sprint | A+B | 2 | El video del pipeline (3 min 47 s) está grabado y subido; _(al cierre: informe)_ | este documento, [video](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/releases/download/v1.1.0/circleguard-pipeline-sprint1.mp4) y [su guion](../evidencias/sprint-1/video-pipeline.md) |
| [#38](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/38) | HU-28 Burndown diario | A | 2 | **Hecho** (foto diaria del 5 al 8 nov) | [`metricas/`](metricas/sprint-1-burndown.md) |

### Demostración de punta a punta

El domingo 8 el pipeline corrió de punta a punta en AWS, con las 4 VMs encendidas. Las cifras salen de las APIs de Jenkins, SonarQube, Nexus y GitHub; el detalle está en [`ensayo-pipeline-real.md`](../evidencias/sprint-1/ensayo-pipeline-real.md).

1. **`v1.0.0`:** `main` → semantic-release → 9 imágenes en Nexus → dev → E2E → stage → aprobación manual → prod. Build #2: 81,6 min, de los cuales 58,3 fueron la espera de aprobación; el trabajo real fue de unos 23 min.
2. **CI completa sobre el código final:** el PR #56 ejecutó build, 121 pruebas, SonarQube con *quality gate* e integración sobre los 9 servicios en 9,5 min, y GitHub recibió el check `continuous-integration/jenkins/pr-merge`.
3. **`v1.1.0`:** un `feat` (PR #57) arrancó el pipeline solo, por *webhook*, y llegó a la pausa de aprobación de prod en 9 min.
4. **Aviso de fallo:** un rollback de dev a una versión inexistente falló como se esperaba y el pipeline avisó por WhatsApp y Discord sin afectar al ambiente.
5. **Rollback en prod:** `circleguard-rollback` #2 (`ENV=prod`, `VERSION=1.0.0`) pidió confirmación («¿Devolver PROD a v1.0.0?»), la aprobó `jmrojas06` y prod volvió a `1.0.0` con 10 contenedores `healthy`; `RELEASES` registra la línea `rollback`.

## 2. Métricas

| Métrica | Valor |
|---|---|
| Puntos comprometidos / terminados | 61 / _(al cierre)_ |
| Velocidad del sprint | _(al cierre)_ puntos en 4 días |
| Pruebas | 51 → 121 (110 unitarias + 11 de integración con Testcontainers) + 6 E2E de Playwright |
| Cobertura de líneas (núcleo, SonarQube) | auth 88,6 % · identity 84,6 % · form 88,6 % · promotion 70,4 % · gateway 81,8 % |
| PR fusionados en el sprint | #43, #44, #45, #46, #48, #49, #51, #53, #54, #55, #57 |
| Versiones publicadas | `v1.0.0`, `v1.1.0` |
| Tiempo del pipeline | CI de un PR: 9,5 min · de `main` hasta la pausa de prod: 9 a 23 min según lo que cambia |

## 3. Retrospectiva

| Salió bien | Por mejorar | Acción para el Sprint 2 |
|---|---|---|
| Las pruebas de integración encontraron 3 errores reales del código base que las unitarias no veían | Los PR apilados (#44→#45→#46, #48→#50) se marcan en conflicto al hacer *squash* y descartan aprobaciones: cada uno costó un rebase extra | Un PR a la vez contra `main`; el siguiente se abre cuando el anterior se fusiona |
| El pipeline de CI quedó en verde el mismo día y bloqueó la demo de #47 | Las VMs estuvieron apagadas la mayor parte del sábado: la CD se validó en local y la corrida real quedó para el domingo | Encender `tools` + `dev` al empezar cada bloque de trabajo de pipeline y apagar al terminar |
| Todo está como código: `up.sh` reconstruyó la plataforma en 3,5 min y la primera corrida real de `main` salió completa (`v1.0.0` en unos 23 min de trabajo), porque cada pieza se había validado antes en local | `up.sh` construye Jenkins con los archivos de la copia local: una copia atrasada dejó fuera el job de rollback y se descubrió con las VMs ya encendidas. Además, mientras Jenkins estuvo apagado se fusionaron 6 PR sin pasar por la CI (#48, #49, #51, #53, #54 y #55), y la CI completa sobre el código final solo se ejecutó al final | `git pull` antes de `up.sh` (y un aviso en el script si la copia está atrasada); activar el check obligatorio de Jenkins en `main` para que ningún PR entre sin pasar por la CI |
| Las pruebas de integración y E2E cumplieron su objetivo y detectaron a tiempo fallos críticos: el gateway dejaba pasar a usuarios contagiados, el cuestionario perdía las respuestas y el login fallaba directamente en el navegador | Probé demasiado tiempo solo con `curl` en lugar de validar en un navegador real, por eso el tema de CORS apareció al final. Además, encadenar un PR sobre otro (#48 → #50) hizo que GitHub cerrara el segundo y hubo que rehacer el trabajo | Correr la suite E2E contra el docker-compose local antes de abrir cualquier PR que toque el frontend, y mantener la regla de abrir cada PR solo y directo contra `main` |

## 4. Entregables de la Parte 1 (índice para el informe)

| Entregable | Dónde |
|---|---|
| E1.1 Gestión ágil | este documento, [`sprint-0.md`](sprint-0.md), [tablero](https://github.com/orgs/ingesoft5/projects/8), [`metricas/`](metricas/sprint-1-burndown.md), [`CONTRIBUTING.md`](../../CONTRIBUTING.md) |
| E1.2 Nube y Bash | [`infra/bootstrap/`](../../infra/bootstrap/README.md), [`docs/evidencias/sprint-1/bootstrap-*`](../evidencias/sprint-1), [`ensayo-pipeline-real.md`](../evidencias/sprint-1/ensayo-pipeline-real.md) |
| E1.3 Empaquetamiento | `services/*/Dockerfile`, `mobile/Dockerfile`, [`deploy/compose/`](../../deploy/compose/README.md), [`ensayo-pipeline-real.md` §3 y §4](../evidencias/sprint-1/ensayo-pipeline-real.md) |
| E1.4 CI | [`Jenkinsfile`](../../Jenkinsfile), [`jenkins/`](../../jenkins/README.md), SonarQube, [`ensayo-pipeline-real.md` §2](../evidencias/sprint-1/ensayo-pipeline-real.md) |
| E1.5 CD | etapas de `main` del `Jenkinsfile`, [`docs/release-notes/`](../release-notes/README.md), job de rollback, [`ensayo-pipeline-real.md`](../evidencias/sprint-1/ensayo-pipeline-real.md) |
| E1.6 Pruebas | cobertura, `integrationTest`, `tests/e2e-playwright/`, [`ensayo-pipeline-real.md`](../evidencias/sprint-1/ensayo-pipeline-real.md) |
| E1.7 Change management | [`docs/change-management/`](../change-management/politica.md), GitHub Releases, [RFC #52](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/52) |
| E1.8 Documentación, video e IAG | [`README.md`](../../README.md), [`docs/arquitectura/`](../arquitectura/README.md), [`IAG_REGISTRO.md`](../../IAG_REGISTRO.md), [video y guion](../evidencias/sprint-1/video-pipeline.md) |
