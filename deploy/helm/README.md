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
./deploy/helm/run.sh check                     # helm lint de cada ambiente + 37 pruebas de las plantillas
./deploy/helm/run.sh template dev              # muestra lo que se instalaría en dev

# Instalar en un clúster (el pipeline hará esto en HU-16):
KUBECONFIG_FILE=/ruta/al/kubeconfig ./deploy/helm/run.sh helm upgrade --install circleguard deploy/helm/circleguard \
  --namespace circleguard-dev --create-namespace \
  -f deploy/helm/circleguard/values-dev.yaml -f /ruta/a/values-secrets.yaml \
  --set global.imageRegistry=<cuenta>.dkr.ecr.us-east-1.amazonaws.com --set global.imageTag=1.2.0 --wait

# Volver a la versión anterior (HU-25):
KUBECONFIG_FILE=/ruta/al/kubeconfig ./deploy/helm/run.sh helm rollback circleguard -n circleguard-dev
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
