# Patrón de rendimiento y consistencia: Cache-aside del estado de salud

Historia: [HU-19 (#26)](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/26) · Evidencia: [`hu-19-cache-aside.md`](../evidencias/sprint-2/hu-19-cache-aside.md)

## El problema

En la puerta, el gateway decide si alguien entra leyendo su estado en Redis (`user:status:<anonymousId>`). Redis no es la fuente de verdad: promotion guarda el estado en Neo4j y copia el resultado en Redis cuando cambia. El gateway trataba una clave **ausente** como "sin riesgo":

```java
String status = redisTemplate.opsForValue().get("user:status:" + anonymousId);
if (status != null && FENCED_STATUSES.contains(status)) { ... RED ... }
return GREEN;   // sin clave = entra
```

Y la clave falta más seguido de lo que parece:

- El Redis del compose corre **sin persistencia** (`--save "" --appendonly no`). Un reinicio, un despliegue o un rollback borran todos los estados.
- En Kubernetes, Redis no tiene volumen: si el pod se reprograma, empieza vacío.
- `RECOVERED` se guarda con 30 días de vencimiento.

**Consecuencia, comprobada en el compose de dev con el código de `main`:** se confirma un caso (Neo4j `CONFIRMED`, puerta en ROJO), se reinicia Redis y la misma persona pasa la puerta en **VERDE**. En un sistema de cerco epidemiológico, el fallo abría la puerta (*fail-open*).

## La solución: cache-aside

El gateway trata a Redis como lo que es, una caché, y sabe ir a la fuente de verdad cuando no tiene el dato:

```
validar QR ──► Redis tiene el estado? ──sí──► decidir
                     │ no (o Redis caído)
                     ▼
          promotion GET /internal/v1/users/{id}/status  (lee Neo4j)
                     │ ok                          │ falla o tarda > 2 s
                     ▼                             ▼
     Redis SET NX con TTL 5 min ──► decidir     ROJO "Health status unavailable" (falla cerrada)
```

| Pieza | Dónde |
|---|---|
| Lectura de la caché y, si falta, de la fuente de verdad | [`QrValidationService.java:82`](../../services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/service/QrValidationService.java#L82) |
| Redis caído se trata como fallo de caché, no como error | [`QrValidationService.java:96`](../../services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/service/QrValidationService.java#L96) |
| Escritura con `SET NX` + TTL | [`QrValidationService.java:105-110`](../../services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/service/QrValidationService.java#L105-L110) |
| Falla cerrada si no hay ni caché ni fuente de verdad | [`QrValidationService.java:72`](../../services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/service/QrValidationService.java#L72) |
| Cliente de promotion con timeout de 2 s | [`PromotionStatusClient.java`](../../services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/client/PromotionStatusClient.java) |
| Ruta de promotion que lee el grafo (`ACTIVE` si la persona no está en el grafo) | [`InternalStatusController.java:29`](../../services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/controller/InternalStatusController.java#L29), consulta en [`UserNodeRepository.java:40`](../../services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/repository/graph/UserNodeRepository.java#L40) |
| La ruta `/internal/` no se publica por el nginx (404) | [`default.conf.template:42`](../../mobile/nginx/default.conf.template#L42) |
| Configuración (`PROMOTION_URL`, `PROMOTION_TIMEOUT`, `STATUS_CACHE_TTL`) | [`gateway/application.yml`](../../services/circleguard-gateway-service/src/main/resources/application.yml) |

### Decisiones de consistencia

- **`SET NX` (solo si no existe):** si promotion escribe un estado nuevo entre la consulta del gateway y la escritura, gana el de promotion, que es más reciente. El gateway nunca pisa un valor puesto por promotion.
- **TTL de 5 minutos solo en lo que escribe el gateway:** acota cuánto puede vivir un valor leído por el gateway. Lo que escribe promotion no cambia (no vence, salvo `RECOVERED`). En la práctica, promotion sobrescribe la misma clave en cuanto el estado cambia, así que el TTL es una red de seguridad y no el mecanismo principal.
- **Falla cerrada:** si Redis no tiene el dato y promotion no responde, no se puede saber si la persona está en cerco, así que se niega el paso con un mensaje distinto ("Health status unavailable, try again") para que el guardia sepa que no es un positivo.

## Trade-offs

| A favor | En contra |
|---|---|
| Corrige el *fail-open*: perder Redis ya no abre la puerta a los casos en cerco | La puerta depende de promotion cuando Redis no tiene el dato: si los dos fallan a la vez, nadie entra (falla cerrada) |
| Redis puede reiniciarse o vaciarse sin migrar ni "recalentar" nada: se llena solo con el uso | La primera validación de cada persona tras perder Redis es más lenta (una llamada HTTP + Neo4j) |
| Las personas que nunca cambiaron de estado (la mayoría) también quedan en caché, y promotion recibe una llamada cada 5 min por persona y no una por validación | Hay dos escritores de la misma clave (promotion y gateway); `SET NX` y el TTL lo hacen seguro, pero hay que entenderlo para mantenerlo |
| Redis caído deja de ser un error: el gateway va directo a la fuente de verdad | Se agrega una ruta interna en promotion que hay que proteger (hoy: no publicada por el nginx; con RBAC y NetworkPolicies en HU-23) |

**Alternativas descartadas:** dar persistencia a Redis (AOF) solo reduce el problema: un Redis nuevo o vaciado sigue abriendo la puerta. *Read-through* con `@Cacheable` en el gateway serviría para la lectura, pero no controla el `SET NX` ni la falla cerrada. Y *Outbox* en form (la otra opción del issue) resuelve la pérdida de eventos, no este hueco de seguridad en la puerta.

## Cómo se prueba

- **Unitarias** ([`QrValidationServiceTest`](../../services/circleguard-gateway-service/src/test/java/com/circleguard/gateway/service/QrValidationServiceTest.java)): caché con dato, caché sin dato (consulta y guarda con TTL), promotion caído (falla cerrada, no guarda), Redis caído (va a promotion) y QR falso (no consulta nada).
- **Integración con Redis real** ([`QrAccessIntegrationTest`](../../services/circleguard-gateway-service/src/test/java/com/circleguard/gateway/integration/QrAccessIntegrationTest.java)): `FLUSHALL` sobre un caso confirmado sigue dando ROJO; la segunda validación sale de Redis sin llamar a promotion; un estado escrito por promotion no se pisa ni recibe TTL; sin caché y sin promotion, falla cerrada.
- **Integración con Neo4j real** ([`SurveySubmittedIntegrationTest`](../../services/circleguard-promotion-service/src/test/java/com/circleguard/promotion/integration/SurveySubmittedIntegrationTest.java)): la ruta interna devuelve el estado del grafo tras el cuestionario, y `ACTIVE` para quien no está en el grafo.
- **Sistema real:** antes y después en el compose de dev (ver la evidencia).
