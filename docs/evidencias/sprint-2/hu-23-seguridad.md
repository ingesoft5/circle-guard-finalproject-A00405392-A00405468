# HU-23 · Seguridad: escaneo continuo, secretos, RBAC y TLS — evidencia (E2.6)

Issue [#30](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/30) · Sprint 2. Diseño y razones: [`docs/arquitectura/seguridad.md`](../../arquitectura/seguridad.md). Los secretos del historial de git están en [`hu-23-secretos.md`](hu-23-secretos.md).

**Dónde se probó:** un k3s local en Docker de la **misma versión que instala Terraform** (`v1.37.2+k3s1`), con las imágenes reales construidas del código actual y los secretos sellados por Sealed Secrets. **No se probó en AWS** (presupuesto): lo que solo se puede comprobar allí está en «Limitaciones».

## Criterios de aceptación

| Criterio del issue | Resultado | Evidencia |
|---|---|---|
| Trivy y escaneo de dependencias continuo | **Hecho** como compuerta bloqueante local: IaC, dependencias npm e imágenes (HIGH/CRITICAL). **Falta conectarlo al Jenkinsfile (HU-16)** | [`hu-23-check.txt`](hu-23-check.txt), [`hu-23-trivy-imagenes.txt`](hu-23-trivy-imagenes.txt) |
| Cero credenciales en el repositorio (Sealed Secrets) | **Hecho:** gitleaks sin hallazgos en el historial y en lo que git versionaría; el cluster recibe los secretos por un `SealedSecret` cifrado | [`hu-23-check.txt`](hu-23-check.txt), [`hu-23-sealed.txt`](hu-23-sealed.txt) |
| RBAC en Kubernetes | **Hecho y probado contra el cluster:** los pods no tienen permisos ni token; el rol de solo lectura no ve Secrets | [`hu-23-k3s-seguridad.txt`](hu-23-k3s-seguridad.txt) |
| RBAC en Jenkins | **Configurado, sin ejercer:** tres niveles en `jenkins.yaml`; se comprueba al reiniciar Jenkins en la ventana de HU-16 | [`jenkins.yaml`](../../../infra/tools/jenkins/casc/jenkins.yaml) |
| TLS verificable | **Hecho en local:** certificado emitido por cert-manager con una CA propia, verificado con ella, rechazado sin ella, y el HTTP redirige | [`hu-23-k3s-tls.txt`](hu-23-k3s-tls.txt) |
| TLS con certificado válido (Let's Encrypt sobre `*.sslip.io`) | **Preparado, NO probado:** los emisores `letsencrypt` y `letsencrypt-staging` están en el chart de plataforma con pruebas de plantilla; emitir el certificado real necesita una IP pública y el puerto 80 | [`deploy/helm/platform`](../../../deploy/helm/platform) |
| Secretos del código base eliminados del historial o rotados | **Hecho:** rotados en la práctica y con compuerta para que no vuelvan (decisión documentada) | [`hu-23-secretos.md`](hu-23-secretos.md) |

## Lo que se midió

**Pods.** Al renderizar el chart, Trivy encontró **44 hallazgos HIGH** en los Deployments (sin contexto de seguridad ni disco de solo lectura). Con el cambio, los 9 Deployments de la aplicación quedan en **0**; el middleware (Postgres, Kafka, Neo4j, Redis) conserva 9 hallazgos aceptados con motivo y vencimiento.

**Imágenes.** La web pasó de **68 hallazgos (65 HIGH, 3 CRITICAL) a 0** subiendo la base a `nginx 1.30` y con `apk upgrade`. Los 8 servicios Java no tienen **ningún** hallazgo en el sistema operativo; tienen **74 CVE únicos** en las librerías (de 43 a 73 por servicio, 9 a 12 CRITICAL) que vienen de Spring Boot 3.2.4. Se registran como línea base con vencimiento; el arreglo es subir Spring Boot. Detalle por servicio en [`hu-23-trivy-imagenes.txt`](hu-23-trivy-imagenes.txt).

**Dependencias npm.** 74 hallazgos (73 HIGH, 1 CRITICAL) en 17 paquetes de la web, todos de la cadena de compilación de Expo y ninguno dentro de la imagen final; aceptados por CVE con vencimiento. `npm audit fix` reescribía ~9.000 líneas del lockfile, así que se descartó para este PR.

**Cluster real** (`security-smoke`, cada «bloqueado» tiene un control positivo en el mismo mecanismo):

| Comprobación | Resultado |
|---|---|
| El rol `viewer` lee pods; **no** lee Secrets, no borra pods, no crea Deployments | ok |
| Los pods (`circleguard-workload`) no pueden leer pods ni Secrets | ok |
| `auth` no tiene el token de la API montado, no escribe en su imagen, sí en `/tmp`, corre como usuario 100 | ok |
| `auth → postgres` permitido; `gateway → postgres` bloqueado | ok |
| `gateway → promotion/internal/...` responde (HTTP real); `auth → promotion/internal/...` queda sin respuesta | ok |
| Un pod con la etiqueta de `web` llega a `auth`; el mismo pod sin etiqueta, no | ok |
| `/api/promotion/internal/...` por el Ingress (desde fuera) | HTTP 404 |

## Lo que encontró el trabajo

1. **Trivy salía en verde sin mirar el chart.** Un chart con `required` no se puede renderizar sin valores y Trivy lo salta en silencio: 0 hallazgos. Al pasarle `--helm-set` aparecieron 44. El runner ahora exige que el resumen nombre el chart, los Dockerfile y Terraform.
2. **El escaneo de secretos del árbol leía los archivos ignorados por git.** Con el cluster local levantado, `deploy/helm/.local/` (kubeconfig y secretos de la prueba) hacía fallar `check` con 6 hallazgos que nunca irían al repositorio. Ahora escanea lo que git versionaría y se probó con un secreto nuevo sin commitear (lo detecta).
3. **Una comprobación negativa sin control positivo no prueba nada.** El primer `security-smoke` pasó entero, pero «bloqueado» podía ser un pod de prueba que no arrancaba. Se agregó un control (misma prueba con y sin la etiqueta) y, al agregar la ruta `/internal` con HTTP real, el control positivo **falló**: la imagen de `promotion` era anterior a #69 y devolvía 404. Se reconstruyeron las imágenes y entonces pasó. Las imágenes viejas habrían dado un «bloqueado» falso.
4. **Las pruebas de plantillas (76) no vieron lo que vio el cluster real:** la prueba de HTTPS mostró el certificado por defecto de Traefik justo después de que cert-manager lo emitiera (Traefik tarda unos segundos en cargarlo), y la redirección es un 301 y no un 308. Se ajustó la prueba, no el chart.
5. **Un endurecimiento que no se probó en un cluster real habría roto algo.** Con el disco de solo lectura, nginx necesita `/etc/nginx/conf.d` y el servicio de archivos `/app/uploads` escribibles; los 10 pods arrancaron a la primera porque esas rutas ya estaban en el diseño, y la prueba funcional (login real) confirma que la aplicación sigue funcionando.
6. **El servicio de archivos no podía guardar subidas antes de este cambio** (comprobado en la imagen: `/app` es de root y `mkdir /app/uploads` falla como usuario `app`). Con el volumen efímero en `/app/uploads` ahora sí puede (los archivos se pierden al reiniciar el pod y cada réplica guarda los suyos: con 2 réplicas una subida puede no verse desde la otra; guardarlos de verdad es otra historia y conviene decidirlo antes de depender de este servicio).

## Limitaciones

- **Nada de esto corre aún en Jenkins** (HU-16): hoy es `./security/run.sh` a mano. La compuerta bloqueante en el pipeline se prueba en la ventana de Jenkins.
- **Let's Encrypt real y RBAC de Jenkins no se ejercieron** (necesitan nube). TLS autofirmado se justifica por ser un cluster local sin dominio público.
- **La deuda de Spring Boot 3.2.4 (74 CVE) sigue abierta.** Es el riesgo más importante y se corrige en una historia propia después de la observabilidad, porque ambas tocan las dependencias de los 8 servicios.
- **La defensa de `/internal` es de red** (pod y puerto), no de ruta; `dashboard` y `web` podrían llamarla. Cerrarla del todo exige autenticación entre servicios.
- **Las NetworkPolicies son solo de entrada** y el middleware corre con las excepciones de seguridad descritas en [`seguridad.md`](../../arquitectura/seguridad.md#los-pods).
- **Un `SealedSecret` no sobrevive a un cluster nuevo** (cada cluster tiene su llave): se vuelve a sellar en cada ventana de nube.
- `pierrecuevas` pasa de administrador a desarrollador en Jenkins; el equipo debe estar de acuerdo antes de la ventana. **Plan de reversión:** si en la ventana el `input` de prod o el reescaneo dan 403, devolverle `Overall/Administer` en `jenkins.yaml` (una línea) y reiniciar Jenkins; la aprobación a prod solo se prueba allí.
- La web se reconstruye con `apk upgrade`, así que dos construcciones de la misma versión en fechas distintas pueden traer paquetes distintos: se aceptó para cerrar 68 hallazgos de la base; la alternativa es fijar el digest de una base ya parchada.
