# Arquitectura desplegada de la Parte 2 (HU-26, E2.9)

Dos vistas de lo que se despliega: **la nube** (qué crea Terraform y cómo se conecta Jenkins) y **un cluster** (qué corre dentro de cada ambiente). El diagrama de Terraform con sus módulos está en [`infra-terraform.md`](infra-terraform.md); el pipeline, en [`pipeline-k8s.md`](pipeline-k8s.md); la seguridad, en [`seguridad.md`](seguridad.md).

## 1. La nube: cuatro estados de Terraform y un estado compartido

```mermaid
flowchart TB
  GH[GitHub: PR y main] -->|webhook| J
  subgraph tools["VPC tools · 10.0.0.0/16 (envs/tools)"]
    J["Jenkins · SonarQube · Nexus<br/>t3.xlarge"]
    ECR[("ECR: 9 repositorios<br/>tags inmutables")]
  end
  subgraph amb["Un ambiente (dev, stage o prod)"]
    SG["Security group<br/>22 y 6443 solo admin + tools · 80/443 abiertos"]
    NODE["EC2 k3s v1.37.2<br/>dev y stage t3.large · prod t3.xlarge<br/>disco gp3 cifrado · IMDSv2"]
    S3D[("S3 de datos")]
    CW["CloudWatch → SNS (alarmas por correo)"]
  end
  STATE[("S3 del estado de Terraform<br/>versionado y cifrado<br/>+ tabla DynamoDB de bloqueo")]
  J -->|"circleguard-infra: plan → aprobación → apply"| amb
  J -->|"SSH: kubeconfig · helm --atomic"| NODE
  J -->|"ecrPublish"| ECR
  NODE -->|"rol IAM de la EC2: baja imágenes, sin contraseñas"| ECR
  amb -.->|"estado remoto con lock"| STATE
  J -.->|"circleguard-drift: plan diario"| STATE
```

- **Una VPC por ambiente** (dev `10.10.0.0/16`, stage `10.20.0.0/16`, prod `10.30.0.0/16`; cada una con su propio estado de Terraform), una subred pública, sin NAT Gateway (≈ USD 32/mes cada uno): el aislamiento lo da la red propia y los grupos de seguridad. Un fallo o una prueba de caos en `dev` no toca `prod`.
- **Un solo nodo por ambiente** (k3s): es Kubernetes certificado y cabe en el presupuesto; EKS habría costado ≈ USD 216/mes solo en planos de control (ver [`costos.md`](../operaciones/costos.md)).
- Antes del *cutover* (importar las 4 IP elásticas y destruir las VMs de Bash), Jenkins sigue en la VM de la Parte 1 y `envs/tools` solo aporta los repositorios ECR (`TARGET=module.registry`).

## 2. Dentro de un cluster (un ambiente)

```mermaid
flowchart LR
  U([Usuario]) -->|"HTTPS · <ip>.sslip.io"| T["Traefik (Ingress)<br/>certificado de cert-manager<br/>HTTP redirige a HTTPS"]
  subgraph app["Namespace de la aplicación"]
    W[web · nginx] --> A[auth] & I[identity] & F[form] & P[promotion] & G[gateway] & D[dashboard] & FI[file] & N[notification]
    A --> I
    D --> P
    G --> P
    A & I & F & P & D --> PG[("PostgreSQL")]
    I & F & P & N --> K[("Kafka")]
    P --> NEO[("Neo4j")]
    G & P --> R[("Redis")]
  end
  T --> W
  subgraph plat["Plataforma del cluster"]
    CM["cert-manager<br/>CA propia (local) · Let's Encrypt (nube)"]
    SS["Sealed Secrets<br/>el Secret circleguard-secrets lo crea el controlador"]
  end
  CM -.->|emite el certificado| T
  SS -.->|crea circleguard-secrets| app
```

- El namespace se llama `circleguard-<ambiente>` y su **NetworkPolicy cierra toda la entrada salvo la tabla `networkPolicy.allow`**.
- Cada servicio corre **sin root, con disco de solo lectura, seccomp y sin token de la API de Kubernetes**; el middleware es la excepción documentada (imágenes oficiales que arrancan como root).
- Sondas `startup`, `liveness` y `readiness` contra `/actuator/health/...`, y HPA por CPU (70 %). En `dev` y `stage` (8 GB) `dashboard`, `file` y `notification` están apagados; `prod` corre los 8.
- La tabla de qué servicio puede llamar a cuál, y por qué `promotion/internal` solo la alcanzan la web, el dashboard y el gateway, está en [`seguridad.md`](seguridad.md#quién-puede-llamar-a-quién).

## 3. Dónde está cada cosa

| Tema | Documento | Código |
|---|---|---|
| Terraform: módulos, ambientes, estado remoto | [`infra-terraform.md`](infra-terraform.md), [`infra/terraform/README.md`](../../infra/terraform/README.md) | `infra/terraform/` |
| Kubernetes: chart, probes, HPA, Ingress | [`deploy/helm/README.md`](../../deploy/helm/README.md) | `deploy/helm/` |
| Pipeline, Trivy, despliegue y drift | [`pipeline-k8s.md`](pipeline-k8s.md) | `Jenkinsfile`, `jenkins/` |
| Seguridad (red, pods, secretos, TLS, RBAC) | [`seguridad.md`](seguridad.md) | `security/`, `deploy/helm/platform/` |
| Patrones de diseño | [`docs/patrones/`](../patrones/README.md) | `services/` |
| Costos y manual de operaciones | [`costos.md`](../operaciones/costos.md), [`manual-operaciones.md`](../operaciones/manual-operaciones.md) | |
