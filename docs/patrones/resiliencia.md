# Patrón de resiliencia: Circuit Breaker + Retry

Historia: [HU-17 (#24)](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/24) · Librería: Resilience4j 2.2.0 (`resilience4j-spring-boot3`) · Evidencia: [`hu-17-resiliencia.md`](../evidencias/sprint-2/hu-17-resiliencia.md)

## El problema

Dos servicios dependen de otro por HTTP síncrono:

| Llamada | Para qué | Qué pasaba si el otro servicio caía |
|---|---|---|
| `auth → identity` | El login pide el ID anónimo del usuario | El login respondía **500** con el mensaje interno del error, y cada intento esperaba a que la conexión fallara |
| `dashboard → promotion` | Las estadísticas de salud del campus | El `try/catch` devolvía un mapa de error, pero cada petición volvía a esperar a promotion |

Además, los dos `RestTemplate` se creaban con `new RestTemplate()`, **sin timeout**: un servicio colgado (no caído) dejaba la petición esperando indefinidamente.

## La solución

```
petición ──► Retry ──► Circuit Breaker ──► RestTemplate (timeout 2 s) ──► servicio remoto
               │              │
               │              └─ abierto: falla al instante, sin llamar (CallNotPermittedException)
               └─ agotados los intentos o circuito abierto: fallback
```

- **Timeout** (2 s de conexión y de lectura, `IDENTITY_TIMEOUT` / `PROMOTION_TIMEOUT`): convierte "colgado" en un error que el circuito puede contar.
- **Retry** (afuera): hasta 3 intentos con espera exponencial (200 ms, 400 ms). Solo reintenta caídas (`ResourceAccessException`: sin conexión o timeout) y errores 5xx (`HttpServerErrorException`). Un 4xx no se reintenta: la petición está mal y repetirla no sirve.
- **Circuit Breaker** (adentro): mira las últimas 10 llamadas; con al menos 5 y un 50 % de fallas se **abre** y deja de llamar durante 10 s (`RESILIENCE_OPEN_WAIT`). Luego pasa a **semiabierto**, deja pasar 2 llamadas de prueba y, si salen bien, se **cierra**. Solo cuentan como falla las caídas y los 5xx.
- **Fallback** (degradación):
  - auth: lanza `IdentityServiceUnavailableException` y el login responde **503** con `Retry-After: 10` y un mensaje claro. Las credenciales eran válidas: no es un 401, y tampoco un 500 con detalles internos.
  - dashboard: devuelve las estadísticas vacías marcadas con `degraded: true`. El tablero sigue funcionando sin los datos de promotion.

### Dónde está en el código

| Qué | Archivo y línea |
|---|---|
| Timeout del cliente de identity | [`IdentityClient.java:40`](../../services/circleguard-auth-service/src/main/java/com/circleguard/auth/client/IdentityClient.java#L40) |
| Retry + Circuit Breaker en `getAnonymousId` | [`IdentityClient.java:44-45`](../../services/circleguard-auth-service/src/main/java/com/circleguard/auth/client/IdentityClient.java#L44-L45) |
| Fallbacks (sin respuesta, 5xx, circuito abierto) | [`IdentityClient.java:58-68`](../../services/circleguard-auth-service/src/main/java/com/circleguard/auth/client/IdentityClient.java#L58-L68) |
| Login degradado a 503 | [`LoginController.java:51`](../../services/circleguard-auth-service/src/main/java/com/circleguard/auth/controller/LoginController.java#L51) |
| Configuración de auth | [`auth/.../resilience4j.yml`](../../services/circleguard-auth-service/src/main/resources/resilience4j.yml) |
| Retry + Circuit Breaker en las dos llamadas a promotion | [`PromotionClient.java:40-48`](../../services/circleguard-dashboard-service/src/main/java/com/circleguard/dashboard/client/PromotionClient.java#L40-L48) |
| Fallbacks del dashboard | [`PromotionClient.java:58-66`](../../services/circleguard-dashboard-service/src/main/java/com/circleguard/dashboard/client/PromotionClient.java#L58-L66) |
| Configuración de dashboard | [`dashboard/.../resilience4j.yml`](../../services/circleguard-dashboard-service/src/main/resources/resilience4j.yml) |

La configuración vive en un `resilience4j.yml` propio que `application.yml` importa (`spring.config.import`). Las esperas se pueden cambiar con variables de entorno sin recompilar, y las pruebas cargan ese mismo archivo.

## Por qué estos valores

| Parámetro | Valor | Razón |
|---|---|---|
| Timeout | 2 s | identity y promotion responden en milisegundos; 2 s deja margen para un pico sin dejar al usuario esperando |
| Intentos | 3 | Cubre un reinicio o un fallo de red puntual. Más intentos multiplican la carga sobre un servicio que ya está mal |
| Espera entre intentos | 200 ms ×2 | Exponencial para no golpear al servicio en ráfaga |
| Ventana / mínimo | 10 / 5 llamadas | Con 3 intentos por petición, 2 logins fallidos seguidos bastan para abrir el circuito; un solo error aislado no lo abre |
| Umbral | 50 % | Mitad de las llamadas fallando ya es una caída, no ruido |
| Tiempo abierto | 10 s | Lo que tarda un contenedor en reiniciar; coincide con el `Retry-After` del 503 |

## Trade-offs

| A favor | En contra |
|---|---|
| Con el circuito abierto el login falla en ~0,2 s en lugar de esperar timeouts: no se acumulan hilos ocupados en auth (fallas en cascada) | Durante los 10 s abiertos, un login puede fallar aunque identity ya se haya recuperado |
| identity recibe menos tráfico mientras se recupera | Los reintentos multiplican hasta ×3 las llamadas cuando hay fallas pasajeras |
| El usuario recibe un 503 claro, sin detalles internos | Más configuración que mantener y probar: un umbral mal puesto abre el circuito por ruido o no lo abre nunca |
| Los fallos pasajeros (un reinicio) se ocultan al usuario | Los reintentos solo son seguros porque `POST /identities/map` es idempotente (devuelve el mismo ID anónimo); no se puede copiar a ciegas a cualquier llamada |
| El dashboard sigue mostrando la página aunque promotion esté caído | Mostrar datos vacíos marcados como degradados exige que el cliente web lo maneje |

**Alternativas descartadas:** solo timeout (no evita seguir llamando a un servicio caído); solo Retry (empeora la caída al multiplicar las llamadas); Bulkhead y Rate Limiter (no hay un problema de saturación medido; se reconsideran con los resultados de Locust en HU-24).

## Cómo se prueba

- **Pruebas con falla inyectada** ([`IdentityClientResilienceTest`](../../services/circleguard-auth-service/src/test/java/com/circleguard/auth/client/IdentityClientResilienceTest.java), [`PromotionClientResilienceTest`](../../services/circleguard-dashboard-service/src/test/java/com/circleguard/dashboard/client/PromotionClientResilienceTest.java)): un servidor HTTP local reemplaza al servicio remoto, responde 5xx, lento o bien según la prueba, y cuenta las llamadas que recibe. Así se comprueba que se reintenta, que el circuito se abre y **deja de llamar**, que vuelve a cerrarse cuando el servicio se recupera, que el timeout cuenta como falla y que un 4xx ni se reintenta ni abre el circuito.
- **En el sistema real** (compose de dev): se apaga identity, el login pasa a 503 y, con el circuito abierto, responde en ~0,2 s; al encender identity vuelve a 200 sin reiniciar auth. Ver la evidencia.

## Observabilidad

Resilience4j publica el estado del circuito y los reintentos como métricas de Micrometer (`resilience4j_circuitbreaker_state`, `resilience4j_retry_calls`). Se exponen en Prometheus con [HU-20 (#27)](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/27), y con ellas se puede alertar cuando un circuito se abre.
