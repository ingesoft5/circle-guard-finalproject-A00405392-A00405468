# HU-05 (#12) · Cobertura de líneas ≥ 60 % en los 5 servicios núcleo — 2026-11-06

Medido con JaCoCo 0.8.13 (`./gradlew test jacocoTestReport`) en `gradle:8.14-jdk21`, con Docker para las pruebas de Testcontainers de promotion.

## Antes y después (cobertura de líneas por servicio, no promedio)

| Servicio | Antes | Después | Líneas cubiertas | Mínimo exigido |
|---|---|---|---|---|
| auth | 29.1 % | **89.6 %** | 163 / 182 | 60 % ✅ |
| identity | 50.4 % | **84.4 %** | 114 / 135 | 60 % ✅ |
| promotion | 35.0 % | **68.4 %** | 582 / 851 | 60 % ✅ |
| form | 43.6 % | **88.6 %** | 124 / 140 | 60 % ✅ |
| gateway | 81.0 % | **81.0 %** | 17 / 21 | 60 % ✅ |

`./gradlew build` completo: **110 pruebas, 0 fallas** (antes 51).

## El umbral bloquea

Cada servicio núcleo tiene `jacocoTestCoverageVerification` con mínimo 0.60 de líneas, que corre al terminar `test` (y en `check`). Prueba negativa: subiendo el umbral de gateway al 95 % en una copia temporal, el build falla:

```
> Task :services:circleguard-gateway-service:jacocoTestCoverageVerification FAILED
[ant:jacocoReport] Rule violated for bundle circleguard-gateway-service: lines covered ratio is 0.80, but expected minimum is 0.95
BUILD FAILED in 7s
```

Con el umbral real (0.60) la tarea pasa en los 5 servicios:

```
> Task :services:circleguard-auth-service:jacocoTestCoverageVerification
> Task :services:circleguard-form-service:jacocoTestCoverageVerification
> Task :services:circleguard-gateway-service:jacocoTestCoverageVerification
> Task :services:circleguard-identity-service:jacocoTestCoverageVerification
> Task :services:circleguard-promotion-service:jacocoTestCoverageVerification
BUILD SUCCESSFUL in 1m 47s
```

## Qué se probó

| Servicio | Pruebas nuevas |
|---|---|
| auth | Emisión y validación de JWT y QR (incluido QR vencido), filtro JWT (válido, alterado, sin cabecera), cadena LDAP → cuentas locales, roles y permisos como *authorities*, usuario deshabilitado, login 401/500, *handoff* de visitante, usuarios por permiso, QR solo autenticado |
| identity | Hash estable y sin la identidad en claro, alta y reutilización del ID anónimo, 404 al resolver, endpoints públicos `/map` y `/visitor`, `lookup` con JWT real con permiso (200 + auditoría en Kafka), sin permiso (403) y alterado (401) |
| form | Encuesta con síntomas publicada a Kafka, sin cuestionario activo, certificado pendiente/aprobado/rechazado, cuestionarios (crear, listar, activar desactivando los demás), almacenamiento y error de disco |
| promotion | Edificios, pisos y puntos de acceso (crear, editar, borrar con sus restricciones), resolución de ubicación con encuentros en Redis y Neo4j, y sus controladores con la regla `ADMIN` (403 para otro rol) |

La lógica de negocio de los controladores de promotion se probó con la `SecurityConfig` real, así que también quedan cubiertas las reglas de autorización.
