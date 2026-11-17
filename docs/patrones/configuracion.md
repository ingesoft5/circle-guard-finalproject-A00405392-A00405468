# Patrón de configuración: External Configuration + Feature Toggle

Historia: [HU-18 (#25)](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/25) · Fuente de verdad: el ConfigMap del chart de Helm ([HU-15](../evidencias/sprint-2/hu-15-kubernetes.md)) · Evidencia: [`hu-18-configuracion.md`](../evidencias/sprint-2/hu-18-configuracion.md)

## El problema

Dos decisiones de comportamiento estaban escritas en el código, así que cambiarlas exigía recompilar, construir una imagen nueva y volver a desplegar:

| Decisión | Cómo estaba | Qué costaba cambiarla |
|---|---|---|
| **K del k-anonimato** (el mínimo de personas para mostrar un conteo en el dashboard) | Un `5` fijo en `KAnonymityFilter` y **otro `5` repetido** en `AnalyticsService.getEntryTrends` | Tocar dos sitios, y era fácil que quedaran distintos: una pantalla enmascaraba con K=5 y otra con otro valor |
| **Login contra LDAP** | `DualChainAuthenticationProvider` siempre probaba LDAP primero y, si fallaba, la base local | En un ambiente sin servidor LDAP (dev, local, la demo) cada login esperaba el fallo de LDAP antes de llegar a la base local. No había forma de apagarlo sin cambiar código |

## La solución

```
values.yaml (o --set)  ──►  ConfigMap circleguard-config  ──►  variable de entorno del pod  ──►  propiedad de Spring  ──►  el código
config.privacy.kAnonymity       PRIVACY_K_ANONYMITY              (envFrom del Deployment)       circleguard.privacy.k-anonymity   KAnonymityFilter
config.features.ldapLoginEnabled FEATURE_LDAP_LOGIN                                              circleguard.features.ldap-login-enabled  DualChainAuthenticationProvider
```

- **External Configuration.** El K ya no vive en el código: se lee de `circleguard.privacy.k-anonymity`, que `application.yml` resuelve desde `PRIVACY_K_ANONYMITY` (por defecto 5). Un solo punto lo lee y `AnalyticsService` le pregunta el valor al filtro (`kAnonymityFilter.k()`), así que **toda la aplicación enmascara con el mismo K**.
- **Feature Toggle.** El login contra LDAP se enciende o apaga con `circleguard.features.ldap-login-enabled` (`FEATURE_LDAP_LOGIN`, por defecto `true`). Apagado, `authenticate` va directo a la base local y **nunca llama a LDAP**.
- **Los valores por defecto conservan el comportamiento de siempre** (K=5, LDAP encendido). Ningún ambiente cambia al desplegar esta historia; solo ganan la posibilidad de cambiar.
- **Cambiar un valor es cambiar el chart**, no el código: `helm upgrade --set config.privacy.kAnonymity=8 --set config.features.ldapLoginEnabled=false`. El Deployment lleva una anotación `checksum/config` con el hash del ConfigMap, así que Helm **reinicia los pods solo** cuando el ConfigMap cambia. Sin esa anotación los pods seguirían con el valor viejo.
- **Un K inválido no desactiva la protección en silencio.** `K < 1` hace fallar el arranque con un mensaje que dice qué propiedad está mal. Un K de 0 dejaría pasar cualquier conteo, y para una medida de privacidad es mejor que el servicio no arranque a que arranque sin proteger.

### Dónde está en el código

| Qué | Archivo y línea |
|---|---|
| Toggle de LDAP: se lee la propiedad y se registra su valor al arrancar | [`DualChainAuthenticationProvider.java:36-45`](../../services/circleguard-auth-service/src/main/java/com/circleguard/auth/security/DualChainAuthenticationProvider.java#L36-L45) |
| Apagado: solo la base local; encendido: LDAP y, si falla, la base local | [`DualChainAuthenticationProvider.java:48-59`](../../services/circleguard-auth-service/src/main/java/com/circleguard/auth/security/DualChainAuthenticationProvider.java#L48-L59) |
| Propiedad del toggle (`FEATURE_LDAP_LOGIN`) | [`auth/.../application.yml:33-34`](../../services/circleguard-auth-service/src/main/resources/application.yml#L33-L34) |
| K leído de la configuración, con validación | [`KAnonymityFilter.java:22-27`](../../services/circleguard-dashboard-service/src/main/java/com/circleguard/dashboard/service/KAnonymityFilter.java#L22-L27) |
| Las tendencias de entrada usan el mismo K (antes un `5` propio) | [`AnalyticsService.java:42`](../../services/circleguard-dashboard-service/src/main/java/com/circleguard/dashboard/service/AnalyticsService.java#L42) |
| Propiedad del K (`PRIVACY_K_ANONYMITY`) | [`dashboard/.../application.yml:19-20`](../../services/circleguard-dashboard-service/src/main/resources/application.yml#L19-L20) |
| Las dos claves en el ConfigMap | [`configmap.yaml:17-18`](../../deploy/helm/circleguard/templates/configmap.yaml#L17-L18) |
| Valores por defecto y su comentario | [`values.yaml:30-33`](../../deploy/helm/circleguard/values.yaml#L30-L33) |

## Por qué estas dos decisiones

| Decisión | Razón |
|---|---|
| **El K y el toggle de LDAP**, no "todo" | Son los dos únicos valores de comportamiento que el equipo necesita cambiar entre ambientes y que estaban fijos en el código. Las URLs, el host de la base y los tiempos de espera ya eran configurables (variables de entorno desde la Parte 1) |
| **ConfigMap, no Secret** | Ninguno de los dos es secreto. Los secretos (claves, contraseñas) siguen en el `Secret` del chart y no pasan por aquí |
| **Reiniciar los pods al cambiar el valor** | Spring lee las propiedades al arrancar. La alternativa, recargar en caliente, pide `spring-cloud-kubernetes` y un `@RefreshScope`: una dependencia más y un servicio con permisos para leer el ConfigMap, a cambio de no reiniciar. En prod los servicios núcleo tienen al menos 2 réplicas y el Deployment usa el rolling update por defecto, así que el reinicio no corta el servicio; en dev y stage (1 réplica) hay un corte breve, que ahí no importa |
| **Falla al arrancar si K < 1** | Ver arriba: no hay un valor "seguro" al que degradar en silencio |
| **Toggle encendido por defecto** | Un toggle nuevo no debe cambiar el comportamiento de quien no lo conoce |

## Trade-offs

| A favor | En contra |
|---|---|
| Se cambia el comportamiento en segundos con `helm upgrade`, sin compilar ni construir imágenes | Hay más estados posibles que probar: cada toggle duplica los caminos del código. Por eso las pruebas cubren los dos estados |
| El mismo artefacto (imagen) pasa de dev a stage a prod; solo cambian los valores | Un valor mal puesto en producción cambia el comportamiento sin que haya un commit que lo revele; mitigación: los valores viven en el repositorio y pasan por PR |
| El K queda en un solo lugar y no puede divergir | Cambiar el K **cambia qué datos se muestran**: bajarlo reduce la privacidad. Debe decidirlo quien responde por los datos, no quien despliega |
| Apagar LDAP quita la espera por un servidor que no existe | Un toggle olvidado se vuelve código muerto o una puerta lateral: hay que retirarlo cuando ya no se necesite |
| Cambiar la configuración deja registro: cada `helm upgrade` es una revisión y se puede volver atrás con `helm rollback` | El cambio de un ConfigMap reinicia los pods; si varios servicios leen el mismo ConfigMap, **todos** se reinician aunque el valor solo le importe a uno |

**Alternativas descartadas:** Spring Cloud Config Server (un servicio más que operar y proteger para dos valores); recarga en caliente con `spring-cloud-kubernetes` (ver arriba); un servicio de feature flags como Unleash o LaunchDarkly (útil para activar por usuario o por porcentaje, pero aquí el toggle es por ambiente y no justifica otro servicio ni otra dependencia).

## Cómo se prueba

- **Los dos estados del toggle** ([`LdapLoginToggleTest`](../../services/circleguard-auth-service/src/test/java/com/circleguard/auth/security/LdapLoginToggleTest.java), 7 pruebas): encendido, se prueba LDAP primero y la base local solo si LDAP falla; apagado, **LDAP nunca se invoca** y las credenciales malas siguen rechazándose. Una prueba con `ApplicationContextRunner` comprueba que la propiedad `circleguard.features.ldap-login-enabled=false` llega al componente y que, sin ninguna propiedad, el toggle queda encendido.
- **El K configurable** ([`KAnonymityConfigTest`](../../services/circleguard-dashboard-service/src/test/java/com/circleguard/dashboard/service/KAnonymityConfigTest.java), 9 pruebas): la misma población se enmascara con K=5 y se muestra con K=3; el valor sale de la propiedad (8), sin propiedad es 5 y un K de 0 impide arrancar. Las tendencias de entrada se enmascaran con el mismo K que el filtro.
- **El chart** ([`config_secret_ingress_test.yaml`](../../deploy/helm/circleguard/tests/config_secret_ingress_test.yaml)): el ConfigMap lleva las dos claves con sus valores por defecto y cambian con `--set` sin tocar las plantillas.
- **En un clúster real** (k3s local, la misma versión que instala Terraform): el mismo release se despliega con los valores por defecto y luego con `--set`; se comprueba el ConfigMap, las variables dentro del pod, el log de arranque de auth y que el login sigue funcionando. Ver la evidencia.
