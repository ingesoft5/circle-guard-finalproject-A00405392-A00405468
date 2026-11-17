# HU-17 · Circuit Breaker + Retry: evidencia

Historia: [#24](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/24) · Diseño y trade-offs: [`docs/patrones/resiliencia.md`](../../patrones/resiliencia.md) · Fecha: 17 de noviembre de 2026

## Criterios de aceptación

| Criterio | Estado | Evidencia |
|---|---|---|
| Circuit Breaker + Retry en `auth → identity` y `dashboard → promotion` | ✅ | `IdentityClient` y `PromotionClient` con `@Retry` + `@CircuitBreaker`, timeout de 2 s y fallback ([tabla de archivos y líneas](../../patrones/resiliencia.md#dónde-está-en-el-código)) |
| Prueba con falla inyectada que demuestra el efecto | ✅ | 10 pruebas nuevas (abajo) y la demostración en el compose de dev |
| Propósito y trade-offs documentados en `docs/patrones/` | ✅ | [`resiliencia.md`](../../patrones/resiliencia.md) |
| Patrones existentes documentados con archivo y línea | ➡️ | Se entrega con HU-19 ([#26](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/26)), que tiene el mismo criterio |

## 1. Pruebas con falla inyectada

Un servidor HTTP local reemplaza a identity/promotion: responde 5xx, lento o bien según la prueba y cuenta las llamadas que recibe. Se carga la configuración real (`resilience4j.yml`); solo se acortan las esperas.

`gradle :services:circleguard-auth-service:test :services:circleguard-dashboard-service:test` → **auth 26 pruebas, dashboard 6, 0 fallas**; la cobertura mínima del 60 % de auth se sigue cumpliendo.

| Prueba | Falla inyectada | Qué demuestra |
|---|---|---|
| `IdentityClientResilienceTest.transientFailureIsRetriedAndTheLoginSucceeds` | 503 dos veces y luego bien | 3 llamadas; el login funciona y el circuito sigue cerrado |
| `…identityDownOpensTheCircuitAndThenFailsFastWithoutCallingIt` | 500 siempre | Tras 5 fallas el circuito se **abre**; 5 logins más no llegan a identity (las llamadas recibidas no cambian) |
| `…circuitClosesAgainWhenIdentityRecovers` | 500 y luego bien | Abierto → semiabierto tras la espera → **cerrado** con 2 llamadas de prueba exitosas |
| `…slowIdentityCountsAsAFailureThanksToTheTimeout` | Responde en 1 s (timeout de 300 ms en la prueba) | El timeout corta y cuenta como falla: 3 fallas registradas |
| `…clientErrorsAreNeitherRetriedNorCountedAsFailures` | 400 | 1 sola llamada, 0 fallas en el circuito |
| `LoginControllerTest.identityUnavailableDegradesTo503InsteadOf500` | identity no disponible | El login responde 503 con `Retry-After: 10` y no emite token |
| `PromotionClientResilienceTest.returnsPromotionStatsWhenHealthy` | — | Camino feliz: 1 llamada |
| `…transientFailureIsRetried` | 502 una vez | 2 llamadas y respuesta normal |
| `…promotionDownDegradesTheDashboardAndOpensTheCircuit` | 503 siempre | Respuesta con `degraded: true`; con el circuito abierto el dashboard ya no llama a promotion |
| `…departmentWithSpacesIsEncodedInThePath` | — | `Faculty of Engineering` viaja como `Faculty%20of%20Engineering` (antes se concatenaba sin codificar) |

## 2. Demostración en el sistema real

`deploy/compose/compose.sh dev up -d --wait` con la imagen de auth de esta rama (10/10 contenedores `healthy`). Login de `health_user` a través del nginx del web (`POST /api/auth/api/v1/auth/login`); la columna de tiempo es la respuesta completa vista por el cliente.

```
## 1. identity arriba
200 0.981067s {"anonymousId":"c1d478b3-…","type":"Bearer","token":"eyJhbGciOiJ…
## 2. se detiene identity (14:52:27)
login 1: 503 6.850372s {"message":"Identity service unavailable, please try again shortly"}
login 2: 503 2.429772s {"message":"Identity service unavailable, please try again shortly"}
login 3: 503 0.299007s {"message":"Identity service unavailable, please try again shortly"}
login 4: 503 0.237066s {"message":"Identity service unavailable, please try again shortly"}
login 5: 503 0.210856s {"message":"Identity service unavailable, please try again shortly"}
## 3. logs de auth
WARN … c.c.auth.client.IdentityClient : identity-service no disponible: sin respuesta (I/O error on POST request for "http://identity:8083/…
WARN … c.c.auth.client.IdentityClient : identity-service no disponible: circuito abierto, no se llama
WARN … c.c.auth.client.IdentityClient : identity-service no disponible: circuito abierto, no se llama
WARN … c.c.auth.client.IdentityClient : identity-service no disponible: circuito abierto, no se llama
WARN … c.c.auth.client.IdentityClient : identity-service no disponible: circuito abierto, no se llama
## 4. se enciende identity (14:52:39)
identity healthy (14:52:52)
login 1: 200 0.382971s {"anonymousId":"c1d478b3-…","type":"Bearer","token":"eyJhbGciOiJ…
login 2: 200 0.233416s
login 3: 200 0.233929s
```

Lectura:

- **Login 1 (6,9 s):** 3 intentos contra un identity apagado, más las esperas de 200 y 400 ms. Antes de este cambio el resultado era un 500 con el mensaje interno del error.
- **Login 2 (2,4 s):** dos intentos más completan 5 fallas y el circuito se abre; el tercer intento ya no se hace.
- **Logins 3 a 5 (~0,2 s):** circuito abierto, auth responde 503 sin llamar a identity. El tiempo restante es la verificación de la contraseña (BCrypt).
- **Recuperación:** con identity de nuevo arriba y pasados los 10 s, los logins vuelven a 200 sin reiniciar auth. El ID anónimo es el mismo de antes de la caída.

## 3. Hallazgo

Los `application.yml` de `src/test/resources` de auth tapan al principal durante las pruebas. La primera corrida de las pruebas falló justamente por eso: Resilience4j tomó sus valores por defecto (reintentaba los 4xx y el circuito nunca se abría con menos de 100 llamadas). Por eso la configuración quedó en un `resilience4j.yml` propio, que importan tanto `application.yml` como la prueba.
