# HU-06 (#13) · Pruebas de integración entre servicios con Testcontainers — 2026-11-07

Se ejecutan con `./gradlew :services:<svc>:integrationTest` (etiqueta `@Tag("integration")`, fuera de `test`). El pipeline las detecta solo y las corre en la etapa **Integration tests**. Cada una levanta los contenedores reales que necesita; el mensaje o el token del servicio productor se construye con **su misma configuración** (serializador, tópico, clave y firma).

| # | Flujo | Servicio | Contenedores | Qué se verifica |
|---|---|---|---|---|
| 1 | form → Kafka `survey.submitted` → promotion | promotion | Kafka (`apache/kafka:3.8.0`), Neo4j 5.26, Redis 7.2 | Con síntomas: el usuario pasa a `SUSPECT` en Neo4j, el estado queda en Redis (`user:status:<id>`, lo lee gateway) y promotion publica `promotion.status.changed`, legible con el `StringDeserializer` de notification. Sin síntomas: sigue `ACTIVE` |
| 2 | QR firmado como auth → gateway (+ Redis de promotion) | gateway | Redis 7.2, gateway completo por HTTP | QR válido → `GREEN`; `SUSPECT`, `PROBABLE`, `CONFIRMED` → `RED`; `ACTIVE`, `RECOVERED` → `GREEN`; QR vencido o firmado con otra clave → `RED` |
| 3 | promotion → Kafka `promotion.status.changed` → notification | notification | Kafka | Estado de cerco → despacha el aviso y sincroniza la asistencia remota (LMS); volver a `ACTIVE` no notifica |

## Resultado final

```
$ ./gradlew :services:circleguard-promotion-service:integrationTest \
            :services:circleguard-gateway-service:integrationTest \
            :services:circleguard-notification-service:integrationTest
promotion     SurveySubmittedIntegrationTest: 2 pruebas, 0 fallas
gateway       QrAccessIntegrationTest:        7 pruebas, 0 fallas
notification  StatusChangedIntegrationTest:   2 pruebas, 0 fallas
BUILD SUCCESSFUL
```

`./gradlew build` sigue en verde, incluidas las pruebas de propagación a contactos de promotion (`HealthStatusReevaluationTest` 4/4, `AdministrativeCorrectionTest` 2/2).

## Errores reales que encontraron las pruebas (antes de corregir)

Las pruebas se escribieron primero contra el código sin cambios. Encontraron tres errores que las pruebas unitarias no veían porque simulan Kafka y Redis:

**1. gateway dejaba entrar a personas dentro del cerco.** Solo negaba `CONTAGIED` y `POTENTIAL`, pero promotion escribe `SUSPECT`, `PROBABLE` y `CONFIRMED`:

```
gateway  QrAccessIntegrationTest: 7 pruebas, 3 fallas
  FALLA [1] status=SUSPECT:   expected: false  but was: true
  FALLA [2] status=PROBABLE:  expected: false  but was: true
  FALLA [3] status=CONFIRMED: expected: false  but was: true
```

**2. promotion no actualizaba Redis ni avisaba si la persona no tenía contactos.** En la consulta Cypher, un `WHERE c1 IS NOT NULL` después de `OPTIONAL MATCH` eliminaba la única fila cuando no había contactos; la consulta no devolvía resultado y el código se saltaba Redis y Kafka (Neo4j sí cambiaba):

```
promotion  FALLA surveyWithSymptoms...: expected: "SUSPECT" but was: null   (Redis tras 60 s)
           log: "Updating status: ... -> SUSPECT" y nunca "Batch updating ... Redis entries"
```

**3. promotion no podía publicar a Kafka.** No tenía `value-serializer`, así que usaba `StringSerializer` con un `Map`:

```
SurveyListener : Failed to process survey event: {hasSymptoms=true, ...}
SerializationException: Can't convert value of class java.util.HashMap to class
  org.apache.kafka.common.serialization.StringSerializer specified in value.serializer
```

identity y notification tenían la misma configuración incompleta (publican objetos: auditoría de accesos a la bóveda y auditoría de avisos).

## Correcciones

| Error | Arreglo |
|---|---|
| 1 | `QrValidationService` niega `SUSPECT`, `PROBABLE`, `CONFIRMED` (y los nombres antiguos `CONTAGIED`, `POTENTIAL`) |
| 2 | El cambio de los contactos se calcula en una columna `changed` y se aplica con `FOREACH`, sin descartar la fila del usuario |
| 3 | `spring.kafka.producer` con `StringSerializer` + `JsonSerializer` en promotion, identity y notification (como form) |
