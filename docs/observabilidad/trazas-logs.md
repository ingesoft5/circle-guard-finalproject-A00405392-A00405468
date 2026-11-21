# Trazas distribuidas (Jaeger) y logs centralizados (Loki)

Historia: [HU-22 (#29)](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/29) · Evidencia: [`hu-22-trazas-logs.md`](../evidencias/sprint-2/hu-22-trazas-logs.md) · Métricas y alertas: [`metricas.md`](metricas.md), [`alertas.md`](alertas.md)

## Para qué

Un cuestionario con síntomas cruza cuatro servicios y dos tópicos de Kafka antes de que alguien quede en cerco y reciba el aviso. Con métricas se sabe que algo va lento; con la traza se sabe **en cuál de los saltos**, y con los logs, **por qué**:

```
form (HTTP POST /surveys) ──Kafka survey.submitted──► promotion (actualiza Neo4j y Redis)
                                                         └─Kafka promotion.status.changed──► notification
```

## Trazas: Micrometer Tracing + OpenTelemetry → Jaeger

| Pieza | Dónde |
|---|---|
| `micrometer-tracing-bridge-otel` y `opentelemetry-exporter-otlp` en los 8 servicios | [`build.gradle.kts`](../../build.gradle.kts) raíz |
| Muestreo y destino OTLP por variable (`TRACING_SAMPLING`, `OTLP_TRACING_ENDPOINT`) | `management.tracing` y `management.otlp` en cada `application.yml` |
| La traza cruza Kafka: el *producer* escribe la cabecera W3C `traceparent` en el mensaje y el *consumer* continúa la traza | `spring.kafka.template.observation-enabled` y `spring.kafka.listener.observation-enabled` en form, identity, promotion y notification |
| La traza cruza HTTP entre servicios (auth → identity, dashboard → promotion, gateway → promotion) | Automático: los clientes se construyen con `RestTemplateBuilder` (HU-17, HU-19), que ya viene instrumentado |
| Jaeger (recibe OTLP en el puerto 4318) | [`compose/observability.yml`](../../deploy/compose/observability.yml) |

**Muestreo:** en la base el muestreo es 0. Los logs igual llevan `trace_id` (sirve para correlacionar), pero no se exporta nada ni se intenta conectar a un Jaeger que no existe. El overlay de observabilidad (y en la nube, la plataforma) fija `TRACING_SAMPLING=1.0` y el destino. En producción con tráfico real conviene un valor menor, por ejemplo 0,1.

## Logs: JSON con `trace_id` → Promtail → Loki → Grafana

Los 8 servicios usan el mismo [`config/logging/logback-spring.xml`](../../config/logging/logback-spring.xml) (el build raíz lo agrega a los recursos de cada uno). Cada evento es una línea JSON:

```json
{"timestamp":"…","message":"Promoting user … to SUSPECT due to symptoms","logger_name":"…SurveyListener",
 "thread_name":"…","level":"INFO","service":"circleguard-promotion-service",
 "trace_id":"4bf92f3577b34da6a3ce929d0e0e4736","span_id":"00f067aa0ba902b7"}
```

- `trace_id` y `span_id` siguen W3C Trace Context: son los mismos que viajan en `traceparent` y los que muestra Jaeger.
- Para leer los logs como texto en local: `SPRING_PROFILES_ACTIVE=plain-logs`.
- [Promtail](../../deploy/observability/promtail/promtail.yml) lee los logs de los contenedores por el socket de Docker (solo lectura) y los envía a [Loki](../../deploy/observability/loki/loki.yml) con etiquetas de baja cardinalidad: servicio, nivel y servicio de compose. El `trace_id` **no** es etiqueta (crearía una serie por petición): queda en la línea y se filtra con `|= "<trace_id>"`.

## Del log a la traza, y de la traza a los logs

Las dos fuentes de Grafana están enlazadas ([`trazas-logs.yml`](../../deploy/observability/grafana/provisioning/datasources/trazas-logs.yml)):

- **Loki → Jaeger:** en cada línea con `"trace_id":"…"`, el campo derivado `trace_id` abre la traza en Jaeger.
- **Jaeger → Loki:** desde un span, «Logs for this span» abre Loki con las líneas de ese `trace_id`.

## En Kubernetes

Jaeger, Loki y Promtail se despliegan en el namespace `monitoring`: la red de la aplicación nace cerrada para la **entrada** (HU-23), pero la **salida** está libre, así que los pods pueden enviar OTLP a `monitoring`. En los `values` del chart solo hay que fijar `TRACING_SAMPLING` y `OTLP_TRACING_ENDPOINT` en el ConfigMap. En Kubernetes, Promtail lee `/var/log/pods` en cada nodo (DaemonSet) en vez del socket de Docker.

## Cómo verlo en local

```bash
./deploy/compose/compose.sh dev -f deploy/compose/observability.yml up -d --wait
bash docs/evidencias/sprint-2/hu-22-traza.sh          # cuestionario con síntomas y búsqueda de su traza y sus logs
# Jaeger: http://localhost:16686 · Grafana › Explore › Loki: {service="circleguard-promotion-service"}
```
