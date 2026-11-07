# Política de gestión de cambios (HU-11, #18)

Todo cambio llega a los ambientes por el mismo camino: **issue → rama corta → PR revisado → `main` → pipeline**. Lo que varía según el tipo de cambio es quién aprueba, cuándo y qué documentación exige. La política aplica a código, infraestructura (`infra/`, `jenkins/`, `deploy/`) y documentación.

## 1. Tipos de cambio

| Tipo | Ejemplos | Aprobación | Camino | Versión |
|---|---|---|---|---|
| **Estándar** | Documentación, dependencia menor, pipeline o infraestructura sin impacto en usuarios (`docs`, `chore`, `ci`, `build`, `test`, `refactor` sin cambio de comportamiento) | 1 revisor: el otro integrante | PR → `main`. El pipeline corre la CI; **no genera versión ni despliega** | — |
| **Normal** | `feat`, `fix`, `perf`: cambia lo que ve el usuario o el comportamiento de un servicio | 1 revisor **+ aprobación manual (`input`) para prod** | PR → `main` → `vX.Y.Z` → dev → E2E → stage → `input` → prod | MINOR o PATCH |
| **Mayor** | Cambio incompatible: esquema de datos, contrato de API o de eventos, roles (`feat!`, `BREAKING CHANGE`) | 1 revisor + **solicitud de cambio (RFC) aprobada antes de fusionar** + `input` para prod | Igual que *normal*, con la RFC enlazada en el PR | MAJOR |
| **Emergencia** | Caída o fallo grave en prod | **Se actúa primero**: *rollback* inmediato con el job `circleguard-rollback`. El arreglo va en una rama `hotfix/*` con revisión en caliente (puede ser por llamada; la aprobación en GitHub sigue siendo obligatoria para fusionar). La RFC se documenta **después, en máximo 24 h** | rollback → `hotfix/*` → PR → `main` → pipeline | PATCH |

El tipo lo decide el autor al abrir el PR (plantilla de PR, sección *Tipo de cambio*) y lo confirma el revisor. El tipo de commit (Conventional Commits) y el tipo de cambio coinciden: un `feat` nunca es *estándar*.

## 2. Solicitud de cambio (RFC)

- Plantilla: issue **"Solicitud de cambio"** ([`.github/ISSUE_TEMPLATE/solicitud-cambio.md`](../../.github/ISSUE_TEMPLATE/solicitud-cambio.md)), etiqueta `change-request`. Copia comentada en [`solicitud-de-cambio.md`](solicitud-de-cambio.md).
- **Obligatoria** para cambios *mayores* (antes) y *emergencias* (después, ≤ 24 h). **Opcional** para cambios *normales* que toquen datos o configuración de prod.
- Debe tener: qué cambia y por qué, clasificación, servicios y ambientes afectados, versión actual → nueva, riesgo e impacto, plan de pruebas, **plan de rollback** (versión destino) y quién aprueba.
- La RFC se enlaza desde el PR (`Refs #N`) y se cierra cuando el cambio está verificado en el ambiente destino.

## 3. Quién aprueba qué

| Decisión | Quién | Dónde queda |
|---|---|---|
| Fusionar un PR | El integrante que **no** es el autor (protección de `main`: 1 aprobación, descartada si llegan commits nuevos; quien hizo el último *push* no puede aprobar) | Revisión del PR |
| Promover a prod | Cualquiera de los dos en el `input` del pipeline (`submitter: jmrojas06,pierrecuevas`); de preferencia quien **no** escribió el cambio | Etapa *Aprobación para prod* del build + aviso en Discord/WhatsApp |
| Rollback de prod | Quien detecta el problema lo ejecuta; el job pide confirmación (`input`) | Build del job `circleguard-rollback` + `/opt/circleguard/RELEASES` + Discord |
| Cambio mayor | Los dos integrantes, en la RFC | Comentario de aprobación en el issue RFC |

## 4. Esquema de etiquetado

| Qué | Formato | Quién lo pone |
|---|---|---|
| Versión | `vMAJOR.MINOR.PATCH` (tag de Git + GitHub Release). `fix`/`perf` → PATCH, `feat` → MINOR, `!`/`BREAKING CHANGE` → MAJOR. Primera versión `v1.0.0` | semantic-release en `main` ([detalle](../release-notes/README.md)) |
| Imágenes | `<registro>/circleguard/<servicio>:X.Y.Z` y `:sha-<7 caracteres>`; **nunca `latest`**. Labels OCI `version`, `revision`, `source` | `dockerPublish` en el pipeline |
| Ambientes | `/opt/circleguard/RELEASES`: fecha, versión, motivo (`deploy` o `rollback`), commit, URL del build | `deploy.sh` en cada despliegue |
| Ramas | `<tipo>/<issue>-<descripcion>` (`feat/15-entrega-continua`, `hotfix/<descripcion>`) | Autor ([CONTRIBUTING §1](../../CONTRIBUTING.md)) |
| Issues y PR | `resp: A (jmrojas06)` / `resp: B (pierrecuevas)`, entregable `E1.x`/`E2.x`, `change-request`, `bug` | Autor |

## 5. Ventanas y reglas operativas

- **dev y stage** reciben cada versión de `main` sin ventana: son ambientes de verificación.
- **prod** solo cambia con el `input` aprobado y con alguien del equipo disponible los **30 minutos siguientes** para ejecutar el rollback si el *smoke test* o un usuario reporta un problema.
- Un E2E en rojo contra dev **detiene** la promoción a stage; un *smoke test* en rojo detiene la promoción al siguiente ambiente.
- Durante la sustentación o una entrega no se fusiona a `main` salvo emergencia (*change freeze*).

## 6. Trazabilidad de punta a punta

```
issue HU-N  →  PR "Closes #N" (revisado)  →  commit squash en main  →  tag vX.Y.Z + Release + CHANGELOG
→  imágenes X.Y.Z en Nexus  →  RELEASES en dev/stage/prod  →  aviso en Discord/WhatsApp
```

Con una versión en la mano se puede llegar al commit, al PR, al issue, a la RFC (si la hubo) y al build que la desplegó; y al revés.

## 7. Rollback

El *rollback* no es un camino especial: es volver a desplegar una versión anterior, ya publicada e inmutable, con el job `circleguard-rollback` (`ENV`, `VERSION`). Se dispara cuando el *smoke test* falla tras un despliegue, cuando los E2E fallan en stage o cuando un usuario reporta una regresión en prod. Queda registrado en `RELEASES` con el motivo `rollback`, en el build del job y en Discord. Si el problema es de datos (migración incompatible), el rollback requiere además una RFC de emergencia con el plan de recuperación de datos.
