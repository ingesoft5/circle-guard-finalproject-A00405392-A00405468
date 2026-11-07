# Solicitud de cambio (RFC)

Se abre como **issue** con la plantilla *Solicitud de cambio* ([`.github/ISSUE_TEMPLATE/solicitud-cambio.md`](../../.github/ISSUE_TEMPLATE/solicitud-cambio.md)), que crea el issue con la etiqueta `change-request` y el título `RFC: ...`. Este archivo explica cómo llenar cada campo y deja un ejemplo. Cuándo es obligatoria: ver la [política](politica.md#2-solicitud-de-cambio-rfc).

## Cómo llenar la plantilla

| Campo | Qué poner |
|---|---|
| **Cambio solicitado** | Una frase con el *qué* y un párrafo con el *por qué* (problema o historia que resuelve). |
| **Clasificación** | Estándar, Normal, Mayor o Emergencia, según la [tabla de tipos](politica.md#1-tipos-de-cambio). |
| **Servicios afectados** | Nombres de las carpetas de `services/` o `web`. |
| **Ambientes** | Hasta dónde llega: dev / stage / prod. |
| **Versión actual → nueva** | La que está en prod (`/opt/circleguard/RELEASES` o GitHub Releases) y la que saldrá (según el tipo de commit). |
| **PR relacionado** | Número del PR que implementa el cambio (`Refs #RFC` en el PR). |
| **Ventana de despliegue** | Cuándo se aprobará el `input` de prod y quién estará disponible los 30 min siguientes. |
| **Riesgo e impacto** | Qué puede fallar, a quién afecta (guardias, centro de salud, estudiantes) y cómo se detecta (smoke, E2E, Discord). |
| **Plan de pruebas** | Qué capa lo cubre: unitarias (JaCoCo ≥ 60 %), integración (Testcontainers), E2E (Playwright contra dev), smoke tras cada despliegue. |
| **Plan de rollback** | Versión destino y comando/job: `circleguard-rollback` con `ENV` y `VERSION`. Si hay migración de datos, cómo se revierte. |
| **Aprobación** | Quién aprueba (el integrante que no es autor) y en qué ambiente quedó verificado. |

## Ejemplo: RFC-001 · Primer despliegue a producción (v1.0.0)

> Issue real: [#52](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/52), abierto con la plantilla el 7 de noviembre.

**Cambio solicitado.** Llevar a prod la primera versión completa de la Parte 1: los 5 servicios núcleo, el middleware y el cliente web, con la configuración por variables de entorno y los secretos generados en la VM. Es la primera vez que prod recibe tráfico, así que se trata como cambio **Normal** con RFC por tocar configuración de prod.

**Clasificación.** Normal (`feat`), con RFC opcional por ser el primer despliegue.

| Campo | Valor |
|---|---|
| Servicios afectados | auth, identity, form, promotion, gateway, web |
| Ambientes | dev → stage → prod |
| Versión actual → nueva | (ninguna) → `v1.0.0` |
| PR relacionado | #48 (imágenes y compose), #51 (entrega continua) |
| Ventana de despliegue | Ensayo general del domingo 8 nov, 10:00; ambos integrantes conectados |

**Riesgo e impacto.** Prod arranca vacío (bases de datos nuevas, secretos recién generados). Riesgo principal: un servicio no llega a `healthy` por memoria en la VM de 8 GB; se detecta en `up -d --wait` y en el smoke test, y el pipeline no avanza. No hay usuarios reales afectados todavía.

**Plan de pruebas.** CI en verde (110 unitarias, 11 de integración, quality gate), smoke test en dev y stage, E2E de Playwright contra dev, login de `super_admin` y validación de un QR en stage antes de aprobar el `input`.

**Plan de rollback.** No hay versión anterior: el rollback es `compose down` en prod (deja la VM como estaba). Para versiones siguientes: job `circleguard-rollback` con `ENV=prod` y `VERSION=1.0.0`.

**Aprobación.** `input` de prod aprobado por el integrante que no fusionó el último PR; verificación en prod con el smoke test y el login.
