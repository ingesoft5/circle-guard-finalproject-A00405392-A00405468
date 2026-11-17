# HU-18 · Configuración externa y feature toggle — evidencia (E2.4)

Issue [#25](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/25) · Sprint 2. Patrón, decisiones y trade-offs en [`docs/patrones/configuracion.md`](../../patrones/configuracion.md).

## Qué se entrega

| Pieza | Qué hace |
|---|---|
| **External Configuration** | El **K del k-anonimato** del dashboard sale del ConfigMap de Kubernetes (`PRIVACY_K_ANONYMITY` → `circleguard.privacy.k-anonymity`, por defecto 5). Antes estaba fijo en el código y **repetido en dos sitios** (`KAnonymityFilter` y `AnalyticsService`) |
| **Feature Toggle** | El **login contra LDAP** se apaga con `FEATURE_LDAP_LOGIN` → `circleguard.features.ldap-login-enabled` (por defecto `true`). Apagado, el login va directo a la base local y nunca llama a LDAP |
| **Chart de Helm** | `config.privacy.kAnonymity` y `config.features.ldapLoginEnabled` en `values.yaml`; el ConfigMap los publica y los pods se reinician solos al cambiarlos (`checksum/config`) |

Los valores por defecto conservan el comportamiento de siempre: desplegar esta historia sin tocar nada no cambia ningún ambiente.

## Resultados

- **Pruebas de los dos estados del toggle** ([`LdapLoginToggleTest`](../../../services/circleguard-auth-service/src/test/java/com/circleguard/auth/security/LdapLoginToggleTest.java), 7): con el toggle encendido se prueba LDAP y, si falla, la base local; con el toggle apagado **LDAP nunca autentica** y las credenciales malas siguen rechazándose. Con `ApplicationContextRunner` se comprueba que la propiedad `circleguard.features.ldap-login-enabled=false` llega al componente y que, sin propiedad, queda encendido.
- **Pruebas del K configurable** ([`KAnonymityConfigTest`](../../../services/circleguard-dashboard-service/src/test/java/com/circleguard/dashboard/service/KAnonymityConfigTest.java), 9): la misma población de 3 personas se enmascara con K=5 y se muestra con K=3; un conteo de 7 se enmascara con K=10; el valor se lee de la propiedad (8); sin propiedad es 5; **un K de 0 impide arrancar**. Las tendencias de entrada usan el mismo K que el filtro.
- **Suites completas, sobre la `main` que ya incluye la resiliencia de #66:** auth 33 pruebas y dashboard 15, 0 fallas; la cobertura mínima de JaCoCo de auth se sigue cumpliendo.
- **Chart:** 40 pruebas de plantillas (2 nuevas: las dos claves del ConfigMap con sus valores por defecto y cambiadas con `--set`) y `helm lint --strict` sin errores en los 4 ambientes.
- **En un clúster real** (k3s local, la misma versión que Terraform instala): el mismo release se desplegó con los valores por defecto y después con `--set config.features.ldapLoginEnabled=false --set config.privacy.kAnonymity=8`, **con la misma imagen** — [`hu-18-configuracion-k3s.txt`](hu-18-configuracion-k3s.txt):

| | Revisión 1 (valores por defecto) | Revisión 2 (`--set`) |
|---|---|---|
| ConfigMap | `FEATURE_LDAP_LOGIN=true`, `PRIVACY_K_ANONYMITY=5` | `FEATURE_LDAP_LOGIN=false`, `PRIVACY_K_ANONYMITY=8` |
| Variables dentro del pod de auth | `true 5` | `false 8` |
| Log de arranque de auth | `Feature toggle circleguard.features.ldap-login-enabled = true` | `... = false` |
| Login real de `super_admin` a través del Ingress | OK (token) | OK (token) |

## Lo que mostró la prueba real

1. **Cambiar el ConfigMap reinicia todos los servicios que lo leen, no solo el que usa el valor.** Tras la revisión 2, auth, form, gateway, identity, promotion y web aparecieron con ~70 s de edad y el middleware (Postgres, Neo4j, Kafka, Redis, que no leen el ConfigMap) con 6 min. Es el trade-off que se documentó: el K solo le importa al dashboard y el toggle solo a auth, pero todos comparten `circleguard-config`. Separar un ConfigMap por servicio evitaría el reinicio masivo; no se hizo porque para dos valores añade 8 objetos y la diferencia solo se nota en prod.
2. **El toggle apagado sigue autenticando:** con LDAP apagado el login contra la base local devuelve token. El LDAP de los ambientes de prueba es `ldap://ldap.invalid`, que nunca responde: con el toggle encendido cada login pasa primero por el fallo de LDAP.
3. **La primera versión de la prueba con `ApplicationContextRunner` falló con `NoInteractionsWanted`**, y se comprobó con una sonda que la causa no era el toggle: Spring invoca `setMessageSource` en el mock de LDAP al registrarlo (es lo único que toca), así que `verifyNoInteractions` era una pregunta demasiado estricta. Se cambió por `verify(ldap, never()).authenticate(any())`, que expresa lo que importa: LDAP **nunca autentica**.

## Limitaciones

- **El cambio de valores reinicia pods:** no hay recarga en caliente (decisión documentada en el patrón).
- **El dashboard no se ejecutó en el k3s local** (`values-local.yaml` lo desactiva por memoria): el K se comprobó en el ConfigMap y con las pruebas, no en un pod del dashboard. En prod (`t3.xlarge`) el dashboard está habilitado.
- **La demo en el k3s local se hizo antes de integrar #66** (la imagen de auth no traía todavía el Circuit Breaker); las suites completas se repitieron después de integrarlo y pasan.
- **No se probó en AWS:** pendiente de una ventana corta de prueba (un ambiente a la vez por el límite de 5 IP elásticas y el presupuesto).
- **Los valores de K por ambiente** (por ejemplo 5 en prod y 3 en dev) no se fijaron en `values-*.yaml`: es una decisión de privacidad que debe tomar quien responde por los datos, no una de despliegue.
