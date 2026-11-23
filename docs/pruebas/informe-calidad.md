# Informe de calidad: cobertura, rendimiento y seguridad

Historia: [HU-24 (#31)](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/31) · Objetivo de rendimiento (declarado antes de medir): [`rendimiento-objetivo.md`](rendimiento-objetivo.md) · Evidencia: [`docs/evidencias/sprint-2/hu-24/`](../evidencias/sprint-2/hu-24) · Fecha: 21 de noviembre de 2026

## Resumen

| Área | Resultado |
|---|---|
| **Rendimiento contra el objetivo** | ✅ **Se cumple con margen.** Puerta: 55 req/s con p95 de 5 ms (objetivo ≥ 33 req/s y ≤ 300 ms). Cuestionario: 22 req/s con p95 de 14 ms (objetivo ≥ 14 req/s y ≤ 800 ms). 0 % de errores. Con 300 usuarios, 197 y 79 req/s sin degradarse |
| **Cuello de botella encontrado** | El **login**: BCrypt de costo 12 lo hace dependiente de la CPU. En un nodo de 2 vCPU (el de la nube) se saturaba en **8,5 req/s con p95 de 5,5 s** |
| **Mejora de rendimiento aplicada y medida otra vez** | Costo de BCrypt 12 → 10 (el mínimo de OWASP): **29,4 req/s (×3,5) y p95 de 980 ms (−82 %)** en las mismas condiciones |
| **Seguridad dinámica (ZAP baseline)** | 0 fallas graves. 9 advertencias → **6** tras agregar las cabeceras de seguridad al nginx, sin romper la app (E2E 6/6). Con las reglas del equipo: 0 FAIL, 2 WARN, 4 falsos positivos ignorados con su razón. El control negativo (imagen sin cabeceras) **falla** como debe |
| **ZAP en el pipeline** | Etapa «ZAP baseline contra dev» después de los E2E: si se pierde una cabecera de seguridad, la versión no pasa a stage |

## 1. Cobertura y calidad del código

| Servicio núcleo | Cobertura de líneas (JaCoCo, mínimo exigido por el build: 60 %) | Pruebas unitarias | Integración |
|---|---|---|---|
| auth | 92,3 % | 37 (con las 4 de esta historia) | — |
| identity | 93,5 % | 17 | — |
| form | 86,7 % | 21 | — |
| promotion | 67,4 % | 44 | 2 (Kafka, Neo4j y Redis reales) |
| gateway | 80,0 % | 10 | 11 (Redis real) |

Coberturas de SonarQube tras excluir el código de Lombok ([`ensayo-pipeline-real.md` §2.1](../evidencias/sprint-1/ensayo-pipeline-real.md)). Pruebas al 21 de noviembre, contando las de los PR abiertos de la Parte 2 (HU-20, #72). Además: **6 E2E de Playwright** (login, cuestionario, validación en la puerta y versión), que corren en el pipeline contra dev, y las pruebas de plantillas del chart de Helm, de los módulos de Terraform y de las reglas de alerta (`promtool`). La calidad se controla en cada PR con el *quality gate* de SonarQube, y la seguridad estática con Trivy y gitleaks (HU-23).

**Lo más débil:** promotion, con 67,4 %, es el único núcleo cerca del mínimo. Lo que falta cubrir son las consultas de propagación en Neo4j y sus casos de error; se prueban mejor con integración que con mocks.

## 2. Rendimiento (Locust)

Locust en un contenedor de la misma red, contra el nginx del compose de dev ([`tests/performance/`](../../tests/performance)). Todo, incluido Locust, corre en un portátil de 12 núcleos.

| Corrida | Usuarios | Puerta: req/s · p95 · p99 | Cuestionario: req/s · p95 · p99 | Errores | Objetivo |
|---|---|---|---|---|---|
| Base | 60 (50 puerta + 10 cuestionario) | 44,8 · 9 ms · 250 ms | **8,8** · 68 ms · 450 ms | 0 % | Puerta ✅ · cuestionario ❌ (throughput) |
| Perfil corregido | 90 (proporción 5:2) | **55,4 · 5 ms · 27 ms** | **22,2 · 14 ms · 42 ms** | 0 % | ✅ los dos |
| Estrés | 300 | 196,5 · 3 ms · 5 ms | 78,6 · 7 ms · 10 ms | 0 % | ✅ los dos |

**Lectura:**

- **El "no cumple" de la corrida base es de la prueba, no del sistema.** El cuestionario respondía en 68 ms de p95, pero 10 usuarios con pausas de ~1 s no pueden generar más de ~10 req/s. Se corrigió el **perfil** (más usuarios de cuestionario) sin tocar el **objetivo**, y se dejó explicado en el `locustfile.py`.
- **Con 300 usuarios no hay degradación** (p99 ≤ 10 ms) y ningún contenedor pasa del 15 % de CPU. Después de 19 679 cuestionarios, promotion terminó con **lag 0** en `survey.submitted`: el cerco asíncrono sigue el ritmo de la carga.
- Los p99 de la corrida base (250 a 450 ms) son el calentamiento de la JVM en los primeros segundos; desaparecen en las corridas siguientes.

## 3. Cuello de botella y mejora aplicada: el login

El login no estaba en el objetivo, pero en la hora pico cada persona inicia sesión para sacar su QR, y en la corrida base fue lo más lento (p95 de 430 ms). Con un escenario solo de login (`CLASSES=LoginUser`, 50 usuarios, 2 min), auth usó el **960 % de CPU** mientras identity y Postgres estaban casi en reposo: el tiempo se iba en verificar la contraseña. La migración `V3` había dejado las cuentas con **BCrypt de costo 12** (~250 ms de CPU por verificación).

En la nube cada ambiente es un nodo **t3.large de 2 vCPU** (HU-14). Para medir en esas condiciones, auth se limitó a 2 CPU (`docker update --cpus=2`) antes y después:

| Login, auth con 2 CPU, 50 usuarios | Costo 12 (antes) | Costo 10 (después) |
|---|---|---|
| Throughput | 8,5 req/s | **29,4 req/s** (×3,5) |
| p50 / p95 / p99 | 4 900 / 5 500 / 5 700 ms | **660 / 980 / 1 100 ms** |
| Mínimo | 1 021 ms | 67 ms |
| Errores | 0 % | 0 % |

**Qué se cambió:** la migración [`V7`](../../services/circleguard-auth-service/src/main/resources/db/migration/V7__rehash_seed_users_bcrypt_cost_10.sql) vuelve a calcular el hash de las cuentas locales con costo 10, y `SecurityConfig` fija el costo de forma explícita (`circleguard.security.bcrypt-strength`, por defecto 10), con un **mínimo de 10**: un valor menor impide arrancar. Los hashes antiguos de costo 12 se siguen aceptando (BCrypt lee el costo del propio hash). Hay 4 pruebas nuevas en `PasswordHashingTest`.

**Trade-off:** cada intento de adivinar una contraseña robada cuesta 4 veces menos. Sigue en el mínimo que recomienda OWASP para BCrypt (costo ≥ 10). Las cuentas reales de la universidad vienen de LDAP, que no usa estos hashes. El login sigue limitado por CPU (p95 ≈ 1 s con 50 personas a la vez en 2 vCPU); la siguiente palanca es escalar auth horizontalmente con el HPA de HU-15, no bajar más el costo.

## 4. Seguridad dinámica (OWASP ZAP baseline)

ZAP 2.15.0 en modo *baseline* (araña más análisis pasivo, sin ataques) contra el web del compose ([`tests/security/zap/`](../../tests/security/zap)).

| Corrida | FAIL | WARN | Ignorados | PASS | Informe |
|---|---|---|---|---|---|
| Antes (sin cabeceras, sin reglas) | 0 | 9 | 0 | 58 | [`zap-antes.md`](../evidencias/sprint-2/hu-24/zap-antes.md) |
| Después de agregar las cabeceras (sin reglas) | 0 | 6 | 0 | 61 | [`zap-despues-sin-reglas.md`](../evidencias/sprint-2/hu-24/zap-despues-sin-reglas.md) |
| Con las reglas del equipo | **0** | 2 | 4 | 61 | [`zap-con-reglas.md`](../evidencias/sprint-2/hu-24/zap-con-reglas.md) |
| Control negativo: imagen de `main`, sin cabeceras | **4** (sale con código 1) | 1 | 4 | 58 | [`zap-control-negativo.md`](../evidencias/sprint-2/hu-24/zap-control-negativo.md) |

**Mejora aplicada (en el nginx del web):** `Content-Security-Policy`, que permite solo los scripts propios más el único script en línea de Expo (por su hash, sin `unsafe-inline` en scripts) y `frame-ancestors 'none'`; además `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy` y `Permissions-Policy` (cámara solo propia, para el escáner). El HTML va sin caché. Detalle técnico: en nginx, un `add_header` dentro de un `location` anula todos los heredados del `server`, así que la caché se fija con `expires` y las cabeceras de seguridad llegan a todas las rutas, incluidas las de la API. **Los 6 E2E pasan con la CSP activa.**

**Lo que queda, y por qué** ([`reglas.conf`](../../tests/security/zap/reglas.conf)):

| Hallazgo | Decisión |
|---|---|
| CSP con `style-src 'unsafe-inline'` | **Riesgo aceptado:** React Native Web inyecta los estilos en tiempo de ejecución. Los scripts sí quedan restringidos, que es donde está el riesgo de XSS |
| Contenido almacenable | Informativo: el HTML va sin caché y los `.js` con hash, con caché larga a propósito |
| Comentarios sospechosos, marcas de tiempo, `eval` | **Falsos positivos:** salen de librerías dentro del *bundle* de Expo, no del código propio |
| ZAP desactualizado | La versión de la imagen está fijada a propósito, para que los resultados sean reproducibles |

**En el pipeline:** el paso [`zapBaseline`](../../jenkins/vars/zapBaseline.groovy) corre después de los E2E contra dev y publica el informe HTML en el build. Las 4 cabeceras están marcadas **FAIL**: si un cambio las quita, la versión no pasa a stage.

## 5. Propuestas de mejora

| # | Propuesta | Estado |
|---|---|---|
| 1 | BCrypt de costo 12 a 10 en las cuentas locales | ✅ **Aplicada y medida otra vez** (§3) |
| 2 | Cabeceras de seguridad en el nginx y ZAP como compuerta del pipeline | ✅ **Aplicada y medida otra vez** (§4) |
| 3 | Escalar auth con el HPA (CPU 70 %) en vez de bajar más el costo de BCrypt: el login sigue limitado por CPU | Propuesta; el HPA ya existe en el chart (HU-15) y se valida en la nube |
| 4 | Tiempo de contención en frío: 5,5 s en la primera ráfaga tras arrancar contra 0,6 s en caliente (HU-20). Precalentar el consumidor de Kafka y las conexiones antes de marcar promotion como *ready* | Propuesta |
| 5 | promotion registra cada evento de cuestionario completo en nivel INFO (19 679 líneas en 3 min de carga): bajarlo a DEBUG antes de centralizar logs (HU-22) | Propuesta |
| 6 | Subir la cobertura de promotion (67,4 %) con más pruebas de integración de la propagación en Neo4j | Propuesta |
| 7 | Actualizar Spring Boot 3.2.4 → 3.5.x para cerrar los CVE registrados como deuda en HU-23 | Propuesta (toca los 8 servicios) |

## Cómo reproducirlo

```bash
./deploy/compose/compose.sh dev up -d --wait
tests/performance/run.sh 90 3m perfil                                    # objetivo; sale con 1 si no se cumple
CLASSES=LoginUser SPAWN_RATE=10 tests/performance/run.sh 50 2m login     # solo login
tests/security/zap/run.sh baseline                                       # sale con 1 si aparece una regla FAIL
```
