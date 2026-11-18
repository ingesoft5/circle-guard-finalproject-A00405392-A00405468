# HU-19 · Cache-aside en gateway y patrones existentes: evidencia

Historia: [#26](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/26) · Diseño y trade-offs: [`cache-aside.md`](../../patrones/cache-aside.md) · Patrones existentes: [`existentes.md`](../../patrones/existentes.md) · Fecha: 17 de noviembre de 2026

## Criterios de aceptación

| Criterio | Estado | Evidencia |
|---|---|---|
| Cache-aside en `gateway` implementado y probado | ✅ | Pruebas unitarias, de integración (Redis y Neo4j reales) y antes/después en el sistema real (abajo) |
| Patrones existentes documentados con evidencia en el código (database-per-service, event-driven, state machine, API Gateway) | ✅ | [`existentes.md`](../../patrones/existentes.md): los 4 pedidos y uno más (pseudonimización), con archivo y línea, trade-offs y qué mejorar. Cierra también el criterio que quedó pendiente en HU-17 (#24) |

## 1. Pruebas

`gradle :services:circleguard-gateway-service:test :services:circleguard-gateway-service:integrationTest :services:circleguard-promotion-service:test :services:circleguard-promotion-service:integrationTest`, con Testcontainers:

| Servicio | Unitarias | Integración | Fallas |
|---|---|---|---|
| gateway | 8 (5 nuevas del cache-aside) | 11 (4 nuevas) | 0 |
| promotion | 39 (2 nuevas: `InternalStatusControllerTest`) | 2 (la de `survey.submitted` ahora también consulta la ruta interna contra Neo4j real) | 0 |

Las verificaciones de cobertura mínima del 60 % de los dos servicios siguen pasando.

| Prueba nueva de integración (gateway, Redis real) | Qué demuestra |
|---|---|
| `fencedUserIsStillDeniedAfterRedisLosesItsData` | `FLUSHALL` sobre un caso confirmado: la puerta sigue en ROJO (antes: VERDE) |
| `cacheMissAsksPromotionAndStoresTheStatusInRedis` | Sin dato en Redis se consulta a promotion, se guarda con TTL y la segunda validación ya no llama a promotion |
| `statusWrittenByPromotionIsNotOverwrittenByTheGateway` | Con el dato de promotion en Redis no se llama a promotion, y la clave no recibe TTL |
| `withoutRedisDataAndWithPromotionDownTheGateFailsClosed` | Sin caché y sin fuente de verdad: ROJO con "Health status unavailable", y no se guarda nada |

## 2. Antes y después en el sistema real

Compose de dev con volúmenes limpios. El script [`hu-19-demo.sh`](hu-19-demo.sh) confirma el caso de `health_user` con la API real de promotion (`POST /api/v1/health/confirmed`, como `super_admin`, que tiene `ROLE_HEALTH_CENTER`), valida su QR en la puerta, reinicia Redis (en el compose no tiene persistencia) y vuelve a validar. Salida completa: [`hu-19-antes-despues.txt`](hu-19-antes-despues.txt).

| Paso | Antes (imágenes de `main`, 6c0f71b) | Después (esta rama) |
|---|---|---|
| Caso confirmado: Neo4j `CONFIRMED`, Redis `CONFIRMED` | ROJO "Health Risk Detected" | ROJO "Health Risk Detected" |
| Se reinicia Redis: Neo4j `CONFIRMED`, Redis vacío | **VERDE "Welcome to Campus"** | **ROJO "Health Risk Detected"** |
| Redis después de validar | vacío | `CONFIRMED`, TTL 299 s (lo repuso el gateway) |
| `GET /api/promotion/internal/v1/users/<id>/status` desde afuera | 404 (la ruta no existía) | 404 (la bloquea el nginx); dentro de la red, `web → promotion:8088` responde `{"status":"ACTIVE",…}` |

Log del gateway en el paso 2 (después):

```
DEBUG … c.c.gateway.service.QrValidationService : Estado de 9d55564c-… no estaba en Redis; promotion respondió CONFIRMED
```

Con las imágenes de esta rama, los **E2E de Playwright pasan 6/6** contra el mismo compose (`tests/e2e-playwright/run.sh http://localhost:8090`).

## 3. Hallazgos

- **El hueco de seguridad (fail-open):** con el código de `main`, perder Redis dejaba pasar a los casos confirmados. En el compose, Redis corre sin persistencia, así que basta un reinicio o un despliegue.
- **Al preparar la demostración, la primera corrida usó imágenes locales viejas** (anteriores a la corrección de la consulta Cypher del Sprint 1): promotion actualizaba Neo4j pero no escribía en Redis el estado de la persona confirmada, y la puerta la dejaba pasar incluso sin reiniciar Redis. Se reconstruyeron las imágenes desde `main` para que el "antes" fuera el código actual. Con cache-aside, ese tipo de desajuste entre Neo4j y Redis tampoco abriría la puerta.
- **Eventos sin consumidor:** al documentar los patrones existentes se encontró que `audit.identity.accessed`, `notification.audit` y `proximity.detected` se publican y nadie los consume (anotado en [`existentes.md`](../../patrones/existentes.md)).
