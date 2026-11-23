# HU-22 · Trazas con Jaeger y logs con Loki: evidencia

Historia: [#29](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/29) · Diseño: [`docs/observabilidad/trazas-logs.md`](../../observabilidad/trazas-logs.md) · Fecha: 21 de noviembre de 2026

## Criterios de aceptación

| Criterio | Estado | Evidencia |
|---|---|---|
| Traza de punta a punta `form → Kafka → promotion → notification` en Jaeger (OpenTelemetry) | ✅ | 5 spans en 3 servicios con el mismo `trace_id`, cruzando Kafka dos veces ([captura](hu-22-jaeger-traza.png), [corrida](hu-22-traza-corrida.txt)) |
| Logs de todos los servicios en Loki, consultables desde Grafana | ✅ | Promtail envía a Loki los logs de todos los contenedores del compose; en Grafana › Explore › Loki se filtran por servicio, nivel o `trace_id` ([captura](hu-22-grafana-loki.png)) |
| Logs en JSON con `trace_id` (W3C Trace Context) para saltar del log a la traza | ✅ | Los 8 servicios escriben JSON con `trace_id` y `span_id`. Loki tiene el campo derivado `trace_id` que abre la traza en Jaeger, y Jaeger el enlace inverso a los logs |

## 1. La traza

El script [`hu-22-traza.sh`](hu-22-traza.sh) crea una persona con dos contactos en el grafo y envía su cuestionario con síntomas por el nginx con una cabecera W3C propia, `traceparent: 00-<trace_id>-<span_id>-01`. Así se sabe exactamente qué traza buscar. Luego consulta esa traza en la API de Jaeger y sus logs en Loki (a través de Grafana).

```
5 spans en la traza 661e8ffbfe986c56d353710c9e0a71ea:
  +     0.0 ms      11.5 ms  circleguard-form-service          http post /api/v1/surveys
  +     6.1 ms       2.7 ms  circleguard-form-service          survey.submitted send
  +    10.3 ms      41.3 ms  circleguard-promotion-service     survey.submitted receive
  +    26.0 ms       2.7 ms  circleguard-promotion-service     promotion.status.changed send
  +    29.9 ms       6.7 ms  circleguard-notification-service  promotion.status.changed receive
```

- **La traza cruza Kafka dos veces sin perderse:** el `traceparent` viaja en las cabeceras de cada mensaje (`observation-enabled` en el *producer* y en el *consumer*).
- **Toda la cadena tarda 37 ms** con el sistema caliente, desde que llega el cuestionario hasta que notification despacha el aviso. Casi todo el tiempo está en promotion (41 ms, la propagación del cerco en Neo4j y Redis).
- **En frío tardó 3 s:** en la primera traza después de arrancar, `survey.submitted receive` duró 2 445 ms (primera consulta a Neo4j y arranque del productor de Kafka de promotion). Es la misma diferencia entre frío y caliente que midió el tiempo de contención en HU-20 (5,5 s contra 0,6 s), ahora con el lugar exacto donde se pierde el tiempo.

## 2. Los logs de esa traza

7 líneas de los servicios de CircleGuard con el mismo `trace_id`, en orden y de dos servicios distintos:

```
circleguard-promotion-service    INFO  span=a6ca740dd77660c1  Received survey submission event: {anonymousId=de88f74a-…
circleguard-promotion-service    INFO  span=a6ca740dd77660c1  Promoting user de88f74a-… to SUSPECT due to symptoms
circleguard-promotion-service    INFO  span=a6ca740dd77660c1  Updating status: de88f74a-… -> SUSPECT (Admin Override: false)
circleguard-promotion-service    INFO  span=a6ca740dd77660c1  Batch updating 3 Redis entries based on consolidated propagation
circleguard-notification-service INFO  span=ec171b6bbb21f179  Received health status change event: {"anonymousId":"de88f74a-…
circleguard-notification-service INFO  span=ec171b6bbb21f179  Dispatching contextual multi-channel notifications for user: de88f74a-…
circleguard-promotion-service    WARN  span=a6ca740dd77660c1  Neo.ClientNotification.Statement.UnknownPropertyKeyWarning: …
```

El `span_id` de cada línea coincide con el span de Jaeger que la produjo (`survey.submitted receive` en promotion, `promotion.status.changed receive` en notification).

## 3. Pruebas

Pruebas unitarias de los 8 servicios con el tracing y los logs JSON activos: auth 33, identity 17, form 21, promotion 44, gateway 10, dashboard 15, file 1 y notification 11, sin fallas y con la cobertura mínima cumplida.

## 4. Hallazgos

- **El `logback-spring.xml` compartido no llegaba a las imágenes.** Los Dockerfiles copiaban solo los archivos de Gradle y la carpeta de cada servicio, y en el primer arranque los logs salieron en texto. Se agregó `COPY config/logging` a los 8 Dockerfiles; las pruebas no lo habían detectado porque Gradle sí encuentra el archivo en el repositorio.
- **Promotion parecía no exportar sus spans.** La primera consulta encontró solo form y notification: cada servicio exporta por lotes cada ~5 s y el script consultaba apenas llegaba el primero. El script ahora espera a los tres servicios y a un lote más.
- **La búsqueda por `trace_id` traía líneas que no eran de la aplicación** (por ejemplo, el log de Grafana registrando la propia consulta). Se restringió a `{service=~"circleguard-.+"}`, la etiqueta que solo tienen los logs JSON de los servicios.
- **Un aviso de Neo4j que nadie veía:** con los logs centralizados aparece en cada promoción un `UnknownPropertyKeyWarning`. Una consulta de promotion filtra por `isAutoGenerated`, una propiedad que no existe en los nodos `Circle` del grafo. No rompe nada (`coalesce` lo trata como `false`), pero queda anotado para revisarlo.
