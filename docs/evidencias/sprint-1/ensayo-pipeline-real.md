# HU-08 · HU-09 · HU-10 · Ensayo real del pipeline de punta a punta — 2026-11-08

Primera corrida real de `main` en AWS con las 4 VMs encendidas: del commit a producción, con versionado automático, imágenes en Nexus, E2E dentro del pipeline y aprobación manual. Todas las cifras salen de las APIs de Jenkins, SonarQube, Nexus y GitHub y de las propias VMs, consultadas después de la corrida.

## 1. Release `v1.0.0` (build `main` #2)

[Build #2](http://44.221.115.232:8080/job/circleguard/job/main/2/) · commit `f2b7707` · resultado **SUCCESS** · 81,6 min, de los cuales 58,3 fueron la espera de aprobación para prod (el trabajo real fue de unos 23 min).

| Etapa | Resultado | Min |
|---|---|---|
| Commitlint | SUCCESS | 0,2 |
| Release (semantic-release): tag `v1.0.0` y GitHub Release | SUCCESS | 2,4 |
| Package + Publish (9 imágenes) | SUCCESS | 8,3 |
| Deploy dev + prueba de humo | SUCCESS | 3,5 |
| E2E contra dev (Playwright, 5 pruebas, 0 fallas) | SUCCESS | 1,1 |
| Deploy stage + prueba de humo + aviso de aprobación | SUCCESS | 3,8 |
| Aprobación para prod (`input`, aprobado por `pierrecuevas`) | SUCCESS | 58,3 |
| Deploy prod + prueba de humo | SUCCESS | 3,8 |

Las etapas Build, Unit tests, SonarQube e Integration tests salieron *NOT_EXECUTED* en este build: en `main` la ejecución selectiva compara con el commit anterior (#53, solo documentos) y no encontró servicios cambiados. La CI completa sobre el código final se ejecutó en el PR #56 (sección 2).

- Tag y [GitHub Release `v1.0.0`](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/releases/tag/v1.0.0) con las notas agrupadas por tipo (Funcionalidades y Correcciones) y `CHANGELOG.md` como artefacto del build.
- Webhook de GitHub a Jenkins: entregas `push`, `pull_request` (`opened`, `review_requested`) con **HTTP 200**.

## 2. CI completa sobre el código final (PR #56, build `PR-56` #1)

[Build PR-56 #1](http://44.221.115.232:8080/job/circleguard/job/PR-56/1/) · **SUCCESS** en 9,5 min. El PR toca `jenkins/`, así que `changedServices` construyó los 9 servicios. GitHub recibió el check obligatorio `continuous-integration/jenkins/pr-merge: SUCCESS`.

| Etapa | Resultado | Min |
|---|---|---|
| Commitlint | SUCCESS | 0,2 |
| Build | SUCCESS | 1,0 |
| Unit tests + JaCoCo | SUCCESS | 3,6 |
| SonarQube + Quality Gate | SUCCESS | 2,4 |
| Integration tests | SUCCESS | 2,2 |

Pruebas JUnit del build: **121, 0 fallas, 0 omitidas** (110 unitarias y 11 de integración).

### SonarQube (quality gate «CircleGuard»: OK en los 8 proyectos)

| Servicio | Cobertura de **líneas** | Cobertura de ramas | «Coverage» de Sonar (líneas y ramas) |
|---|---|---|---|
| auth | **88,6 %** | 13,8 % | 56,7 % |
| identity | **84,6 %** | 6,6 % | 43,4 % |
| form | **88,6 %** | 11,2 % | 40,3 % |
| promotion | **70,4 %** | 10,5 % | 41,1 % |
| gateway | **81,8 %** | 75,0 % | 80,8 % |
| notification | 58,2 % | 47,6 % | 56,3 % |
| file | 21,1 % | — | 21,1 % |
| dashboard | 5,2 % | 0,0 % | 3,5 % |

La meta del enunciado es cobertura de **líneas** ≥ 60 % en los 5 servicios núcleo (auth, identity, promotion, form, gateway): se cumple en todos y además el build la exige con `jacocoTestCoverageVerification` (mínimo 0,60 de líneas). El indicador «Coverage» que muestra el panel de SonarQube combina líneas y ramas, y por eso se ve más bajo en los servicios con pocas pruebas de ramas.

## 3. Imágenes inmutables en Nexus

Repositorio Docker `circleguard` (política `allow_once`: un tag publicado no se sobrescribe). 9 imágenes, cada una con dos tags, 18 componentes:

| Imagen | Tags |
|---|---|
| `circleguard/circleguard-auth-service` | `1.0.0`, `sha-f2b7707` |
| `circleguard/circleguard-dashboard-service` | `1.0.0`, `sha-f2b7707` |
| `circleguard/circleguard-file-service` | `1.0.0`, `sha-f2b7707` |
| `circleguard/circleguard-form-service` | `1.0.0`, `sha-f2b7707` |
| `circleguard/circleguard-gateway-service` | `1.0.0`, `sha-f2b7707` |
| `circleguard/circleguard-identity-service` | `1.0.0`, `sha-f2b7707` |
| `circleguard/circleguard-notification-service` | `1.0.0`, `sha-f2b7707` |
| `circleguard/circleguard-promotion-service` | `1.0.0`, `sha-f2b7707` |
| `circleguard/circleguard-web` | `1.0.0`, `sha-f2b7707` |

## 4. Los tres ambientes

Cada ambiente corre en su propia VM, con 10 contenedores (5 servicios núcleo, el web y el middleware), **todos `healthy`**, y la misma versión `1.0.0`:

| Ambiente | URL | Desplegado (UTC) | Contenedores | Historial `/opt/circleguard/RELEASES` |
|---|---|---|---|---|
| dev | http://34.230.234.121 | 20:55:40 | 10/10 healthy | `1.0.0 deploy f2b7707` · build #2 |
| stage | http://3.209.207.195 | 21:00:27 | 10/10 healthy | `1.0.0 deploy f2b7707` · build #2 |
| prod | http://50.19.116.114 | 22:02:35 | 10/10 healthy | `1.0.0 deploy f2b7707` · build #2 |

## 5. Segundo release `v1.1.0` (build `main` #3, disparado por *webhook*)

El PR #57 (`feat(web): expone la version desplegada en /version`, aprobado por `pierrecuevas`) se fusionó a las 17:22 (hora local). GitHub entregó el `push` a Jenkins con HTTP 200 y el [build #3](http://44.221.115.232:8080/job/circleguard/job/main/3/) arrancó solo: causa «Push event to branch main». Del *push* a la pausa de aprobación pasaron 9 minutos (17:22 → 17:31).

| Etapa | Resultado | Min |
|---|---|---|
| Commitlint | SUCCESS | 0,2 |
| Build y pruebas unitarias (el cambio solo afectó al web: sin servicios Java) | SUCCESS | 0,0 |
| Release (semantic-release): tag `v1.1.0` (versión MINOR por el `feat`) | SUCCESS | 0,4 |
| Package + Publish (9 imágenes) | SUCCESS | 2,0 |
| Deploy dev + prueba de humo | SUCCESS | 2,3 |
| E2E contra dev (6 pruebas, 0 fallas, incluida `version.spec.ts`) | SUCCESS | 0,4 |
| Deploy stage + prueba de humo + aviso de aprobación | SUCCESS | 2,4 |
| Aprobación para prod | ver §7 | |

[GitHub Release `v1.1.0`](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/releases/tag/v1.1.0): sección «Funcionalidades: **web:** expone la version desplegada en /version (#57)». semantic-release además comenta en cada PR incluido la versión en la que salió.

Versión que mostraba cada ambiente mientras esperaba la aprobación de prod (`curl http://<ip>/version`):

| Ambiente | Respuesta de `/version` | Versión |
|---|---|---|
| dev | `1.1.0` (`text/plain`) | 1.1.0 |
| stage | `1.1.0` (`text/plain`) | 1.1.0 |
| prod | HTML de la aplicación (la imagen `1.0.0` no tiene la ruta) | 1.0.0 |

El historial `/opt/circleguard/RELEASES` de dev quedó con las dos versiones: `1.0.0` (build #2, commit `f2b7707`) y `1.1.0` (build #3, commit `34303d1`).

## 6. Aviso de fallo de despliegue (provocado a propósito)

Para comprobar las notificaciones de fallo se ejecutó el job [`circleguard-rollback` #1](http://44.221.115.232:8080/job/circleguard-rollback/1/) sobre **dev** con una versión que no existe (`VERSION=9.9.9`). Resultado: **FAILURE** en 10 s.

| Etapa | Resultado |
|---|---|
| Historial del ambiente (`releaseHistory`: imprime `RELEASES` de dev) | SUCCESS |
| Confirmar (solo prod) | no aplica |
| Rollback (`deployCompose` + prueba de humo) | **FAILED**: `failed to resolve reference …/circleguard-gateway-service:9.9.9: not found` |
| Post Actions (`notifyTeam('FAILURE')`) | SUCCESS: `[notify] WhatsApp enviado` y `[notify] Discord enviado` |

Dev no se vio afectado: `deploy.sh` hace el `pull` antes de tocar los contenedores, así que siguieron los 10 `healthy` con las imágenes `1.0.0`, `RELEASES` no registró nada y `/healthz` y el *readiness* respondieron 200. El único rastro fue el `IMAGE_TAG` del `.env` de dev (`9.9.9`), que se devolvió a `1.0.0` a mano; el siguiente despliegue lo habría corregido igual.

## 7. Promoción de `v1.1.0` a prod y rollback

_(al cierre: aprobación de prod, versión que responde `/version` en prod, y rollback de prod a `1.0.0` con `circleguard-rollback`, con el antes y el después en `RELEASES` y en los tags de las imágenes)_

## 8. Incidencias de la corrida (y qué se aprendió)

| Qué pasó | Causa | Qué se hizo |
|---|---|---|
| El primer build de `main` (#1) falló a los 17 s | Ocurrió durante el arranque de Jenkins, mientras `up.sh` reaplicaba su configuración | Se disparó el #2 con *Build Now*; corrió limpio |
| El job `circleguard-rollback` no aparecía | `up.sh` construye Jenkins con los archivos de la copia local y esa copia estaba atrasada respecto a `main` | `git pull`, segundo `up.sh tools` y *Reload Configuration from Disk* |
| Las entregas del webhook de los *merge* de #49 a #53 dieron 502 | Jenkins estaba apagado | Con Jenkins encendido, las entregas nuevas responden 200 |
| `main` omitió Build, pruebas, Sonar e integración | La ejecución selectiva no vio servicios cambiados (el último commit era de documentos) | El PR #56 toca `jenkins/` (cambio común) y ejecutó la CI completa |
