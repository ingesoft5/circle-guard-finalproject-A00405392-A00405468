# Chaos engineering (HU-29, bono)

Qué rompimos a propósito, qué esperábamos que pasara, qué pasó de verdad y qué cambiamos por lo que vimos. No usamos Chaos Mesh ni Litmus: el cluster de cada ambiente es un solo nodo k3s y el stack de observabilidad vive en Compose, así que inyectamos las fallas con `kubectl scale`, `docker stop`, `docker pause` y reiniciando contenedores. Todas son reversibles y el script de Kubernetes devuelve el número original de réplicas aunque algo falle. Lo que no probamos (latencia o pérdida de paquetes de red, por ejemplo) está en el último apartado.

## Cómo se plantea cada experimento

1. **Estado estable.** Qué significa «sano»: el login responde 200 en menos de un segundo, la sonda de readiness de los 5 servicios núcleo da 200, ninguna alerta está activa y el atraso de `survey.submitted` en Kafka es cero.
2. **Hipótesis.** Qué debería pasar al inyectar la falla.
3. **Inyección.** Un comando, reversible.
4. **Observación.** Respuestas, tiempos, alertas y trazas, con la hora de cada cosa.
5. **Veredicto y lo aprendido.** Si la hipótesis se cumplió y qué cambió en el código o en la prueba.

## Los cinco experimentos

| # | Falla inyectada | Dónde | Hipótesis | Resultado | Evidencia |
|---|---|---|---|---|---|
| 1 | `identity` a cero réplicas | k3s real, mismo chart y versión que Terraform | El login degrada con un 503 claro y no se cuelga; vuelve solo al volver identity | **Se cumplió.** Primer intento 503 en 7,4 s (los reintentos esperan); los siguientes 503 en ~0,7 s con el circuito abierto y `Retry-After: 10`. Al volver identity, login 200 en 17 s, sin reiniciar auth (`RESTARTS 0`) | [`hu-27-ensayo-falla-provocada.txt`](../evidencias/sprint-2/hu-27-ensayo-falla-provocada.txt) |
| 2 | `docker compose stop identity`, con logins cada 2 s | Compose de dev con Prometheus y Alertmanager | Las alertas de circuito abierto y de servicio caído se disparan y se resuelven solas | **Se cumplió.** `CircuitoAbierto` a los 68 s, `ServicioCaido` a los 102 s; resueltas 47 s después de volver. Además sonó `LatenciaP95Alta` en auth: el p95 subió a 5,2 s por los reintentos, una caída produjo tres alertas | [`hu-21-alertas-corrida.txt`](../evidencias/sprint-2/hu-21-alertas-corrida.txt) |
| 3 | `docker pause` de PostgreSQL con logins cada 2 s | Compose de dev | La latencia p95 de login se dispara y la alerta de latencia salta | **Se cumplió.** `LatenciaP95Alta` a los 3 min 5 s (exige 2 min por encima de 1 s), con p95 de 30 s; resuelta 2 min 34 s después de reanudar | idem |
| 4 | `promotion` detenido y 150 cuestionarios enviados | Compose de dev | Kafka retiene los mensajes (no se pierde ninguno) y la alerta de atraso se dispara | **Se cumplió.** `LagKafkaAlto` a los 100 s: «promotion-service-group lleva 150 mensajes de atraso». Al volver promotion procesó lo acumulado y la alerta se resolvió 42 s después | idem |
| 5 | Reiniciar Redis (sin persistencia) con un caso confirmado | Compose de dev, antes y después de HU-19 | La puerta sigue negando el acceso a una persona confirmada | **No se cumplió antes del cambio.** Con el código de `main`, el caso confirmado pasaba la puerta en verde tras reiniciar Redis. Con cache-aside que falla cerrado, la puerta sigue en rojo y repone el estado en Redis (TTL 299 s) | [`hu-19-antes-despues.txt`](../evidencias/sprint-2/hu-19-antes-despues.txt) |

## Lo que cambió por culpa de los experimentos

- **El experimento 5 encontró un fallo de seguridad real** que ninguna prueba unitaria había visto: cuando Redis perdía los datos, el gateway dejaba pasar a una persona con contagio confirmado. Se arregló en HU-19: el gateway consulta a promotion (la fuente de verdad), repone Redis y, si no puede confirmar el estado, **niega** el acceso.
- **El experimento 1 definió el diseño del login.** Inventar un identificador anónimo cuando identity no responde rompería la privacidad, así que el login no tiene *fallback*: devuelve 503 con `Retry-After`. El dashboard, en cambio, sí responde con el último dato y la marca `stale: true`.
- **Los experimentos 2 a 4 afinaron las alertas.** Una primera corrida de `LatenciaP95Alta` «se disparó» en el mismo segundo en que se congeló PostgreSQL; no podía ser por eso, porque la alerta exige dos minutos: seguía activa por la caída de identity del escenario anterior. Desde entonces el script espera a que la alerta esté inactiva antes de provocar cada falla. Además, `kafka-exporter` no publica el atraso de un grupo que nunca confirmó un offset, y la primera prueba de `LagKafkaAlto` no se disparó; se corrigió la forma de provocarlo (ver `hu-21-alertas.md`).

## Cómo repetirlos

```bash
# Experimento 1 (Kubernetes): apaga identity, mide el login y restaura siempre las réplicas
KUBECONFIG_FILE=<ruta absoluta> BASE=http://127.0.0.1:8088 NS=circleguard-dev ./scripts/chaos/identidad-caida.sh

# Experimentos 2 a 4 (Compose con observabilidad): provoca cada alerta y espera a que se resuelva
docs/evidencias/sprint-2/hu-21-provocar-alertas.sh

# Experimento 5 (cache-aside): confirmar un caso, reiniciar Redis y validar en la puerta
docs/evidencias/sprint-2/hu-19-demo.sh
```

## Lo que no hicimos

- **Sin una herramienta de caos** (Chaos Mesh o Litmus). La inyección es manual y no hay un catálogo de experimentos con calendario.
- **Nada de red:** no probamos latencia, pérdida de paquetes ni particiones entre servicios. Es la falla que más habría tensado el Circuit Breaker y los *timeouts*.
- **Un solo nodo por ambiente.** No probamos qué pasa cuando muere el nodo entero, ni el escalado del HPA durante una falla.
- **Nada de esto se ejecutó en AWS.** El experimento 1 se hizo en un k3s local de la misma versión que instala Terraform; repetirlo en un nodo de la nube está en el ensayo de la sustentación (el script acepta `BASE` de un ambiente real).
- **Sin automatizar.** No hay un job de Jenkins que corra los experimentos de forma periódica; son una práctica manual y documentada.
