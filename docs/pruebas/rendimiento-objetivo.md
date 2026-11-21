# Objetivo de rendimiento (declarado antes de medir)

Historia: [HU-24 (#31)](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/31) · Resultados: [`informe-calidad.md`](informe-calidad.md)

Este documento se escribió y se versionó **antes** de correr la primera prueba de carga: el commit que lo agrega es anterior a los resultados. Así el informe compara contra una meta fijada de antemano y no contra lo que salió.

## De dónde salen los números

| Supuesto | Valor | Razón |
|---|---|---|
| Personas que entran al campus en un día | 5 000 | Orden de magnitud de una universidad mediana |
| Hora pico de entrada | 7:00 a 8:00, con el 60 % concentrado en 15 min | Primera clase del día |
| Check-ins en el pico | 5 000 × 0,6 / 900 s ≈ **3,3 por segundo** | |
| Cuestionarios en el pico | Se responden antes de salir de casa: 5 000 en 1 h ≈ **1,4 por segundo** | |
| Margen de diseño | **×10** (picos, varias puertas a la vez, reintentos) | |

## Objetivo

| Flujo (por el nginx del web, como un cliente real) | Throughput sostenido | Latencia p95 | Tasa de error |
|---|---|---|---|
| **Validación de acceso en la puerta** `POST /api/gateway/api/v1/gate/validate` | **≥ 33 req/s** | **≤ 300 ms** | **< 1 %** |
| **Envío del cuestionario** `POST /api/form/api/v1/surveys` | **≥ 14 req/s** | **≤ 800 ms** | **< 1 %** |

**Por qué estas latencias:**
- **300 ms en la puerta:** el guardia escanea y la persona sigue caminando. Por encima de ~0,3 s la espera se nota y se forma fila.
- **800 ms en el cuestionario:** la persona lo envía desde el celular una vez al día. Debe sentirse inmediato, pero es menos crítico que la puerta.

## Cómo se mide

- **Herramienta:** Locust (`tests/performance/locustfile.py`), en un contenedor de la misma red, contra el nginx del compose de dev.
- **Perfil:** 60 usuarios virtuales, 50 en la puerta y 10 en el cuestionario. Rampa de 30 s y 3 min sostenidos. Cada usuario de puerta pide su QR a auth al empezar (y lo renueva antes de que venza) y luego valida en ciclo con pausas de 0,5 a 1,5 s; cada usuario de cuestionario envía uno con pausas de 0,5 a 1,5 s, un tercio con síntomas.
- **Se mide del lado del cliente** (Locust: percentiles, req/s y errores por endpoint). En paralelo se miran los dashboards de HU-20 para ubicar el cuello de botella.

## Limitaciones conocidas de antemano

- **Todo corre en un solo portátil** (los servicios, Postgres, Neo4j, Kafka, Redis y el propio Locust): la máquina comparte CPU entre quien genera la carga y quien la atiende. Si se cumple aquí, en la nube (un nodo por ambiente, HU-14) hay margen. Si no se cumple, el informe separa lo que es del código de lo que es de la máquina.
- **Una sola réplica por servicio** (el HPA de HU-15 no actúa en el compose).
