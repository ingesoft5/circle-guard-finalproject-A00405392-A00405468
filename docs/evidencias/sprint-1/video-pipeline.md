# E1.8 · Video del pipeline de punta a punta — 8 de noviembre de 2026

Recorrido por las herramientas y los tres ambientes de CircleGuard después de la corrida real del pipeline. Es la grabación de pantalla tal cual, sin edición.

| Dato | Valor |
|---|---|
| Duración | 3 min 47 s |
| Tamaño | 189 MB (MP4) |
| Grabado | 8 de noviembre de 2026, 18:16 (hora de Colombia), en el computador de `jmrojas06` |
| Edición | Ninguna |
| Enlace | [circleguard-pipeline-sprint1.mp4](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/releases/download/v1.1.0/circleguard-pipeline-sprint1.mp4), archivo adjunto al release `v1.1.0` (GitHub lo ofrece como descarga) |

## Cómo se grabó

- **Grabación:** captura de pantalla del computador del autor mientras el navegador recorría las páginas. El video no se editó, no se cortó ni se le agregaron títulos.
- **Navegación automatizada con Claude en Chrome:** el cambio de una página a otra lo hizo la extensión *Claude en Chrome*, que controla el navegador mediante la API de depuración. Por eso, en la parte superior de todo el video se ve el aviso **«“Claude” comenzó a depurar este navegador»**. La extensión solo navegó y se desplazó; no se le pidió pulsar ningún botón que cambie algo (aprobar, desplegar, *build*, rollback, fusionar o borrar).
- **Lo que hizo la persona:** iniciar sesión en Jenkins, SonarQube, Nexus y GitHub antes de grabar, y entrar a mano a la aplicación en dev, stage y prod con el usuario de prueba, porque las contraseñas no se le entregan a la extensión.
- **Quién definió el recorrido:** el orden y las 14 direcciones del recorrido se acordaron con la IA. El prompt y su verificación están registrados en [`IAG_REGISTRO.md`](../../../IAG_REGISTRO.md).

## Guion: lo que muestra el video

Los tiempos son aproximados (salen de cuadros tomados cada 15 segundos).

| Tiempo | Pantalla | Qué demuestra |
|---|---|---|
| 0:00 | GitHub: *Releases* | `v1.1.0` y `v1.0.0`, con notas generadas por semantic-release y `CHANGELOG.md` como artefacto |
| 0:15 | GitHub: PR fusionados (17) | Revisión por pares: cada PR aprobado por el otro integrante, con su etiqueta de entregable |
| 0:30 | Webhook de GitHub: configuración | URL de Jenkins y eventos `push` y `pull_request` |
| 0:45 | Webhook: *Recent Deliveries* | Entregas con ✓. Las ⚠ de la tarde son de cuando las VMs estaban apagadas, antes de encender Jenkins |
| 1:00 | Tablero de GitHub Projects | Historias HU-01 a HU-09 en Done; HU-10 y HU-11 en Review |
| 1:30 | Jenkins: `main` #2 (`v1.0.0`) | Éxito, pruebas sin fallas, `CHANGELOG.md` y «approved by pierrecuevas» antes de prod |
| 1:45 | Jenkins: `PR-56` #1 | CI de un PR sobre los 9 servicios, con cobertura JaCoCo de líneas de 70,31 % |
| 2:00 | Jenkins: job `circleguard-rollback` | Sus etapas y el build #1 de dev a `9.9.9`, que falló a propósito para probar los avisos |
| 2:15 | SonarQube: proyectos | Los 8 proyectos con el *quality gate* en Passed |
| 2:30 | SonarQube: `circleguard-auth-service` | Panel del servicio con el *quality gate* en Passed |
| 2:45 | Nexus: repositorio `circleguard` | Las 9 imágenes; en `circleguard-web`, los *tags* `1.0.0`, `1.1.0`, `sha-f2b7707` y `sha-34303d1` |
| 3:15 | Aplicación en dev | Sesión iniciada y pase con código QR |
| 3:30 | Aplicación en stage | Lo mismo en stage |
| 3:40 | Aplicación en prod | Lo mismo en prod |

## Antes y después

Lo que muestra el video es el resultado de este sprint. El punto de partida, según el enunciado y la línea base del Sprint 0, era un sistema funcional sin ninguna práctica DevOps.

| Métrica | Antes (4 de noviembre) | Después (8 de noviembre) |
|---|---|---|
| Pruebas unitarias | 51 | 110 |
| Pruebas de integración y E2E | 0 y 0 | 11 de integración y 6 E2E de Playwright |
| Cobertura de líneas, auth | 29,1 % | 89,6 % |
| Cobertura de líneas, identity | 50,4 % | 84,4 % |
| Cobertura de líneas, form | 43,6 % | 88,6 % |
| Cobertura de líneas, promotion | 35,0 % | 68,4 % |
| Cobertura de líneas, gateway | 81,0 % | 81,0 % |
| Imágenes Docker | 0 | 9, con *tags* de versión y de commit |
| Pipeline de CI/CD | ninguno | Jenkins multibranch con *Shared Library* de 11 pasos |
| Ambientes desplegados | 0 | 3 (dev, stage y prod), con 10 contenedores `healthy` cada uno |
| Versiones publicadas | 0 | 2 (`v1.0.0` y `v1.1.0`), con notas automáticas |
| Secretos | en texto plano en `docker-compose.dev.yml` | generados en cada VM, fuera del repositorio |
| Reconstruir la plataforma | pasos manuales | un comando, `up.sh`, en 3,5 min |
| Errores reales del código base encontrados por las pruebas | 0 | 3 de integración y 4 de la aplicación, ya corregidos |

La cobertura de «antes» y de «después» es de JaCoCo (línea base y [`cobertura-servicios-nucleo.md`](cobertura-servicios-nucleo.md)). Medida por SonarQube sobre el código final: 88,6 %, 84,6 %, 88,6 %, 70,4 % y 81,8 %, en el mismo orden.

## Lo que el video no muestra

El paso a producción de `v1.1.0` y el rollback de prod no aparecen en esta grabación. Esa evidencia está en [`ensayo-pipeline-real.md`](ensayo-pipeline-real.md) (§5 y §7) y en las capturas del informe.
