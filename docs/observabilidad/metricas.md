# Métricas: Micrometer, Prometheus y Grafana

Historia: [HU-20 (#27)](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/27) · Evidencia: [`hu-20-metricas.md`](../evidencias/sprint-2/hu-20-metricas.md)

## Qué expone cada servicio

Los 8 servicios publican `/actuator/prometheus` (registro `micrometer-registry-prometheus`, agregado una sola vez en el [`build.gradle.kts`](../../build.gradle.kts) raíz). Cada serie lleva la etiqueta `application` con el nombre del servicio (`management.metrics.tags.application`).

| Dónde se publica | Quién lo lee |
|---|---|
| `http://<servicio>:<puerto>/actuator/prometheus`, dentro de la red | Prometheus (compose: [`prometheus.yml`](../../deploy/observability/prometheus/prometheus.yml); Kubernetes: lo despliega la plataforma) |
| `/api/<servicio>/actuator/prometheus` por el nginx del web | **Nadie: responde 404.** Las métricas no se publican hacia afuera ([`default.conf.template`](../../mobile/nginx/default.conf.template)); `/actuator/health` sí pasa, porque lo usa la prueba de humo |

En auth, identity y promotion, Spring Security permite `/actuator/prometheus` sin token (en identity era obligatorio y Prometheus no habría podido leerlo).

## Métricas técnicas: RED por servicio

Salen de `http.server.requests`, que Spring Boot mide en cada petición. Se activó el histograma (`management.metrics.distribution.percentiles-histogram.http.server.requests: true`) para calcular percentiles en Prometheus y poder sumarlos entre réplicas. Un percentil calculado dentro de la app no se puede promediar entre réplicas; los buckets sí se suman.

| | PromQL (`uri!~"/actuator.*"` excluye los sondeos de Prometheus y Kubernetes) |
|---|---|
| **R**ate | `sum by (uri) (rate(http_server_requests_seconds_count{application="$application", uri!~"/actuator.*"}[5m]))` |
| **E**rrors | `100 * (sum(rate(...{status=~"5.."}[5m])) or vector(0)) / sum(rate(...[5m]))` |
| **D**uration p95 | `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{...}[5m])))` |

## Métricas de negocio (propias, en el código)

| Métrica en Prometheus | Qué mide | Dónde se registra |
|---|---|---|
| `circleguard_promotions_total{status}` | Personas que pasan a un estado: la que reporta y cada contacto que cambió. **Promociones por minuto** = `sum by (status) (rate(circleguard_promotions_total{status=~"SUSPECT\|PROBABLE\|CONFIRMED"}[5m])) * 60` | promotion: [`HealthStatusService`](../../services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/HealthStatusService.java) después de actualizar Neo4j y Redis, vía [`PromotionMetrics`](../../services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/metrics/PromotionMetrics.java) |
| `circleguard_containment_time_seconds` (histograma) | **Tiempo de contención:** desde que la persona envía el cuestionario con síntomas (marca de tiempo del evento de form) hasta que promotion dejó aplicado su cerco (grafo, contactos y Redis). Incluye la espera en Kafka | promotion: [`SurveyListener`](../../services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/listener/SurveyListener.java) |
| `circleguard_gate_checkins_total{result}` | **Check-ins** en la puerta: `granted`, `denied_risk` (persona en cerco), `denied_invalid` (QR falso o vencido), `denied_unavailable` (no se pudo saber el estado) | gateway: [`GateController`](../../services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/controller/GateController.java) vía [`GateMetrics`](../../services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/metrics/GateMetrics.java) |
| `circleguard_surveys_submitted_total{symptoms}` | Cuestionarios recibidos, con o sin síntomas | form: [`HealthSurveyService`](../../services/circleguard-form-service/src/main/java/com/circleguard/form/service/HealthSurveyService.java) |

**Los contadores se registran en 0 al arrancar.** Una serie que nace con valor 1 no le muestra a Prometheus el salto desde 0, así que `increase()` y `rate()` no cuentan el primer evento. En la primera prueba los paneles de "última hora" salían en 0 justamente por eso.

**Decisiones:**
- Los check-ins se cuentan en el controlador y no dentro de `QrValidationService`: así cuentan exactamente lo que vio el guardia, sea cual sea el camino interno (Redis o promotion).
- El tiempo de contención usa la marca de tiempo que pone form, porque es lo que vive la persona: desde que envía el cuestionario. Depende de que los relojes de form y promotion estén sincronizados (NTP en las VMs y en los nodos); un valor negativo se descarta.
- Las etiquetas tienen valores acotados (estado, resultado, sí/no), nunca el ID de una persona: cada valor distinto de una etiqueta crea una serie nueva en Prometheus.

## Dashboards (como código)

Los dashboards se generan con [`generate_dashboards.py`](../../deploy/observability/grafana/generate_dashboards.py), quedan como JSON en [`deploy/observability/grafana/dashboards/`](../../deploy/observability/grafana/dashboards) y Grafana los carga por *provisioning*. Las consultas quedan revisables en el PR y no dependen de clics en la interfaz.

| Dashboard | Contenido |
|---|---|
| **CircleGuard · Servicios (RED)** | Una fila por servicio (variable `Servicio`, con *repeat*): Rate por ruta, % de 5xx, p95 y p50, instancias, memoria heap y CPU |
| **CircleGuard · Negocio** | Promociones por minuto por estado, tiempo de contención (p95 y promedio), check-ins por minuto por resultado, cuestionarios por minuto y totales de la última hora |

## Cómo verlo en local

```bash
# en deploy/compose/dev/.env: GRAFANA_ADMIN_PASSWORD=<algo> (y GRAFANA_PORT si el 3000 está ocupado)
./deploy/compose/compose.sh dev -f deploy/compose/observability.yml up -d --wait
bash docs/evidencias/sprint-2/hu-20-trafico.sh 8 5     # tráfico real: 8 personas + 5 min de carga
# Grafana: http://localhost:3000  ·  Prometheus: http://localhost:9090 (solo en 127.0.0.1)
```

En Kubernetes, la plataforma despliega Prometheus y Grafana (con `kubernetes_sd_configs`); las métricas, las consultas y los JSON de los dashboards son los mismos.
