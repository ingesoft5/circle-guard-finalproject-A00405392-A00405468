# CircleGuard Monorepo

**Absolute Privacy. High-Speed Containment. Secure Campus.**

CircleGuard is a state-of-the-art university contact tracing and fencing system designed to identify interconnected contact groups ("Circles") and apply rapid health fences while preserving individual anonymity.

---

## Vision & Mission

Our vision is a university campus where health containment speed outpaces lab confirmation timelines without compromising student privacy. CircleGuard leverages campus-native intelligence—class schedules and WiFi infrastructure—to deliver a human-validated, graph-based protection ecosystem.

### Key Differentiators
- **Privacy-as-Code**: Zero real-name exposure outside a secure Health Center vault.
- **Recursive Containment**: Status promotion cascades (Suspect → Probable → Confirmed) that trigger in milliseconds.
- **Campus Integration**: Smart check-ins using existing WiFi AP triangulation and Bluetooth Low Energy (BLE).

---

## Success Metrics

| Metric | Target | Measurement |
|:---|:---|:---|
| **Containment Speed** | < 60 Seconds | Automated test of promotion engine cascade |
| **Privacy Compliance** | 100% Anonymity | Penetration test on graph database (Zero real names) |
| **Check-in Adoption** | > 70% | Analytics on scheduled class contact validation |
| **False Positive Rate** | < 15% | Post-fence surveys of actual vs. suspected contact |
| **System Uptime** | 99.5% | 7:00 AM – 10:00 PM (Academic Peak Hours) |

---

## Architecture Overview

CircleGuard follows a **Microservice Architecture** built on a **Hybrid Data Model**.

### Core Engine
1. **Status Promotion Machine**: Uses **Neo4j** for recursive graph traversals to identify contacts within a 14-day temporal window.
2. **Anonymization Vault**: A segregated **PostgreSQL** vault handles salted-hash identity mapping, compliant with **FERPA** regulations.
3. **Event-Driven Core**: **Apache Kafka** manages asynchronous status changes, audit logs, and notification dispatches.

### Services Directory
- **Auth Service**: Dual-chain LDAP (University) / Local (Guest) auth with Dynamic RBAC.
- **Identity Service**: Cryptographic vault for anonymizing real identities.
- **Promotion Service**: The status engine (Recursive Graph Processing).
- **Notification Service**: Multi-channel dispatcher (Push/Email/SMS).
- **Form Service**: Dynamic health questionnaire engine.
- **Gateway Service**: Campus entry validation via signed, time-limited QR tokens.
- **Dashboard Service**: Geospatial hotspot analytics (Privacy-preserving).
- **File Service**: Secure certificate and document storage (S3-compatible).

---

## Technical Stack

| Layer | Technology | Rationale |
|:---|:---|:---|
| **Backend** | Spring Boot 3.2.4 / Java 21 | Enterprise-grade maturity & low-latency Jakarta EE support. |
| **Graph DB** | Neo4j 5.26 | High-performance recursive traversals unreachable with SQL. |
| **Relational DB**| PostgreSQL 16 | ACID compliant storage for identity and configuration. |
| **Message Bus** | Apache Kafka 3.8 (KRaft, no ZooKeeper) | Persistent, audit-trailed event log for status dispatches. |
| **Caching** | Redis 7.2 | L2 distributed cache for rapid entry-gate status validation. |
| **Mobile/Web** | Expo (React Native) + nginx | Unified codebase across iOS, Android, and Browser; the web build is served by nginx, which also proxies `/api/<service>/`. |
| **Infra** | Part 1: Docker Compose on EC2 (dev, stage, prod) · Part 2: Kubernetes | Isolated environments, same immutable images everywhere. |
| **CI/CD** | Jenkins (multibranch + Shared Library), SonarQube, Nexus, semantic-release | Every PR is built, tested and gated; every merge to `main` is versioned and promoted dev → stage → prod. See [`docs/arquitectura/`](docs/arquitectura/README.md). |

---

## Roadmap

### Phase 1: MVP — The Intelligence Core (Current)
- [x] Status Promotion Machine (Suspect → Probable → Confirmed).
- [x] Temporal graph with 14-day TTL edges.
- [x] Multi-channel fence notifications (Push/Email/SMS).
- [ ] Health Center de-identification console.

### Phase 2: Growth — Spatial Intelligence
- [ ] WiFi AP triangulation integration.
- [ ] Campus entry validation (Gatekeeper) QR integration.
- [ ] LMS integration for "Remote Attendance" status automation.

### Phase 3: Vision — Full Ecosystem
- [ ] Off-campus circle detection via P2P Bluetooth.
- [ ] Global Health Dashboard with hotspot visualization.
- [ ] Lab API bridge for automated test result ingestion.

---

## Operación (DevOps) — guía rápida

> Documentación del equipo (Ingeniería de Software V). Reglas de trabajo en [`CONTRIBUTING.md`](CONTRIBUTING.md); plan, sprints y métricas en [`docs/agil/`](docs/agil/PLAN-PROYECTO.md); uso de IA generativa en [`IAG_REGISTRO.md`](IAG_REGISTRO.md).

### Mapa de la documentación

| Tema | Dónde |
|---|---|
| Arquitectura, eventos de Kafka y pipeline (diagramas) | [`docs/arquitectura/`](docs/arquitectura/README.md) |
| Reconstruir la nube (AWS) con un comando, accesos | [`infra/bootstrap/README.md`](infra/bootstrap/README.md) |
| Imágenes, compose por ambiente, despliegue y rollback | [`deploy/compose/README.md`](deploy/compose/README.md) |
| Versionado automático y release notes | [`docs/release-notes/README.md`](docs/release-notes/README.md) |
| Gestión de cambios: tipos, aprobaciones, RFC, etiquetado | [`docs/change-management/politica.md`](docs/change-management/politica.md) |
| Sprints, burndown y evidencias | [`docs/agil/`](docs/agil), [`docs/evidencias/`](docs/evidencias) |

### Ambientes

Cada ambiente es una VM EC2 propia con su *security group* y su IP elástica. Se apagan cuando no se usan (`infra/bootstrap/down.sh`) y `up.sh` las vuelve a encender con la misma IP, así que las URL no cambian.

| Ambiente | URL | Recibe |
|---|---|---|
| Herramientas | Jenkins `http://44.221.115.232:8080` · SonarQube `http://44.221.115.232:9000` · Nexus `http://44.221.115.232:8081` | — |
| **dev** | `http://34.230.234.121` | Cada versión de `main`, automáticamente |
| **stage** | `http://3.209.207.195` | La misma versión, si los E2E contra dev pasan |
| **prod** | `http://50.19.116.114` | La misma versión, tras aprobación manual en Jenkins |

### Cómo llega un cambio a producción

1. Issue en el [tablero](https://github.com/orgs/ingesoft5/projects/8) → rama `<tipo>/<issue>-<descripcion>` → PR cuyo título es un Conventional Commit.
2. Jenkins corre la **CI del PR**: commitlint, build, pruebas unitarias con JaCoCo (≥ 60 % de líneas en los 5 servicios núcleo), SonarQube con *quality gate* bloqueante y pruebas de integración con Testcontainers. Solo construye los servicios que cambiaron.
3. El otro integrante aprueba y se hace *squash and merge* a `main` (protegida: PR obligatorio, 1 aprobación, también para administradores).
4. En **`main`**: semantic-release calcula `vX.Y.Z` y crea el tag y la Release; se publican las 9 imágenes en Nexus con ese tag (nunca `latest`); despliegue a **dev** → E2E con Playwright → **stage** → aprobación manual (`input`) → **prod**. Discord y WhatsApp avisan los fallos, la espera de aprobación y el resultado.
5. Volver atrás: job `circleguard-rollback` en Jenkins con `ENV` y `VERSION`.

### Comandos frecuentes

```bash
./infra/bootstrap/up.sh                                            # crea o enciende tools, dev, stage y prod (~3 min si ya existen)
./infra/bootstrap/up.sh tools dev                                  # solo Jenkins/Sonar/Nexus y dev
./infra/bootstrap/down.sh                                          # apaga todo; se conservan datos e IPs
./gradlew build                                                    # compila, unitarias y umbral de cobertura de los 8 servicios
./gradlew :services:circleguard-gateway-service:integrationTest    # pruebas de integración (necesita Docker)
./deploy/compose/compose.sh dev up -d --wait                       # el sistema completo en local
./docs/agil/metricas/burndown.sh "Sprint 1" 2026-11-05 2026-11-08  # foto diaria del burndown
```

---

## Local Development

### 1. Infrastructure
Ensure Docker is installed, then start the middleware stack:
```bash
docker-compose -f docker-compose.dev.yml up -d
```
*Middleware includes: PostgreSQL, Neo4j, Kafka, Zookeeper, Redis, and OpenLDAP.*

### 2. Secrets (environment variables)
The services no longer carry secrets in `application.yml`, and secrets have no default value: export them in the same shell before running any service. The database, Neo4j and LDAP passwords are the ones from `docker-compose.dev.yml`; the signing keys are throwaway values (the same one must be shared by every service).
```bash
export DB_USER=admin DB_PASSWORD=password NEO4J_PASSWORD=password LDAP_PASSWORD=admin
export JWT_SECRET=$(openssl rand -hex 32) QR_SECRET=$(openssl rand -hex 32)
export VAULT_SECRET=$(openssl rand -hex 32) VAULT_SALT=$(openssl rand -hex 8) VAULT_HASH_SALT=$(openssl rand -hex 8)
```
To run the whole system as containers (the same images the pipeline publishes) see [`deploy/compose/README.md`](deploy/compose/README.md).

### 3. Build & Run
CircleGuard uses Gradle for parallel builds across services:
```bash
# Start all microservices in parallel
./gradlew bootRun --parallel

# Start a specific service
./gradlew :services:<service-name>:bootRun
```

### 4. API Exploration
Every service exposes an OpenAPI 3.0 interface. Once running, visit:
`http://localhost:<service-port>/swagger-ui/index.html`

---

## Frontend Development

The frontend is built using **Expo (React Native)**, supporting iOS, Android, and Web from a single codebase located in `/mobile`.

### 1. Prerequisites
Ensure you have Node.js installed and dependencies loaded:
```bash
cd mobile
npm install
```

### 2. Run the Application
You can run the app in various modes depending on your target platform:

| Platform | Command | Notes |
|:---|:---|:---|
| **Development Menu** | `npm run start` | Opens the Expo Go start-up menu. |
| **Android** | `npm run android` | Requires Android Studio / Emulator or a connected device. |
| **iOS** | `npm run ios` | Requires macOS with Xcode / Simulator installed. |
| **Web Browser** | `npm run web` | Launches the dashboard/app in your default browser. |

### 3. Testing
To run frontend unit and component tests:
```bash
npm run test
```

---

## Testing

We maintain high system integrity via multi-level testing:

| Command | Scope |
|:---|:---|
| `./gradlew build` | Unit tests of the 8 services + JaCoCo report + line-coverage gate (≥ 60 % in the 5 core services) |
| `./gradlew :services:<name>:test` | Unit tests of a single service |
| `./gradlew :services:<name>:integrationTest` | Integration tests (`@Tag("integration")`) with **Testcontainers**: real Kafka, Neo4j and Redis containers. Needs Docker |
| `tests/e2e-playwright/run.sh <BASE_URL>` | End-to-end tests with Playwright against a deployed environment (the pipeline runs them against dev before promoting to stage) |

Coverage per service is published in Jenkins (JaCoCo) and SonarQube; the pipeline fails if a core service drops below 60 % or the SonarQube quality gate is red.

---

## Privacy & Compliance

- **FERPA Compliance**: Student identities are never stored in the contact graph.
- **Right to be Forgotten**: Users can trigger complete data purging via the Identity Vault.
- **Temporal Privacy**: All contact edges are automatically purged after 14 days.
