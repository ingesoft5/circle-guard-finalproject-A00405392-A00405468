# HU-21 · Alertas críticas con Alertmanager: evidencia

Historia: [#28](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/28) · Diseño y runbook: [`docs/observabilidad/alertas.md`](../../observabilidad/alertas.md) · Fecha: 21 de noviembre de 2026

## Criterios de aceptación

| Criterio | Estado | Evidencia |
|---|---|---|
| Al menos 3 alertas críticas (servicio caído, p95 alto, lag de Kafka) | ✅ | 4 alertas en [`alertas.yml`](../../deploy/observability/prometheus/alertas.yml): `ServicioCaido`, `LatenciaP95Alta`, `LagKafkaAlto` y `CircuitoAbierto` (el estado del Circuit Breaker de HU-17) |
| Cada alerta probada provocando la falla, con evidencia | ✅ | Las 4 se dispararon y se resolvieron en el compose de dev con [`hu-21-provocar-alertas.sh`](hu-21-provocar-alertas.sh) ([corrida](hu-21-alertas-corrida.txt)) |
| Cada alerta disparada a propósito, con captura de Alertmanager y de la notificación | ✅ | Capturas de Alertmanager con cada alerta activa, más el log del receptor con cada `DISPARADA` y `RESUELTA` (abajo) |

## 1. Pruebas de las reglas

`promtool check rules alertas.yml`: 4 reglas válidas. `promtool test rules alertas.test.yml`: **SUCCESS**. Cada alerta tiene un caso donde **no** debe dispararse todavía (antes del `for`, o un valor por debajo del umbral) y otro donde sí, con el texto exacto de la notificación.

## 2. Fallas provocadas en el sistema real

Compose de dev con `observability.yml`: Prometheus, Alertmanager, el receptor `alert-sink` y `kafka-exporter`. Antes de cada escenario, el script espera a que la alerta esté **inactiva**, para que la evidencia corresponda a esa falla y no a la anterior.

| Alerta | Falla provocada | Disparada | Resuelta | Captura |
|---|---|---|---|---|
| `CircuitoAbierto` | `compose stop identity` con logins cada 2 s | a los 68 s (08:12:01) | 47 s después de encender identity | [identity](hu-21-alertmanager-identity.png) |
| `ServicioCaido` | la misma | a los 102 s (08:12:35; exige 1 min sin respuesta) | 47 s después de encender identity | [identity](hu-21-alertmanager-identity.png) |
| `LatenciaP95Alta` | `docker pause` de Postgres con logins cada 2 s | a los 3 min 5 s (08:33:17; exige 2 min por encima de 1 s), p95 = **30 s** | 2 min 34 s después de reanudar Postgres | [latencia](hu-21-alertmanager-latencia.png) |
| `LagKafkaAlto` | `compose stop promotion` y 150 cuestionarios | a los 100 s (08:37:47): «promotion-service-group lleva **150** mensajes de atraso en survey.submitted» | 42 s después de encender promotion (procesó lo acumulado) | [kafka](hu-21-alertmanager-kafka.png) |

Notificaciones que recibió el receptor (UTC; hora local = UTC−5), tomadas del log de `alert-sink`:

```
[13:12:00Z] DISPARADA CircuitoAbierto (critical): Circuito identity abierto en circleguard-auth-service
[13:12:30Z] DISPARADA ServicioCaido (critical): identity:8083 no responde
[13:13:15Z] DISPARADA LatenciaP95Alta (critical): p95 de circleguard-auth-service en 5.226s
[13:14:30Z] RESUELTA ServicioCaido (critical): identity:8083 no responde
[13:15:00Z] RESUELTA CircuitoAbierto (critical): Circuito identity abierto en circleguard-auth-service
...
[13:33:15Z] DISPARADA LatenciaP95Alta (critical): p95 de circleguard-auth-service en 30s
[13:36:15Z] RESUELTA LatenciaP95Alta (critical): p95 de circleguard-auth-service en 24.24s
[13:37:45Z] DISPARADA ServicioCaido (critical): promotion:8088 no responde
[13:37:45Z] DISPARADA LagKafkaAlto (critical): promotion-service-group lleva 150 mensajes de atraso en survey.submitted
[13:39:45Z] RESUELTA ServicioCaido (critical): promotion:8088 no responde
[13:39:45Z] RESUELTA LagKafkaAlto (critical): promotion-service-group lleva 150 mensajes de atraso en survey.submitted
```

## 3. Hallazgos

- **Una caída, tres alertas.** Con identity caído sonaron `ServicioCaido` (identity no responde), `CircuitoAbierto` (auth dejó de llamarlo) y `LatenciaP95Alta` en auth: el p95 subió a 5,2 s por los reintentos y la espera del timeout antes de que el circuito se abriera. Cada alerta cuenta una parte distinta del problema; es la demo de la falla provocada que pide la sustentación.
- **Una evidencia que habría engañado.** En la primera corrida, `LatenciaP95Alta` "se disparó" en el mismo segundo en que se congeló Postgres. No podía ser por Postgres, porque la alerta exige 2 minutos: seguía activa por la caída de identity del escenario anterior. Se agregó al script una guarda (cada escenario espera a que su alerta esté inactiva) y se repitió: con la guarda, se disparó 3 minutos después de congelar Postgres.
- **El lag de un grupo que nunca confirmó un offset no se mide.** Con el tópico recién creado y sin ningún mensaje procesado, `kafka-exporter` no publica lag para `promotion-service-group`, y la primera prueba de `LagKafkaAlto` no se disparó. En un sistema que ya procesó eventos el offset existe y la alerta funciona, como mostró la repetición. Queda anotado en el runbook.
- **Un error del propio script.** Se editó el script mientras corría, y bash, que lo va leyendo a medida que avanza, repitió un escenario y terminó con un error de sintaxis. La corrida válida se hizo desde una copia sin cambios.
