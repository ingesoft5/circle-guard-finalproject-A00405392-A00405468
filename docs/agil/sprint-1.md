# Sprint 1 — jueves 5 a domingo 8 de noviembre de 2026

> Borrador que se completa en la *review* y la retrospectiva del domingo 8 (14:00). Las secciones marcadas _(por completar)_ se llenan ese día con la evidencia real.

| | |
|---|---|
| **Objetivo** | *"Un commit en una rama llega a producción de forma automática, probada, versionada y con aprobación, sobre los 3 ambientes."* (Parte 1, entregables E1.1-E1.8) |
| **Equipo** | `jmrojas06` (A, plataforma) · `pierrecuevas` (B, aplicación y calidad) |
| **Capacidad / comprometido** | 2 personas × 4 días · **61 puntos** (14 historias) |
| **Tablero** | [GitHub Projects #8](https://github.com/orgs/ingesoft5/projects/8) · [milestone Sprint 1](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/milestone/2) |
| **Burndown** | [`metricas/sprint-1-burndown.md`](metricas/sprint-1-burndown.md) (foto diaria con `metricas/burndown.sh`) |
| **Planning** | [`sprint-0.md` §5](sprint-0.md#5-planning-del-sprint-1-jue-5--dom-8-nov) |

## 1. Sprint review: historias contra sus criterios

| Issue | Historia | Resp. | Pts | Estado al 7 nov | Evidencia |
|---|---|---|---|---|---|
| [#8](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/8) | HU-01 Nube con Bash idempotente | A | 5 | **Hecho** (PR #44, #45) | [`bootstrap-up-1.txt`](../evidencias/sprint-1/bootstrap-up-1.txt), [`bootstrap-up-2-idempotente.txt`](../evidencias/sprint-1/bootstrap-up-2-idempotente.txt) |
| [#9](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/9) | HU-02 Imágenes Docker inmutables | B | 5 | **Hecho** (PR #48); publicación en Nexus pendiente de la primera corrida de `main` | [`imagenes-y-compose-dev.md`](../evidencias/sprint-1/imagenes-y-compose-dev.md) |
| [#10](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/10) | HU-03 Compose por ambiente | B | 3 | **Hecho** (PR #48) | ídem |
| [#11](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/11) | HU-04 CI en Jenkins | A | 8 | **Hecho** (PR #46) | [`ci-pipeline-verde.txt`](../evidencias/sprint-1/ci-pipeline-verde.txt), [`ci-quality-gate-bloquea-pr.txt`](../evidencias/sprint-1/ci-quality-gate-bloquea-pr.txt) |
| [#12](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/12) | HU-05 Cobertura ≥ 60 % | B | 8 | En revisión (PR #49, aprobado; falta rebase) | `cobertura-servicios-nucleo.md` (en el PR) |
| [#13](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/13) | HU-06 3 pruebas de integración | B | 5 | En revisión (PR #50; falta rebase) | `pruebas-integracion.md` (en el PR) |
| [#14](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/14) | HU-07 E2E con Playwright | B | 5 | _(por completar)_ | |
| [#15](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/15) | HU-08 Promoción entre ambientes | A | 8 | En revisión (PR #51); corrida real pendiente | [`cd-validacion-local.md`](../evidencias/sprint-1/cd-validacion-local.md) |
| [#16](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/16) | HU-09 Semver y release notes | A | 3 | En revisión (PR #51) | ídem |
| [#17](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/17) | HU-10 Rollback | A | 2 | En revisión (PR #51); ejecución real pendiente | ídem |
| [#18](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/18) | HU-11 Change management | A+B | 2 | En revisión | [`docs/change-management/`](../change-management/politica.md) |
| [#19](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/19) | HU-12 Documentación e IAG | A+B | 3 | En curso | [`README.md`](../../README.md), [`docs/arquitectura/`](../arquitectura/README.md), [`IAG_REGISTRO.md`](../../IAG_REGISTRO.md) |
| [#20](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/20) | HU-13 Cierre del sprint | A+B | 2 | _(domingo)_ | este documento, video, informe |
| [#38](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/38) | HU-28 Burndown diario | A | 2 | En curso (foto diaria) | [`metricas/`](metricas/sprint-1-burndown.md) |

### Demostración de punta a punta _(por completar el domingo)_

Ensayo general: un `fix:` recorre PR → revisión → `main` → `vX.Y.Z` → dev → E2E → stage → `input` → prod, y después un rollback en prod. Capturas por etapa en `docs/evidencias/sprint-1/`.

## 2. Métricas

| Métrica | Valor |
|---|---|
| Puntos comprometidos / terminados | 61 / _(por completar)_ |
| Velocidad del sprint | _(por completar)_ puntos en 4 días |
| Pruebas | 51 → 110 unitarias (+ 11 de integración con Testcontainers) |
| Cobertura de líneas (núcleo) | auth 89.6 % · identity 84.4 % · form 88.6 % · promotion 68.4 % · gateway 81.0 % |
| PR fusionados en el sprint | #43, #44, #45, #46, #48 _(+ los del domingo)_ |

## 3. Retrospectiva _(por completar el domingo)_

| Salió bien | Por mejorar | Acción para el Sprint 2 |
|---|---|---|
| Las pruebas de integración encontraron 3 errores reales del código base que las unitarias no veían | Los PR apilados (#44→#45→#46, #48→#50) se marcan en conflicto al hacer *squash* y descartan aprobaciones: cada uno costó un rebase extra | Un PR a la vez contra `main`; el siguiente se abre cuando el anterior se fusiona |
| El pipeline de CI quedó en verde el mismo día y bloqueó la demo de #47 | Las VMs estuvieron apagadas la mayor parte del sábado: la CD se validó en local y la corrida real quedó para el domingo | Encender `tools` + `dev` al empezar cada bloque de trabajo de pipeline y apagar al terminar |
| _(jmrojas06)_ | _(jmrojas06)_ | _(jmrojas06)_ |
| _(pierrecuevas)_ | _(pierrecuevas)_ | _(pierrecuevas)_ |

## 4. Entregables de la Parte 1 (índice para el informe)

| Entregable | Dónde |
|---|---|
| E1.1 Gestión ágil | este documento, [`sprint-0.md`](sprint-0.md), [tablero](https://github.com/orgs/ingesoft5/projects/8), [`metricas/`](metricas/sprint-1-burndown.md), [`CONTRIBUTING.md`](../../CONTRIBUTING.md) |
| E1.2 Nube y Bash | [`infra/bootstrap/`](../../infra/bootstrap/README.md), [`docs/evidencias/sprint-1/bootstrap-*`](../evidencias/sprint-1) |
| E1.3 Empaquetamiento | `services/*/Dockerfile`, `mobile/Dockerfile`, [`deploy/compose/`](../../deploy/compose/README.md) |
| E1.4 CI | [`Jenkinsfile`](../../Jenkinsfile), [`jenkins/vars/`](../../jenkins/vars), SonarQube |
| E1.5 CD | etapas de `main` del `Jenkinsfile`, [`docs/release-notes/`](../release-notes/README.md), job de rollback |
| E1.6 Pruebas | cobertura, `integrationTest`, `tests/e2e-playwright/` |
| E1.7 Change management | [`docs/change-management/`](../change-management/politica.md), GitHub Releases |
| E1.8 Documentación e IAG | [`README.md`](../../README.md), [`docs/arquitectura/`](../arquitectura/README.md), [`IAG_REGISTRO.md`](../../IAG_REGISTRO.md) |
