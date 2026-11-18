# Patrones que ya tenía la arquitectura

Historia: [HU-19 (#26)](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/26). Cada patrón se identificó leyendo el código. La columna "Evidencia" da el archivo y la línea donde se ve, y la columna "Qué mejorar" anota lo que encontramos al revisarlo.

## 1. Database per Service

Cada servicio tiene su propia base de datos y nadie lee la base de otro: si necesita un dato ajeno, lo pide por HTTP o lo recibe por un evento.

| Servicio | Almacén | Evidencia |
|---|---|---|
| auth | PostgreSQL `circleguard_auth` | [`auth/application.yml:10`](../../services/circleguard-auth-service/src/main/resources/application.yml#L10) |
| identity | PostgreSQL `circleguard_identity` | [`identity/application.yml:9`](../../services/circleguard-identity-service/src/main/resources/application.yml#L9) |
| form | PostgreSQL `circleguard_form` | [`form/application.yml:6`](../../services/circleguard-form-service/src/main/resources/application.yml#L6) |
| promotion | PostgreSQL `circleguard_promotion` + **Neo4j** (grafo de contactos) + **Redis** (estado para la puerta) | [`promotion/application.yml:6`](../../services/circleguard-promotion-service/src/main/resources/application.yml#L6) |
| dashboard | PostgreSQL `circleguard_dashboard` | [`dashboard/application.yml:9`](../../services/circleguard-dashboard-service/src/main/resources/application.yml#L9) |
| Creación de las 5 bases | Un esquema por servicio en el mismo servidor | [`deploy/compose/postgres/init-db.sql:2-6`](../../deploy/compose/postgres/init-db.sql#L2-L6) |

**Por qué aquí:** cada servicio evoluciona su esquema (migraciones Flyway propias) sin coordinar con los demás. promotion usa además el almacén que mejor encaja con su problema (*polyglot persistence*): un grafo para recorrer contactos de nivel 1 y 2, y Redis para que la puerta lea el estado en milisegundos.

**Trade-offs:** no hay *joins* ni transacciones entre servicios. Un mismo dato vive copiado en varios sitios (el estado está en Neo4j y en Redis) y hay que mantener la consistencia: ver el patrón cache-aside de HU-19.

**Qué mejorar:** las 5 bases comparten un único servidor PostgreSQL, así que el aislamiento es lógico y no físico. Si ese servidor cae, caen los 5 servicios.

## 2. Event-Driven Architecture (publicar/suscribir con Kafka)

Los servicios se avisan los cambios con eventos y no se llaman entre sí para eso. Quien publica no sabe quién escucha.

| Tópico | Publica | Evidencia | Consume | Evidencia |
|---|---|---|---|---|
| `survey.submitted` | form | [`HealthSurveyService.java:52`](../../services/circleguard-form-service/src/main/java/com/circleguard/form/service/HealthSurveyService.java#L52) | promotion | [`SurveyListener.java:16`](../../services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/listener/SurveyListener.java#L16) |
| `certificate.validated` | form | [`HealthSurveyService.java:78`](../../services/circleguard-form-service/src/main/java/com/circleguard/form/service/HealthSurveyService.java#L78) | promotion | [`SurveyListener.java:33`](../../services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/listener/SurveyListener.java#L33) |
| `promotion.status.changed` | promotion | [`HealthStatusService.java:122`](../../services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/HealthStatusService.java#L122), [`StatusLifecycleService.java:76`](../../services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/StatusLifecycleService.java#L76) | notification | [`ExposureNotificationListener.java:22`](../../services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/ExposureNotificationListener.java#L22) |
| `circle.fenced` | promotion | [`HealthStatusService.java:153`](../../services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/HealthStatusService.java#L153) | notification | [`CircleFencedListener.java:20`](../../services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/CircleFencedListener.java#L20) |
| `alert.priority` | promotion | [`HealthStatusService.java:138`](../../services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/HealthStatusService.java#L138) | notification | [`PriorityAlertListener.java:27`](../../services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/PriorityAlertListener.java#L27) |

**Por qué aquí:** enviar un cuestionario no debería esperar a que se recalcule el grafo de contactos ni a que salgan las notificaciones. form responde al usuario y el resto ocurre después. Si notification está caído, los eventos esperan en Kafka y no se pierden.

**Trade-offs:** la consistencia es eventual (el estado cambia unos segundos después de enviar el cuestionario) y es más difícil seguir una petición de punta a punta; para eso está el trazado de HU-22. Las pruebas de integración del Sprint 1 encontraron dos errores que solo aparecen con eventos reales: un serializador JSON que faltaba y un mensaje que no llegaba porque la consulta descartaba a quien no tenía contactos ([`pruebas-integracion.md`](../evidencias/sprint-1/pruebas-integracion.md)).

**Qué mejorar:** `audit.identity.accessed`, `notification.audit` y `proximity.detected` se publican pero ningún servicio los consume. Tampoco hay *outbox*: si promotion guarda en Neo4j y Kafka falla justo después, el evento se pierde.

## 3. State Machine (ciclo de vida del estado de salud)

El estado de cada persona solo cambia por transiciones definidas:

```
ACTIVE ──(cuestionario con síntomas)──► SUSPECT ──(contacto de un CONFIRMED)──► ...
   ▲                                         │
   │                                         └──(caso confirmado)──► CONFIRMED ──(alta)──► RECOVERED
   └──(vence el plazo sin confirmación / resolución del caso)── SUSPECT, PROBABLE
Contactos: de un CONFIRMED pasan a SUSPECT (nivel 1); de un SUSPECT, a PROBABLE (nivel 2).
```

| Transición | Evidencia |
|---|---|
| Cambio de estado y propagación a contactos de nivel 1 y 2 (CONFIRMED → SUSPECT, SUSPECT → PROBABLE) | [`HealthStatusService.java:40`](../../services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/HealthStatusService.java#L40), reglas en [`:67-89`](../../services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/HealthStatusService.java#L67-L89) |
| Un SUSPECT o PROBABLE no vuelve a ACTIVE antes de cumplir el cerco obligatorio (14 días por defecto), salvo con permiso de administración | [`HealthStatusService.java:43-44`](../../services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/HealthStatusService.java#L43-L44), regla en [`:288-302`](../../services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/HealthStatusService.java#L288-L302) |
| Resolución: libera a los contactos SUSPECT y PROBABLE que no tengan otro riesgo | [`HealthStatusService.java:190`](../../services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/HealthStatusService.java#L190), fases en [`:203-237`](../../services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/HealthStatusService.java#L203-L237) |
| CONFIRMED → RECOVERED (en Redis vence a los 30 días) | [`HealthStatusService.java:278`](../../services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/HealthStatusService.java#L278) |
| Vencimiento automático cada hora: SUSPECT/PROBABLE → ACTIVE | [`StatusLifecycleService.java:33-58`](../../services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/StatusLifecycleService.java#L33-L58) |
| Estados que la puerta trata como "en cerco" | [`QrValidationService.java`](../../services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/service/QrValidationService.java) (`FENCED_STATUSES`) |

**Por qué aquí:** el cerco epidemiológico es justamente un conjunto de reglas sobre qué estado sigue a cuál. Tenerlas explícitas evita estados imposibles (un RECOVERED que vuelve a SUSPECT por un contacto viejo).

**Trade-offs y qué mejorar:** las transiciones viven como texto dentro de consultas Cypher y no como un tipo (`enum`) con sus transiciones válidas. Por eso el gateway tenía su propia lista de estados, desalineada con la de promotion: dejaba pasar a SUSPECT, PROBABLE y CONFIRMED (error encontrado y corregido en el Sprint 1).

## 4. API Gateway / punto de entrada único

El navegador solo habla con un punto: el nginx del cliente web, que enruta `/api/<servicio>/...` al servicio correspondiente. Los servicios no exponen puertos al exterior.

| Qué | Evidencia |
|---|---|
| Tabla servicio → destino | [`default.conf.template:5`](../../mobile/nginx/default.conf.template#L5) |
| Enrutamiento `/api/<servicio>/` con reescritura de la ruta y cabeceras `X-Forwarded-*` | [`default.conf.template:50`](../../mobile/nginx/default.conf.template#L50) |
| Las rutas `/internal/` no se publican (HU-19) | [`default.conf.template:42`](../../mobile/nginx/default.conf.template#L42) |
| En Kubernetes, el Ingress apunta solo al web | `deploy/helm/circleguard/templates/ingress.yaml` (HU-15) |

**Por qué aquí:** un solo origen para el navegador evita CORS y esconde la topología interna. Cambiar dónde corre un servicio no cambia el cliente.

**Trade-offs y qué mejorar:** es un punto único de falla, y hoy solo enruta: la autenticación la valida cada servicio con el JWT. A pesar del nombre, `circleguard-gateway-service` no es el API Gateway sino el validador de QR de la puerta física.

## 5. Pseudonimización (identity vault)

Los servicios de negocio nunca ven la identidad real: trabajan con un UUID anónimo que solo identity sabe traducir.

| Qué | Evidencia |
|---|---|
| Identidad real → hash con sal → UUID anónimo (crea el UUID la primera vez) | [`IdentityVaultService.java:28`](../../services/circleguard-identity-service/src/main/java/com/circleguard/identity/service/IdentityVaultService.java#L28) |
| Traducción inversa, con auditoría | [`IdentityVaultService.java:54`](../../services/circleguard-identity-service/src/main/java/com/circleguard/identity/service/IdentityVaultService.java#L54), [`IdentityVaultController.java:97`](../../services/circleguard-identity-service/src/main/java/com/circleguard/identity/controller/IdentityVaultController.java#L97) |
| El login emite el token con el UUID anónimo | [`LoginController.java:37`](../../services/circleguard-auth-service/src/main/java/com/circleguard/auth/controller/LoginController.java#L37) |

**Por qué aquí:** datos de salud. Si se filtra la base de promotion, no dice quién es quién.

**Trade-offs:** identity queda en el camino de cada login (por eso es la llamada que protegimos con Circuit Breaker en HU-17), y la sal tiene un valor por defecto en el código (`vault.hash-salt`) que en los ambientes se reemplaza por `VAULT_HASH_SALT`.
