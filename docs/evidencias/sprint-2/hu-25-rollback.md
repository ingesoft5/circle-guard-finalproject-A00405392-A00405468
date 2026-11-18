# HU-25 · Rollback en Kubernetes y registro de cambios por ambiente — evidencia (E2.8)

Issue [#32](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/32) · Sprint 2. Uso y decisiones en [`deploy/helm/README.md`](../../../deploy/helm/README.md#desplegar-revertir-y-registro-de-cambios) y en la [política de cambios §7](../../change-management/politica.md#7-rollback).

## Dónde se probó

En el **k3s local** (la misma versión que Terraform instala en los nodos, `rancher/k3s:v1.37.2-k3s1`) con las imágenes reales del repo etiquetadas `local` y `v2`; el presupuesto de AWS casi se agotó y la instalación en un nodo de AWS queda para una ventana corta y planeada. Todo pasa por [`release.sh`](../../../deploy/helm/release.sh), que es lo que usará el pipeline en HU-16.

## Criterios del issue

| Criterio | Resultado | Evidencia |
|---|---|---|
| `helm rollback` ejecutado y evidenciado | Un rollback manual (`release.sh rollback local`) de la revisión 4 a la 1, y uno **automático** (`--atomic`) cuando un despliegue no dejó los pods listos. En los dos, `GET /version` confirma la versión activa | [`hu-25-rollback-k3s.txt`](hu-25-rollback-k3s.txt) |
| Release notes por versión publicada | `semantic-release` publica una GitHub Release con sus notas en cada versión: [v1.0.0](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/releases/tag/v1.0.0), [v1.1.0](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/releases/tag/v1.1.0) y [v1.2.0](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/releases/tag/v1.2.0). Cada despliegue en Kubernetes lleva la versión y el commit en su registro, así que se llega de la revisión a la release | [`docs/release-notes/README.md`](../../release-notes/README.md) |
| Registro de cambios por ambiente | El historial de Helm de cada namespace (`circleguard-<ambiente>`): revisión, fecha, estado, **versión** y motivo. `release.sh history <ambiente> --md` lo exporta | [`hu-25-registro-local.md`](hu-25-registro-local.md) |

## Lo que muestra la prueba

Cinco revisiones del mismo release, en este orden:

| Rev | Qué pasó | Versión activa después |
|---|---|---|
| 1 | `deploy local · commit 1111111 · build #1` | `local` |
| 2 | `deploy v2 · commit 2222222 · build #2` | `v2` |
| 3 | `deploy v404`: la imagen no existe, los pods nunca quedan listos. A los 100 s Helm declara la revisión `failed` y **revierte solo** | `v2` |
| 4 | `Rollback to 2`: el rollback automático de la revisión 3 | `v2` |
| 5 | `release.sh rollback local` sin indicar revisión: salta la 3 (fallida) y la 2 (también `v2`, la versión que ya corre) y vuelve a la 1 | `local` |

- **El despliegue fallido no dejó el servicio caído:** en un rolling update los pods viejos siguen atendiendo hasta que los nuevos estén listos, y tras el fallo `GET /version` devolvía `v2` (no se midió la disponibilidad durante los 100 s). El comando terminó con código de salida 1, que es lo que hará fallar el paso del pipeline.
- **Elegir la revisión de destino no es trivial:** `helm rollback` sin número vuelve a la revisión anterior, pero después de un rollback esa suele ser la misma versión que ya corre. Por eso `release.sh` toma la **última revisión buena con otra versión**, y esa lógica tiene pruebas (`release.sh selftest`, 8 casos: intentos fallidos, una sola revisión, orden numérico y no por posición) que corren con `run.sh check`.
- **El registro conserva el fallo:** la revisión 3 queda como `failed` con su versión (`v404`), no se borra. Eso es lo que permite explicar después qué se intentó.

## Lo que encontró el trabajo (y que habría fallado en producción)

1. **Helm conserva solo 10 revisiones por defecto** (`--history-max 10`). Con despliegues frecuentes el registro de un ambiente se perdería en días. `release.sh` fija 50.
2. **`helm history` no dice qué versión corría en cada revisión.** La descripción de un rollback es solo `Rollback to N`. La versión se saca de los valores de esa revisión (`helm get values --revision N`), y por eso la columna «Versión» de `history --md`.
3. **`helm rollback` no acepta `--description`**, así que el motivo de un rollback es siempre `Rollback to N`; el de un despliegue sí lleva versión, commit y build. Se aceptó: el registro dice qué revisión se restauró y la columna de versión dice a cuál volvió.

## Limitaciones

- **No se probó contra un clúster de AWS.** El mismo `release.sh` recibe el `KUBECONFIG_FILE` del ambiente; la ventana de prueba en AWS es un pendiente conjunto con HU-16 y HU-23.
- **El rollback de la aplicación no deshace cambios de datos.** Una migración incompatible sigue necesitando la RFC de emergencia de la [política de cambios](../../change-management/politica.md#7-rollback).
- **El registro vive en el clúster del ambiente.** Si el clúster se destruye (como se hace al terminar una prueba), el historial se va con él; por eso HU-16 debe archivar `history --md` como artefacto de cada despliegue.
- **El paso de aprobación de `prod` está en el script (`--yes`), no en el pipeline todavía.** Se conecta en HU-16.
