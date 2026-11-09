# Matriz de la rúbrica de la Parte 1 contra la evidencia — verificada el 8 de noviembre de 2026

Cada fila toma un criterio de la rúbrica (P1.1 a P1.8), lo que pide la columna «Excelente» y dónde está la evidencia en este repositorio o en GitHub. Las comprobaciones se hicieron el 8 de noviembre a las 18:50 (hora de Colombia) consultando la API de GitHub (`gh api`), las URL públicas de los ambientes y el contenido de `main`; lo que todavía no está hecho figura como **pendiente** en vez de darse por cumplido.

| Estado | Significado |
|---|---|
| ✅ | Verificado el 8 nov con la fuente indicada |
| 🟡 | Hecho, pero falta cerrar un detalle (se indica cuál) |
| ⏳ | Pendiente |

## P1.1 Gestión ágil y *branching* (2.0 %) · E1.1

| Pide «Excelente» | Evidencia | Estado |
|---|---|---|
| Tablero vivo con historias y criterios medibles | [GitHub Projects #8](https://github.com/orgs/ingesoft5/projects/8): 43 elementos con Status, Sprint, Story Points y Entregable. Los 36 issues del backlog tienen **criterios de aceptación y responsable** (comprobado por API) | ✅ |
| Sprint 0 y 1 con *review* y retrospectiva | [`sprint-0.md`](../../agil/sprint-0.md) §6 y [`sprint-1.md`](../../agil/sprint-1.md) §1 y §3 | 🟡 la fila de cada integrante en la retro la completa cada uno |
| Ramas protegidas | `main`: PR obligatorio, 1 aprobación, se descarta si llegan commits nuevos, el autor del último *push* no aprueba, historial lineal, solo *squash*, sin *force push* ni borrado, también para administradores (`gh api repos/…/branches/main/protection`). Copia en [`proteccion-main.json`](../sprint-0/proteccion-main.json) | ✅ |
| PR revisados por pares | 17 PR fusionados, cada uno aprobado por el integrante que no es el autor, con etiqueta de responsable y de entregable; el detalle de cada revisión está en [`IAG_REGISTRO.md`](../../../IAG_REGISTRO.md) | ✅ |
| Estrategia documentada | [`CONTRIBUTING.md`](../../../CONTRIBUTING.md) (GitHub Flow, Conventional Commits, DoD) | ✅ |
| Burndown | [`metricas/sprint-1-burndown.md`](../../agil/metricas/sprint-1-burndown.md) con una foto por día | 🟡 falta la foto de cierre |

## P1.2 Nube y automatización con Bash (2.0 %) · E1.2

| Pide «Excelente» | Evidencia | Estado |
|---|---|---|
| Tres ambientes aislados | 4 VMs EC2 en ejecución (`tools`, `dev`, `stage`, `prod`), cada una con su *security group* y su IP elástica; `/healthz` responde 200 en las tres URL de ambiente el 8 nov | ✅ |
| *Scripts* idempotentes que reconstruyen Jenkins, SonarQube y el registro con un comando | [`infra/bootstrap/up.sh`](../../../infra/bootstrap/README.md); dos ejecuciones seguidas en [`bootstrap-up-1.txt`](bootstrap-up-1.txt) y [`bootstrap-up-2-idempotente.txt`](bootstrap-up-2-idempotente.txt); reconstrucción del 8 nov en [`ensayo-pipeline-real.md`](ensayo-pipeline-real.md) | ✅ |
| Configuración de Jenkins como código | [`infra/tools/jenkins/casc/jenkins.yaml`](../../../infra/tools/jenkins/casc/jenkins.yaml) (JCasC + Job DSL) | ✅ |
| Webhook de GitHub | Activo hacia `http://44.221.115.232:8080/github-webhook/` con eventos `push` y `pull_request`; entregas con HTTP 200 ([`ensayo-pipeline-real.md`](ensayo-pipeline-real.md) §1 y §5) | ✅ |

## P1.3 Empaquetamiento inmutable (2.5 %) · E1.3

| Pide «Excelente» | Evidencia | Estado |
|---|---|---|
| Imágenes *multi-stage*, no root, versionadas y publicadas para los 8 servicios y el cliente | 9 `Dockerfile` (`services/*/Dockerfile`, `mobile/Dockerfile`); las 9 imágenes en Nexus con tags `1.0.0`/`1.1.0` y `sha-<commit>`, sin `latest` ([`ensayo-pipeline-real.md`](ensayo-pipeline-real.md) §3); tabla de tamaños en [`imagenes-y-compose-dev.md`](imagenes-y-compose-dev.md) | ✅ |
| *Compose* con *healthchecks* | [`deploy/compose/`](../../../deploy/compose/README.md); 10/10 contenedores `healthy` en dev, stage y prod ([`ensayo-pipeline-real.md`](ensayo-pipeline-real.md) §4) | ✅ |

## P1.4 Pipeline CI en Jenkins (2.5 %) · E1.4

| Pide «Excelente» | Evidencia | Estado |
|---|---|---|
| *Multibranch* con *webhook* y *Shared Library* | [`Jenkinsfile`](../../../Jenkinsfile), [`jenkins/vars/`](../../../jenkins/vars), referencia en [`jenkins/README.md`](../../../jenkins/README.md) | ✅ |
| JaCoCo y *quality gate* de SonarQube que bloquea | CI completa del PR #56: 121 pruebas, SonarQube con *quality gate* «CircleGuard» OK en los 8 proyectos ([`ensayo-pipeline-real.md`](ensayo-pipeline-real.md) §2); bloqueo demostrado con el PR #47 ([`ci-quality-gate-bloquea-pr.txt`](ci-quality-gate-bloquea-pr.txt)) | ✅ |
| Ejecución selectiva por servicio | `changedServices()`; el PR #47 construyó solo `gateway`; un cambio en `jenkins/` construye los 9 | ✅ |
| Estado visible en el PR | Jenkins publica `continuous-integration/jenkins/pr-merge` en cada PR (comprobado en #56 y #58) | ✅ |
| Check de Jenkins como requisito para fusionar (`CONTRIBUTING.md` §4) | Desde el 8 de nov por la noche la protección de `main` exige `continuous-integration/jenkins/pr-merge` (`gh api repos/…/branches/main/protection`); el PR #60 fue el primero que lo pasó | ✅ |

## P1.5 Entrega continua y promoción (2.5 %) · E1.5

| Pide «Excelente» | Evidencia | Estado |
|---|---|---|
| Despliegue automático a dev, E2E, stage | Builds `main` #2 (`v1.0.0`) y #3 (`v1.1.0`, por *webhook*) ([`ensayo-pipeline-real.md`](ensayo-pipeline-real.md) §1 y §5) | ✅ |
| Aprobación manual a prod | `v1.0.0` pasó por el `input` y se desplegó en prod (aprobado por `pierrecuevas`); `v1.1.0` también (main #3, aprobado por `jmrojas06`, [`ensayo-pipeline-real.md`](ensayo-pipeline-real.md) §7) | ✅ |
| Versionado semántico automático | Tags [`v1.0.0`](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/releases/tag/v1.0.0) y [`v1.1.0`](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/releases/tag/v1.1.0) creados por semantic-release | ✅ |
| Notificaciones de fallo | Fallo provocado en `circleguard-rollback` #1 con aviso a WhatsApp y Discord ([`ensayo-pipeline-real.md`](ensayo-pipeline-real.md) §6) | ✅ |
| *Rollback* demostrado | Job `circleguard-rollback` creado por JCasC: fallo controlado (#1) y **rollback exitoso de prod a `v1.0.0` (#2, 3 min 48 s, confirmación manual, 10 contenedores `healthy`)**; [`ensayo-pipeline-real.md`](ensayo-pipeline-real.md) §7 | ✅ |

## P1.6 Pruebas automatizadas (3.5 %) · E1.6

| Pide «Excelente» | Evidencia | Estado |
|---|---|---|
| Cobertura de líneas ≥ 60 % en los servicios núcleo | SonarQube: auth 88,6 %, identity 84,6 %, form 88,6 %, promotion 70,4 %, gateway 81,8 %; el *build* lo exige con `jacocoTestCoverageVerification` ([`cobertura-servicios-nucleo.md`](cobertura-servicios-nucleo.md)) | ✅ |
| ≥ 3 integraciones relevantes | 3 flujos con Testcontainers, 11 casos, ejecutados por el pipeline ([`pruebas-integracion.md`](pruebas-integracion.md)) | ✅ |
| 3 flujos E2E con Playwright | Login, cuestionario y validación de acceso: 5/5 en el build #2 y 6/6 en el #3 ([`pruebas-e2e-playwright.md`](pruebas-e2e-playwright.md)) | ✅ |
| Todo ejecutado y reportado por el pipeline | Etapas «Unit tests + JaCoCo», «Integration tests» y «E2E contra dev» del `Jenkinsfile`; reportes JUnit, JaCoCo y Playwright en el build | ✅ |

## P1.7 Change management y release notes (1.5 %) · E1.7

| Pide «Excelente» | Evidencia | Estado |
|---|---|---|
| Plantilla y política de aprobación por tipo | [`politica.md`](../../change-management/politica.md), [`solicitud-de-cambio.md`](../../change-management/solicitud-de-cambio.md), plantilla de issue `solicitud-cambio.md` | ✅ |
| *Release notes* automáticas y etiquetado coherente | Las dos Releases con notas agrupadas por tipo y `CHANGELOG.md` adjunto; tags `vX.Y.Z` según Conventional Commits ([`release-notes/`](../../release-notes/README.md)) | ✅ |
| Uso real del proceso | [RFC #52](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/52): primer despliegue a producción | 🟡 falta enlazarle el release |

## P1.8 Documentación, video e IAG (1.0 %) · E1.8

| Pide «Excelente» | Evidencia | Estado |
|---|---|---|
| `README` que permite reproducir | [`README.md`](../../../README.md) (sección «Operación»), [`infra/bootstrap/README.md`](../../../infra/bootstrap/README.md), [`deploy/compose/README.md`](../../../deploy/compose/README.md), [`jenkins/README.md`](../../../jenkins/README.md) | ✅ |
| Diagramas claros | [`docs/arquitectura/README.md`](../../arquitectura/README.md): servicios y eventos, ambientes en AWS y pipeline | ✅ |
| Video | [`video-pipeline.md`](video-pipeline.md): 3 min 47 s, adjunto al release `v1.1.0` | ✅ |
| Bitácora de IAG con reflexión crítica | [`IAG_REGISTRO.md`](../../../IAG_REGISTRO.md): una entrada por PR, revisiones y reflexiones individuales | 🟡 falta la reflexión de `jmrojas06` |
| Informe PDF con enlaces y evidencias | [`docs/entrega/informe-parte1-pipeline-cicd.pdf`](../../entrega/informe-parte1-pipeline-cicd.pdf), con enlaces y capturas | ✅ |

## Pendientes al cierre de esta verificación

1. ~~Rollback de prod a `v1.0.0`~~ y ~~check obligatorio de `main`~~: hechos el 8 de nov.
3. Fila de cada integrante en las retros, reflexión de `jmrojas06` y foto de cierre del burndown (P1.1, P1.8).
4. ~~Informe PDF~~: entregado en `docs/entrega/`.
