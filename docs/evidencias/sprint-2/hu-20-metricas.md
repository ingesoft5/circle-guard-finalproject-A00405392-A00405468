# HU-20 · Métricas con Prometheus y dashboards de Grafana: evidencia

Historia: [#27](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/27) · Diseño: [`docs/observabilidad/metricas.md`](../../observabilidad/metricas.md) · Fecha: 18 de noviembre de 2026

## Criterios de aceptación

| Criterio | Estado | Evidencia |
|---|---|---|
| Micrometer + `/actuator/prometheus` en los 8 servicios | ✅ | Registro en el `build.gradle.kts` raíz y `include: health,prometheus` en los 8 `application.yml`; en el compose, Prometheus lee los 5 servicios que corren (`up`) |
| Dashboard por servicio | ✅ | **CircleGuard · Servicios (RED)**: una fila por servicio con Rate, Errors, p95/p50, instancias, memoria y CPU ([captura](hu-20-grafana-servicios.png)) |
| Dashboard de negocio: tiempo de contención, promociones por minuto, check-ins | ✅ | **CircleGuard · Negocio** ([captura](hu-20-grafana-negocio.png)) |
| Métricas RED (rate, errors, duración p95) por servicio | ✅ | Histograma de `http.server.requests` activado en los 8 servicios; consultas en [`metricas.md`](../../observabilidad/metricas.md#métricas-técnicas-red-por-servicio) |
| Métricas de negocio propias con Micrometer en el código | ✅ | `circleguard_promotions_total`, `circleguard_containment_time_seconds`, `circleguard_gate_checkins_total` y `circleguard_surveys_submitted_total` |

## 1. Pruebas

| Servicio | Unitarias | Nuevas o cambiadas en esta historia |
|---|---|---|
| promotion | 44 (+ 2 de integración) | `PromotionMetricsTest` (4: promociones por estado, series en 0 al arrancar, tiempo de contención, marcas de tiempo inválidas), `SurveyListenerTest` (mide la contención con la marca de tiempo de form) |
| gateway | 10 (+ 11 de integración con Redis real, con el cache-aside de HU-19) | `GateMetricsTest` (2: los 4 resultados empiezan en 0 y se cuentan por separado), `GateControllerTest` (cada validación cuenta como check-in) |
| form | 21 | `FormServicesTest` (el contador por síntomas), `SymptomMapperTest` (2: el cuestionario en español y palabras completas) |
| auth, identity, dashboard, file, notification | 33, 17, 15, 1, 11 | Solo cambió la configuración; siguen pasando |

Las verificaciones de cobertura mínima del 60 % siguen pasando.

## 2. En el sistema real (compose de dev + Prometheus + Grafana)

`./deploy/compose/compose.sh dev -f deploy/compose/observability.yml up -d --wait`: 12 contenedores `healthy`. Prometheus lee `auth`, `identity`, `form`, `promotion` y `gateway` (todos `up`). Desde afuera, `GET /api/gateway/actuator/prometheus` por el nginx responde **404**, y `/api/gateway/actuator/health/readiness` sigue en **200** para la prueba de humo.

Tráfico con [`hu-20-trafico.sh 8 5`](hu-20-trafico.sh), solo por las APIs públicas. La semilla del grafo de contactos va directo a Neo4j. Hubo una ronda inicial (8 personas con 3 contactos cada una, 16 cuestionarios, 30 check-ins y un caso confirmado) y luego 5 minutos de carga (23 rondas). Lo que registró Prometheus coincide **exactamente** con lo enviado ([salida completa](hu-20-trafico-y-prometheus.txt)):

| Métrica | Esperado por el tráfico | Prometheus |
|---|---|---|
| `circleguard_promotions_total{status="SUSPECT"}` | 8 + 23 personas con síntomas | **31** |
| `circleguard_promotions_total{status="PROBABLE"}` | 24 + 23 contactos | **47** |
| `circleguard_promotions_total{status="CONFIRMED"}` | 1 caso confirmado | **1** |
| `circleguard_gate_checkins_total{result="granted"}` | 20 + 23 × 5 | **135** |
| `circleguard_gate_checkins_total{result="denied_invalid"}` | 6 + 23 QR falsos | **29** |
| `circleguard_gate_checkins_total{result="denied_risk"}` | 4 + 23 validaciones de un caso confirmado | **27** |
| `circleguard_surveys_submitted_total` | 31 con síntomas, 8 sin | **31 / 8** |
| `circleguard_containment_time_seconds` | una medición por cuestionario con síntomas | **31**, promedio **0,60 s** |

Latencia p95 en los últimos 5 minutos: gateway 6 ms, auth 8 ms, form 13 ms, promotion 111 ms.

**Capturas:** en [`hu-20-grafana-negocio.png`](hu-20-grafana-negocio.png) se ven las curvas de promociones, check-ins y cuestionarios por minuto, y el tiempo de contención. En [`hu-20-grafana-servicios.png`](hu-20-grafana-servicios.png), las filas de los 5 servicios; en promotion aparece la ruta interna `/internal/v1/users/{anonymousId}/status` que usa el cache-aside de HU-19. Los totales de la última hora salen con un decimal (27,3 y 79,4) porque `increase()` extrapola; después de la captura se configuraron para mostrarse enteros.

## 3. Dashboard de resiliencia con una falla provocada

Con el compose arriba se hizo un login cada 3 s durante 4 minutos: identity se apagó a los 60 s y se encendió a los 150 s. Resultado: 17 logins con 200, luego 22 con 503 (degradado, sin llamar a identity con el circuito abierto) y de nuevo 16 con 200 al recuperarse, sin reiniciar auth.

[`hu-20-grafana-resiliencia.png`](hu-20-grafana-resiliencia.png): el circuito `identity` pasa de **CERRADO** a **ABIERTO** y vuelve a **CERRADO**. La tasa de fallas sube al 100 %, las llamadas rechazadas con el circuito abierto llegan a 12 por minuto, y en el login los 200 se convierten en 503 y luego vuelven a 200.

La primera versión del panel de estado salía vacía: sumaba `state="open"` con `state="half_open"` sin agrupar antes por nombre, y como las etiquetas no coinciden no emparejaba ninguna serie. Se corrigió agrupando antes de sumar, comprobado contra la API de Prometheus.

## 4. Hallazgos

- **El detector de síntomas solo entendía inglés.** `SymptomMapper` buscaba `fever`, `cough` y `breathing`, pero el cuestionario de la app está en español ("¿Tienes fiebre?"). **Ningún cuestionario real detectaba síntomas**, así que nadie entraba al cerco por esa vía. Se vio porque `circleguard_promotions_total` no subía después de enviar cuestionarios con síntomas. Los E2E no lo notaban, porque solo comprueban que el envío funcione. Se corrigió con palabras en español, sin tildes y como palabra completa ("tos" no coincide con "datos").
- **Los contadores nacían con el primer evento** y Prometheus no contaba ese primer salto: los totales de la última hora salían en 0. Ahora se registran en 0 al arrancar.
- **Tiempo de contención:** en la primera ráfaga, con los servicios recién arrancados, el promedio fue de 5,5 s (máximo 6,9 s); con el sistema caliente bajó a 0,6 s y el p95 queda por debajo de 3 s. El código declara un objetivo de menos de 1 s (NFR-1) solo para la consulta en Neo4j; la métrica mide además la espera en Kafka. Es la línea base para la prueba de rendimiento con Locust (HU-24).
- **Las métricas no se publican hacia afuera:** el nginx devuelve 404 en `/api/<servicio>/actuator/prometheus`. Identity exigía token en todo lo que no fuera health, así que se le abrió solo `/actuator/prometheus`, como en auth y promotion.
