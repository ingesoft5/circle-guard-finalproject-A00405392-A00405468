# Alertas críticas con Alertmanager

Historia: [HU-21 (#28)](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/28) · Evidencia: [`hu-21-alertas.md`](../evidencias/sprint-2/hu-21-alertas.md) · Métricas en las que se apoyan: [`metricas.md`](metricas.md)

## Cómo fluye una alerta

```
servicios ──/actuator/prometheus──┐
kafka-exporter (lag desde el broker)┤──► Prometheus ──evalúa alertas.yml──► Alertmanager ──agrupa──► receptor
                                                                                                (compose: alert-sink;
                                                                                                 nube: Discord/WhatsApp)
```

| Archivo | Qué es |
|---|---|
| [`prometheus/alertas.yml`](../../deploy/observability/prometheus/alertas.yml) | Las 4 reglas, con resumen, descripción y enlace a esta guía |
| [`prometheus/tests/alertas.test.yml`](../../deploy/observability/prometheus/tests/alertas.test.yml) | Pruebas unitarias con `promtool test rules`: cada alerta dispara cuando debe y **no** antes, con el texto exacto de la notificación |
| [`alertmanager/alertmanager.yml`](../../deploy/observability/alertmanager/alertmanager.yml) | Agrupa por alerta y servicio, espera 10 s para juntar alertas relacionadas, repite cada hora y avisa también cuando se resuelve |
| [`alert-sink/sink.py`](../../deploy/observability/alert-sink/sink.py) | Receptor del compose: imprime cada notificación (`DISPARADA` / `RESUELTA`) |
| [`compose/observability.yml`](../../deploy/compose/observability.yml) | Levanta Prometheus, Grafana, Alertmanager, el receptor y `kafka-exporter` junto al ambiente |

Para la nube: el mismo `alertmanager.yml` con un receptor `discord_configs` (o `webhook_configs` hacia el servicio de WhatsApp que ya usa el pipeline). La URL del webhook se lee de un archivo montado (`webhook_url_file`) y nunca va en el repositorio.

## Las alertas

### ServicioCaido

- **Condición:** `up{job="circleguard"} == 0` durante 1 minuto. Prometheus no puede leer las métricas del servicio.
- **Por qué 1 minuto:** un reinicio normal (Kubernetes o compose) tarda menos; más tiempo ya es una caída.
- **Qué hacer:** revisar el contenedor o pod (`docker ps`, `kubectl get pods -n circleguard-<ambiente>`) y sus logs. Si es identity, el login queda degradado (503) gracias al Circuit Breaker. Si es promotion, la puerta sigue funcionando con Redis, y con el cache-aside de HU-19 falla cerrada cuando no tiene el estado.

### CircuitoAbierto

- **Condición:** un circuito de Resilience4j (`identity` en auth, `promotion` en dashboard) abierto durante 30 s.
- **Por qué aparte de ServicioCaido:** un servicio puede estar "vivo" para Prometheus y aun así fallar o tardar en sus respuestas. Esta alerta mide lo que vive el usuario: auth dejó de llamar a identity y el login responde 503.
- **Qué hacer:** ver la causa en identity o promotion (logs, latencia en el dashboard de servicios). El circuito se cierra solo cuando el servicio vuelve (semiabierto a los 10 s, cerrado tras 2 llamadas buenas); no hay que reiniciar auth.

### LatenciaP95Alta

- **Condición:** p95 de las peticiones de un servicio (sin `/actuator`) por encima de 1 s durante 2 minutos.
- **Por qué 1 s y 2 minutos:** en condiciones normales el p95 está entre 6 y 120 ms (HU-20). Un pico aislado no alerta; dos minutos seguidos sí.
- **Qué hacer:** en el dashboard de servicios, ver qué rutas son las lentas. Lo más común es que se haya atascado una dependencia (base de datos, Neo4j, Kafka). En la prueba se congeló Postgres: las peticiones esperaban el timeout de conexión.

### LagKafkaAlto

- **Condición:** un grupo de consumidores acumula más de 100 mensajes sin procesar en un tópico durante 1 minuto (`kafka_consumergroup_lag`, medido desde el broker por `kafka-exporter`).
- **Por qué importa:** si `promotion-service-group` no consume `survey.submitted`, las personas que reportan síntomas no entran al cerco. El sistema parece funcionar, porque form responde 200, pero la contención no ocurre.
- **Por qué desde el broker:** un consumidor detenido no puede reportar su propio atraso. Por eso no basta con las métricas del servicio.
- **Qué hacer:** revisar que el consumidor esté arriba y sin errores en sus logs. Al volver, procesa lo acumulado: el lag baja solo y la alerta se resuelve.
- **Límite conocido:** si el grupo nunca confirmó un offset (tópico recién creado y ningún mensaje procesado), `kafka-exporter` no puede calcular el lag y la alerta no suena. En un sistema que ya procesó eventos no pasa. Para cubrir ese caso, `ServicioCaido` avisa igual si el consumidor está caído.

## Cómo probarlas

```bash
# Reglas: pruebas unitarias
docker run --rm -v "$PWD/deploy/observability/prometheus:/p" -w /p/tests --entrypoint promtool prom/prometheus:v2.55.1 test rules alertas.test.yml

# En el sistema real: provoca cada falla, espera la alerta y su resolución
./deploy/compose/compose.sh dev -f deploy/compose/observability.yml up -d --wait
bash docs/evidencias/sprint-2/hu-21-provocar-alertas.sh todas      # o identity | latencia | kafka
# Alertmanager: http://localhost:9093 · notificaciones: compose logs alert-sink
```
