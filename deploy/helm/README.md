# CircleGuard en Kubernetes (HU-15, E2.2)

Un solo chart de Helm, [`circleguard/`](circleguard), despliega los 8 servicios Spring Boot, el cliente web y el middleware (Postgres, Neo4j, Kafka y Redis) en un clúster k3s, con un namespace por ambiente. Reemplaza al compose de la Parte 1 ([`deploy/compose/`](../compose/README.md)): usa **las mismas imágenes y las mismas variables**, y los Service se llaman igual (`auth`, `identity`, `form`...), así que no se reconstruyó ninguna imagen.

Todo se ejecuta por Docker con versiones fijas, como `infra/terraform/run.sh`: no hay que instalar Helm ni kubectl.

## Qué trae el chart

| Pieza | Dónde | Qué hace |
|---|---|---|
| Deployment + Service por servicio | `templates/deployment.yaml`, `service.yaml` | Un `range` sobre `services:` de `values.yaml`: los 8 servicios y la web salen de la misma plantilla |
| **ConfigMap** `circleguard-config` | `templates/configmap.yaml` | Configuración no secreta compartida (hosts de Postgres, Kafka, Redis y Neo4j, ambiente) |
| **Secret** `circleguard-secrets` | `templates/secret.yaml` | Claves de la base, JWT, QR y bóveda. **Obligatorias**: si falta una, `helm` falla. Cada servicio recibe solo las que usa. Con `existingSecret` el chart usa uno ya existente (Sealed/External Secrets, HU-23) |
| **Ingress** | `templates/ingress.yaml` | Todo entra por la web (nginx), que hace de proxy de `/api/<servicio>/`. Usa el Traefik que trae k3s |
| **HPA** | `templates/hpa.yaml` | `autoscaling/v2` por CPU (70 %), mínimo y máximo por servicio y por ambiente |
| **Sondas de Actuator** | `templates/deployment.yaml` | `startup` (hasta 5 min), `liveness` y `readiness` contra `/actuator/health/...`; la web usa `/healthz` |
| Middleware | `templates/postgres.yaml`, `neo4j.yaml`, `kafka.yaml`, `redis.yaml` | StatefulSet con volumen (Postgres, Neo4j, Kafka en KRaft) y Deployment (Redis), con las imágenes y variables del compose |
| Espera de dependencias | `initContainers` | Cada servicio espera (`nc -z`) a lo que necesita antes de arrancar, en lugar del `depends_on` del compose |

Valores por ambiente: [`values-dev.yaml`](circleguard/values-dev.yaml), [`values-stage.yaml`](circleguard/values-stage.yaml) y [`values-prod.yaml`](circleguard/values-prod.yaml). dev y stage (nodo `t3.large`, 8 GB) apagan dashboard, file y notification; prod (`t3.xlarge`) corre los 8 y al menos 2 réplicas de lo núcleo.

## Usarlo

```bash
./deploy/helm/run.sh check                     # helm lint de cada ambiente + pruebas de las plantillas + selftest de release.sh
./deploy/helm/run.sh template dev              # muestra lo que se instalaría en dev

# Instalar en un clúster (el pipeline hará esto en HU-16): ver «Desplegar, revertir y registro de cambios»
KUBECONFIG_FILE=/ruta/absoluta/al/kubeconfig SECRETS_FILE=deploy/helm/.local/values-secrets.yaml \
  ./deploy/helm/release.sh deploy dev 1.2.0 --commit "$(git rev-parse HEAD)" --build 42
```

Los secretos **no** van en el repo: [`values-secrets.example.yaml`](circleguard/values-secrets.example.yaml) es solo un ejemplo con `CAMBIAR`.

## Probarlo sin gastar nada de AWS

[`local.sh`](local.sh) levanta un k3s dentro de Docker, **de la misma versión** que Terraform instala en los nodos de AWS (`rancher/k3s:v1.37.2-k3s1`), carga las imágenes que construyas y despliega el chart:

```bash
# 1. Construir las imágenes (contexto = raíz del repo) y etiquetarlas local/<nombre>:local
docker build -f services/circleguard-auth-service/Dockerfile -t local/circleguard-auth-service:local .   # y las demás
docker build -f mobile/Dockerfile -t local/circleguard-web:local .

./deploy/helm/local.sh up        # crea el clúster
./deploy/helm/local.sh images    # carga las imágenes local/circleguard-*:local
./deploy/helm/local.sh deploy    # helm upgrade --install con values-local.yaml y secretos aleatorios (los argumentos extra van a helm: --set config.privacy.kAnonymity=8)
./deploy/helm/local.sh smoke     # web, sondas, versión y login a través del Ingress
./deploy/helm/local.sh down      # lo borra
```

`values-local.yaml` despliega los 5 servicios núcleo y la web (la memoria del PC no alcanza para los 8). Los otros tres se validan con `helm template` y las pruebas de plantillas, pero no se ejecutan localmente.

## Desplegar, revertir y registro de cambios

[`release.sh`](release.sh) es la única puerta para cambiar un ambiente (HU-25); lo usa el operador y, desde HU-16, el pipeline. Necesita `KUBECONFIG_FILE` (ruta absoluta) y, para desplegar, `SECRETS_FILE` (valores secretos dentro del repo y fuera de git).

| Comando | Qué hace |
|---|---|
| `release.sh deploy <ambiente> <versión> [--commit <sha>] [--build <n>]` | `helm upgrade --install` con `--atomic`: si los pods no quedan listos en `HELM_TIMEOUT` (10 min), Helm **revierte solo** a la revisión anterior. El motivo queda en el historial: `deploy 1.2.0 · commit abc1234 · build #42` |
| `release.sh rollback <ambiente> [<revisión>] [--yes]` | `helm rollback` a la revisión indicada o, sin número, a la **última revisión buena con una versión distinta de la activa**. En `prod` exige `--yes` |
| `release.sh history <ambiente> [--md]` | El registro de cambios del ambiente: revisión, fecha, estado, **versión** y motivo |
| `release.sh selftest` | Pruebas de la lógica que elige la revisión de destino (no necesitan clúster; corren en `run.sh check`) |

**Por qué el registro es el historial de Helm** y no un archivo aparte: ya vive en el clúster de cada ambiente (un historial por namespace, así que ya es *por ambiente*), no se puede olvidar de escribir porque Helm lo crea con cada cambio, y se puede volver a cualquier revisión. Se sube el límite de revisiones a 50 (`--history-max`; el valor por defecto de Helm es 10 y borraría el registro demasiado pronto). Qué versión corría en cada revisión no viene en `helm history`; sale de los valores de esa revisión y por eso `history --md` la agrega.

**Por qué «versión distinta de la activa»:** después de un rollback, la revisión anterior suele ser la misma versión que ya corre; volver a ella no arreglaría nada.

Probado en el k3s local con un despliegue, una versión que falla (se revierte sola) y un rollback manual: [`hu-25-rollback.md`](../../docs/evidencias/sprint-2/hu-25-rollback.md). El rollback de la **aplicación** (imágenes) no deshace cambios de **datos**: una migración incompatible sigue necesitando la RFC de emergencia de la [política de cambios](../../docs/change-management/politica.md#7-rollback).

## Decisiones

- **Un chart, no ocho:** los 8 servicios son iguales salvo imagen, puerto, base de datos y secretos, así que un `range` evita ocho copias y deja una sola versión por ambiente (`helm rollback` vuelve a la anterior).
- **Plantillas propias del middleware, no subcharts de Bitnami:** Bitnami retiró sus imágenes públicas y los subcharts traen mucha configuración que no usamos. Así lo probado en el compose es lo que corre en Kubernetes.
- **Valores por defecto con anclas de YAML** (`<<: *svc`) y no con la función `merge` de Helm, porque `merge` pisa los `false` y un `enabled: false` por ambiente no funcionaría.
- **`enableServiceLinks: false`:** Kubernetes inyecta variables como `KAFKA_PORT` por cada Service, y la imagen de Kafka las lee como configuración.
- **nginx con nombres completos** (`auth.<namespace>.svc.cluster.local`): el `resolver` de nginx no usa los dominios de búsqueda del pod, y el `resolver` apunta al DNS de k3s (`dnsResolver`).
- **Kafka con `fix-permissions`:** el volumen `local-path` nace como root y Kafka corre como usuario 1000.

## Limitaciones

- La instalación real en AWS queda para una ventana corta y planeada (el presupuesto de nube casi se agotó); el mismo chart se probó en un k3s local de la misma versión.
- TLS (cert-manager), RBAC y NetworkPolicies llegan en HU-23.
