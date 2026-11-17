# HU-15 · Los 8 servicios y el middleware en Kubernetes — evidencia (E2.2)

Issue [#22](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/22) · Sprint 2. Chart y decisiones en [`deploy/helm/`](../../../deploy/helm/README.md).

## Dónde se probó (y por qué no en AWS)

El presupuesto de AWS del proyecto casi se agotó (USD 45,34 de 50 el 17 de nov), así que el chart se validó en **un k3s local dentro de Docker, de la misma versión que Terraform instala en los nodos** (`rancher/k3s:v1.37.2-k3s1`), con las imágenes reales construidas del código. La instalación en un nodo de AWS queda para una ventana corta y planeada.

## Criterios del issue

| Criterio | Resultado | Evidencia |
|---|---|---|
| Namespace por ambiente | El chart no fija el namespace: se instala con `--namespace circleguard-<ambiente>` y la web usa `{{ .Release.Namespace }}` para resolver a los servicios | [`values-dev|stage|prod.yaml`](../../../deploy/helm/circleguard), [`hu-15-k3s-local-estado.txt`](hu-15-k3s-local-estado.txt) |
| ConfigMaps | `circleguard-config` con la configuración no secreta compartida | [`templates/configmap.yaml`](../../../deploy/helm/circleguard/templates/configmap.yaml) |
| Secrets | `circleguard-secrets`, obligatorios (Helm falla si falta uno) y mínimos por servicio; `existingSecret` para Sealed/External Secrets (HU-23) | [`templates/secret.yaml`](../../../deploy/helm/circleguard/templates/secret.yaml) |
| Ingress | Un Ingress de Traefik (el de k3s) hacia la web, que hace de proxy de `/api/<servicio>/` | [`templates/ingress.yaml`](../../../deploy/helm/circleguard/templates/ingress.yaml), [`hu-15-smoke.txt`](hu-15-smoke.txt) |
| HPA | `autoscaling/v2` por CPU (70 %) en los 6 servicios desplegados, leyendo métricas reales (`cpu: 5%/70%`) | [`hu-15-k3s-local-estado.txt`](hu-15-k3s-local-estado.txt) |
| Sondas de Actuator | `startup`, `liveness` y `readiness` contra `/actuator/health/...`; la web usa `/healthz` | [`templates/deployment.yaml`](../../../deploy/helm/circleguard/templates/deployment.yaml) |
| Los 8 servicios y el middleware | Los 8 servicios y la web salen de la misma plantilla; Postgres, Neo4j, Kafka y Redis con las imágenes del compose | `helm template` y las pruebas de plantillas |

## Resultados

- **Pruebas de plantillas (helm-unittest) y lint de los 4 ambientes:** 38 pruebas en 4 suites, `helm lint --strict` sin errores en dev, stage, prod y local — [`hu-15-lint-y-pruebas.txt`](hu-15-lint-y-pruebas.txt).
- **Instalación real en k3s local:** 10 pods `Running` (5 servicios núcleo, la web, Postgres, Neo4j, Kafka y Redis), 6 HPA, 1 Ingress, release de Helm en `deployed` — [`hu-15-k3s-local-estado.txt`](hu-15-k3s-local-estado.txt).
- **Prueba de humo a través del Ingress:** `/healthz`, `/version` (devuelve la versión desplegada) y las sondas de readiness de auth, identity, form, promotion y gateway dan **200**, y el **login real de `super_admin`** devuelve token: la cadena web → nginx → auth → Postgres → identity funciona dentro de Kubernetes — [`hu-15-smoke.txt`](hu-15-smoke.txt).

## Lo que encontró la prueba real (y que las pruebas de plantillas no veían)

1. **Neo4j no arrancaba:** su imagen toma **toda** variable `NEO4J_*` como un ajuste de configuración; el chart definía `NEO4J_PASSWORD` solo para armar `NEO4J_AUTH` y el pod caía con «Unrecognized setting: PASSWORD». El compose no lo sufría porque pone la clave directo en `NEO4J_AUTH`. Se renombró a `CG_NEO4J_PASSWORD` y quedó una prueba de regresión.
2. **Una imagen no se había importado al clúster, sin avisar** (`ErrImageNeverPull` en `auth`): `ctr images import` terminó sin error y no importó nada. `local.sh images` ahora comprueba que cada imagen quedó cargada.
3. **Helm dejó la release en `failed`** porque `--wait` esperó 10 min a ese pod. Se reinstaló (es idempotente) y quedó `deployed`.

## Limitaciones

- **No se ejecutaron dashboard, file y notification** (la memoria del PC no alcanza para los 8 más Neo4j y Kafka): sus manifiestos se validan con `helm template`, `helm lint` y las pruebas de plantillas, no corriendo. En prod (`t3.xlarge`) están habilitados; en dev y stage (8 GB) quedan apagados.
- **El HPA se comprobó leyendo métricas, no escalando bajo carga:** la prueba de carga (Locust) es HU-24.
- **Sin TLS, RBAC ni NetworkPolicies todavía:** HU-23.
- **No se instaló en un nodo de AWS:** pendiente de una ventana de prueba corta (un ambiente a la vez por el límite de 5 IP elásticas y el presupuesto).
