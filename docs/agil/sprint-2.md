# Sprint 2 — lunes 9 a domingo 22 de noviembre de 2026

> **Cierre del sprint, 22 de noviembre.** Estado al final del sprint; la sustentación (HU-27) es el jueves 26. Cada historia enlaza su evidencia real en [`docs/evidencias/sprint-2/`](../evidencias/sprint-2/).

| | |
|---|---|
| **Objetivo** | *«El incremento de la Parte 2: la plataforma como código, la aplicación en Kubernetes, patrones de diseño, seguridad, observabilidad y pruebas avanzadas, y la documentación para operarlo»* (Parte 2, entregables E2.1 a E2.10) |
| **Equipo** | `jmrojas06` (A, plataforma) · `pierrecuevas` (B, aplicación y calidad) |
| **Comprometido** | **91 puntos** (15 historias; A tiene 51 y B, el resto) |
| **Tablero** | [GitHub Projects #8](https://github.com/orgs/ingesoft5/projects/8) |
| **Burndown** | [`metricas/sprint-2-burndown.md`](metricas/sprint-2-burndown.md) (filas del 9 al 18 reconstruidas; desde el 19, `metricas/burndown.sh`) |
| **Planning** | [`PLAN-PROYECTO.md`, Sprint 2](PLAN-PROYECTO.md) |
| **Releases del sprint** | `v1.2.0` (#66), `v1.3.0` (#68) y `v1.4.0` (#69), publicadas por el pipeline |
| **Entrega** | domingo 22 de noviembre, 23:59 (margen interno: 21:00); sustentación el lunes 23 |

## 1. Sprint review: historias contra sus criterios

| Issue | Historia | Resp. | Pts | Estado al 19 nov | Evidencia |
|---|---|---|---|---|---|
| [#21](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/21) | HU-14 Terraform en módulos y 4 ambientes | A | 13 | **Hecho** (PR #65). 5 módulos, 4 ambientes, estado remoto con *lock*, 22 pruebas de módulos; los 4 ambientes se crearon y se destruyeron de verdad, con `plan` limpio y un *drift* provocado y detectado | [`hu-14-terraform.md`](../evidencias/sprint-2/hu-14-terraform.md), [`infra-terraform.md`](../arquitectura/infra-terraform.md) |
| [#22](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/22) | HU-15 Los 8 servicios y el middleware en Kubernetes | A | 13 | **Hecho** (PR #67). Un chart de Helm, 38 pruebas de plantillas y una instalación real en un k3s local con 7 sondas y login real | [`hu-15-kubernetes.md`](../evidencias/sprint-2/hu-15-kubernetes.md), [`deploy/helm/README.md`](../../deploy/helm/README.md) |
| [#23](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/23) | HU-16 Pipeline: Terraform, Trivy y Kubernetes | A | 8 | **Hecho** (PR #73, aprobado por `pierrecuevas`). El check de Jenkins pasó en el Jenkins real (gitleaks, Trivy, Sonar e integración); los jobs de Terraform y Kubernetes se ejecutan en el ensayo de nube | `hu-16-pipeline.md` (en su PR) |
| [#24](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/24) | HU-17 Circuit Breaker + Retry | B | 5 | **Hecho** (PR #66, de `pierrecuevas`). Pruebas con fallas inyectadas y análisis de *trade-offs* | [`hu-17-resiliencia.md`](../evidencias/sprint-2/hu-17-resiliencia.md), [`docs/patrones/resiliencia.md`](../patrones/resiliencia.md) |
| [#25](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/25) | HU-18 External Configuration + Feature Toggle | B | 3 | **Hecho** (PR #68; la hizo A y B la revisó, que no tenía trabajo empezado) | [`hu-18-configuracion.md`](../evidencias/sprint-2/hu-18-configuracion.md), [`docs/patrones/configuracion.md`](../patrones/configuracion.md) |
| [#26](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/26) | HU-19 Cache-aside y patrones existentes | B | 5 | **Hecho** (PR #69, de `pierrecuevas`). Encontró un fallo real: tras reiniciar Redis el gateway dejaba pasar a usuarios confirmados | [`hu-19-cache-aside.md`](../evidencias/sprint-2/hu-19-cache-aside.md), [`docs/patrones/`](../patrones/README.md) |
| [#27](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/27) | HU-20 Prometheus y Grafana | B | 8 | **Hecho** (PR #76, de `pierrecuevas`; el #72 quedó incluido): métricas de los 8 servicios y 3 dashboards de Grafana | — |
| [#28](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/28) | HU-21 Alertas con Alertmanager | B | 3 | **Hecho** (PR #76; el #75 quedó incluido): 4 alertas probadas provocando la falla | — |
| [#29](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/29) | HU-22 Jaeger y Loki | B | 5 | **Hecho** (PR #76): Jaeger y Loki, con una traza de punta a punta | — |
| [#30](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/30) | HU-23 Seguridad | A | 8 | **Hecho** (PR #71, fusionado el 21 nov). Trivy, Sealed Secrets, NetworkPolicies, pods sin root, RBAC y TLS, probados en un k3s real; Let's Encrypt real queda pendiente de la nube. El RBAC de Jenkins quedó aplicado y se comprobó en el Jenkins real | [`hu-23-seguridad.md`](../evidencias/sprint-2/hu-23-seguridad.md) (en su PR), [`seguridad.md`](../arquitectura/seguridad.md) |
| [#31](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/31) | HU-24 Locust y OWASP ZAP | B | 5 | **Hecho** (PR #74, de `pierrecuevas`): Locust con objetivo declarado, BCrypt 12→10 (×3,5) y ZAP baseline | — |
| [#32](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/32) | HU-25 Rollback y registro de cambios en K8s | A | 2 | **Hecho** (PR #70). `release.sh` con `--atomic`; la demo revirtió sola una versión inexistente | [`hu-25-rollback.md`](../evidencias/sprint-2/hu-25-rollback.md) |
| [#33](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/33) | HU-26 Documentación completa | A+B | 5 | **Hecho** (PR #77): costos reales, manual de operaciones, resumen en inglés, arquitectura desplegada e informe PDF | [`costos.md`](../operaciones/costos.md), [`manual-operaciones.md`](../operaciones/manual-operaciones.md), [`resumen-ejecutivo.md`](../resumen-ejecutivo.md) |
| [#34](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/34) | HU-27 Presentación y sustentación | A+B | 3 | **En curso:** guion, falla provocada ensayada y cambio en vivo listos; la sustentación es el jueves 26 de noviembre | — |
| [#39](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/39) | HU-29 Bonos: caos y FinOps | A+B | 5 | **Hecho** (PR #80): chaos engineering y FinOps con evidencia (`docs/bonos/`) y la opción Spot en Terraform | — |

## 2. Métricas

| Métrica | Sprint 1 | Sprint 2 (al 19 nov) |
|---|---|---|
| Puntos comprometidos | 61 | 91 |
| Puntos terminados | 61 | **88 de 91** (14 historias); queda HU-27, la sustentación (3 pts) |
| Días del sprint | 4 (5 a 8 nov) | 14 (9 a 22 nov); se empezó a trabajar el 13 |
| Velocidad | 61 pts en 4 días ≈ 15 pts/día | 88 pts en 10 días de trabajo ≈ 9 pts/día (47 de ellos en los tres últimos días) |
| PR fusionados | 11 | #65 a #71, #73, #74, #76 a #80 (el #72 y el #75 quedaron incluidos en el #76) |
| Versiones publicadas | `v1.0.0`, `v1.1.0` | `v1.2.0`, `v1.3.0`, `v1.4.0`, `v1.5.0` |
| Pruebas | 121 (110 unitarias + 11 de integración) + 6 E2E | + 22 de módulos Terraform, 40 del chart de Helm (76 con HU-23), 14 de resiliencia, 13 del cache-aside |
| Costo de nube (consumo bruto del mes) | — | USD 49,45 de 60; neto USD 0,00 por créditos ([`costos.md`](../operaciones/costos.md)) |

**Cómo leer la velocidad.** Los dos sprints no son comparables punto a punto: el Sprint 1 fueron 14 historias pequeñas sin infraestructura nueva; el Sprint 2 pesa en infraestructura, que se prueba de verdad con cluster y nube (y la nube tiene presupuesto). Además se perdieron cuatro días: Terraform estaba planeado para el 9 al 11 y empezó el 13.

## 3. Acciones de la retro del Sprint 1: ¿se cumplieron?

| Acción acordada | Resultado |
|---|---|
| **Un PR a la vez contra `main`**; el siguiente se abre cuando el anterior se fusiona | **Cumplida.** #65 a #70 entraron uno tras otro y #71 está solo; la rama apilada de HU-18 se abrió como PR recién cuando #67 se fusionó |
| **Encender `tools` + `dev` al empezar cada bloque y apagar al terminar** | **Parcial.** Se incumplió dos veces: las 4 VMs quedaron encendidas dos días (9 y 10 nov, USD 21,7) y Jenkins cuatro (13 a 17 nov, ≈ USD 19). Desde el 17 nov cada encendido es una ventana corta que se apaga en la misma sesión (la del 18 nov: 11,6 min, ≈ USD 0,03) |
| **Check obligatorio de Jenkins en `main`** para que ningún PR entre sin CI | **Cumplida.** Sin `continuous-integration/jenkins/pr-merge` el PR queda `BLOCKED` |
| **Aviso en `up.sh` si la copia local está atrasada** (`git pull` antes de `up.sh`) | **No cumplida.** El script no lo avisa; se documentó en el manual de operaciones (lanzar desde `main` actualizado) y sigue pendiente de automatizar |
| **Correr los E2E contra el compose local antes de abrir un PR que toque el frontend** | **No aplicó:** ningún PR del sprint tocó el frontend |

## 4. Retrospectiva (borrador)

| Salió bien | Por mejorar | Acción |
|---|---|---|
| **Probar en un cluster real encontró lo que las pruebas de plantillas no:** en HU-15 tres fallos de arranque, en HU-23 que Trivy «pasaba» sin mirar el chart, en HU-16 que las herramientas montaban una carpeta vacía dentro de Jenkins | Casi todo lo del sprint depende de una ventana de nube o de Jenkins para quedar **demostrado**, y esas ventanas cuestan dinero y tiempo | Seguir con un simulador local para lo que se pueda y reservar *una* ventana planeada para lo que no |
| **Cada PR lo revisó el otro integrante corriendo las pruebas** (6 revisiones con evidencia en [`IAG_REGISTRO.md`](../../IAG_REGISTRO.md)); una de ellas halló un fallo real del cache-aside | **El inicio tardío (13 nov) y el costo:** el 83 % del gasto del mes vino de dos descuidos (VMs encendidas sin trabajo útil) | Regla «nada queda prendido» (18 nov) y medir el gasto contra el presupuesto antes de cada ventana |
| **Los controles negativos:** cada «bloqueado» tiene un control positivo que lo demuestra | **Se duplicó trabajo:** HU-17 se hizo dos veces porque no se avisó el mismo día que se tomaba una historia ajena | Avisar el mismo día antes de tomar una historia de otra persona |
| Las decisiones importantes quedaron registradas con su porqué en el momento en que se tomaron | **La observabilidad y las pruebas de carga y seguridad (HU-20 a HU-22 y HU-24, ≈ 10 % de la rúbrica) no tenían rama al 19 nov** | Acordar con `pierrecuevas` quién entrega qué y en qué orden antes de que acabe el sprint |
| | **Errores de proceso evitables:** un commit con la cabecera de 110 caracteres tumbó un check de Jenkins; un reinicio de Jenkins mató un build de `main`; el disco del PC al 94 % impidió una prueba | Comprobar el largo de las cabeceras antes de subir; esperar a que `up.sh` termine antes de lanzar builds; liberar disco antes de pruebas con k3s |

### Lecciones aprendidas

1. **Una prueba que no puede fallar no prueba nada.** Hay que armar la entrada mala a propósito y ver que falle (token falso, Dockerfile sin `USER`, versión inexistente).
2. **Lo que funciona en el portátil puede no funcionar dentro de Jenkins.** Un contenedor con el socket del host monta rutas del host, no suyas.
3. **El presupuesto es una restricción de diseño.** k3s en vez de EKS (USD 216/mes menos), sin NAT, ventanas cortas y destruir al terminar.
4. **Un PR a la vez cuesta menos que arreglar PR apilados.**
5. **Avisar antes de tomar el trabajo de otra persona.**

## 5. Cierre del sprint (se completa el 22 nov)

- [x] Estado final de las 15 historias y puntos terminados (88 de 91).
- [x] Burndown con la foto del 22 y el tablero al día.
- [x] Retrospectiva final. Acciones para después de la entrega: ensayo en la nube (ejecuta los jobs de Terraform y Kubernetes y repite el rollback), subir Spring Boot a la 3.5.x y repetir las capturas vacías de Grafana.
