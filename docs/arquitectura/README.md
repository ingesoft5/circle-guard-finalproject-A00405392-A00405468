# Arquitectura de CircleGuard y del pipeline (HU-12, #19)

Diagramas en Mermaid (GitHub los dibuja). Fuente de verdad: el código; si algo cambia, se actualiza aquí en el mismo PR.

## 1. Servicios, eventos y datos

8 servicios Spring Boot 3.2.4 (Java 21) y un cliente web (Expo exportado a estáticos y servido por nginx, que además es el *reverse proxy* `/api/<servicio>/...`). En la Parte 1 se despliegan los **5 servicios núcleo** más el web; dashboard, file y notification tienen imagen publicada pero no se despliegan (memoria de las VMs de 8 GB).

```mermaid
flowchart LR
    subgraph cliente["Cliente (navegador o app)"]
        web["web · Expo + nginx<br/>:80 → /api/&lt;svc&gt;/"]
    end

    subgraph nucleo["Servicios núcleo (Parte 1)"]
        auth["auth :8180<br/>login LDAP/local, JWT, QR firmado"]
        identity["identity :8083<br/>bóveda: identidad real ↔ anonymousId"]
        form["form :8086<br/>cuestionario de salud, certificados"]
        promotion["promotion :8088<br/>máquina de estados Suspect→Probable→Confirmed"]
        gateway["gateway :8087<br/>valida el QR en la portería"]
    end

    subgraph otros["Otros servicios (imagen publicada, no desplegados en P1)"]
        notification["notification :8082<br/>correo, SMS, LMS"]
        dashboard["dashboard :8084<br/>analítica por facultad"]
        file["file :8085<br/>archivos y certificados"]
    end

    subgraph datos["Middleware"]
        pg[("PostgreSQL 16<br/>una BD por servicio")]
        neo[("Neo4j 5.26<br/>grafo de contactos, aristas con TTL 14 días")]
        kafka[["Kafka 3.8 (KRaft)"]]
        redis[("Redis 7.2<br/>user:status:&lt;id&gt;")]
    end

    web --> auth & identity & form & promotion & gateway
    auth -->|"POST /api/v1/identities/map"| identity
    dashboard -->|REST| promotion

    auth --> pg
    identity --> pg
    form --> pg
    promotion --> pg & neo & redis
    gateway --> redis

    form -->|"survey.submitted<br/>certificate.validated"| kafka
    kafka -->|"survey.submitted<br/>certificate.validated"| promotion
    promotion -->|"promotion.status.changed<br/>circle.fenced · alert.priority"| kafka
    kafka -->|"promotion.status.changed<br/>circle.fenced · alert.priority"| notification
    identity -->|"audit.identity.accessed"| kafka
    notification -->|"notification.audit"| kafka
```

**Flujo principal que prueban las pruebas de integración y los E2E:**

1. El usuario inicia sesión en **auth** (`super_admin`/`password` en local). auth pide a **identity** el `anonymousId` y firma un JWT; con él emite un **QR firmado** (HS256, vence a los 5 min).
2. El usuario responde el cuestionario en **form**, que publica `survey.submitted`.
3. **promotion** consume el evento: si hay síntomas, pasa al usuario a `SUSPECT` en Neo4j, propaga a sus contactos (`SUSPECT` → `PROBABLE` → `CONFIRMED`), escribe `user:status:<anonymousId>` en Redis y publica `promotion.status.changed`.
4. El guardia valida el QR en **gateway**: verifica la firma y consulta Redis. `SUSPECT`, `PROBABLE` o `CONFIRMED` → `RED` (acceso negado); lo demás → `GREEN`.
5. **notification** consume `promotion.status.changed` y despacha el aviso (correo/SMS) y la asistencia remota en el LMS.

La identidad real solo vive en la bóveda de identity (cifrada con `VAULT_SECRET`); el grafo y Redis solo conocen el `anonymousId`.

## 2. Ambientes e infraestructura (Parte 1)

```mermaid
flowchart TB
    subgraph aws["AWS us-east-1 · cuenta propia · creado por infra/bootstrap/up.sh"]
        subgraph tools["circleguard-tools · t3.xlarge 16 GB · SG tools"]
            jenkins["Jenkins 2.580 (JCasC)<br/>:8080"]
            sonar["SonarQube 26.9<br/>:9000"]
            nexus["Nexus 3.96<br/>UI :8081 · registro Docker :8082"]
        end
        subgraph dev["circleguard-dev · t3.large 8 GB · SG dev"]
            cdev["docker compose<br/>5 núcleo + middleware + web :80"]
        end
        subgraph stage["circleguard-stage · t3.large · SG stage"]
            cstage["docker compose"]
        end
        subgraph prod["circleguard-prod · t3.large · SG prod"]
            cprod["docker compose"]
        end
    end
    gh["GitHub<br/>repo + webhook"] -->|push / PR| jenkins
    jenkins -->|"ssh ubuntu@&lt;env&gt; (deploy-ssh)"| cdev & cstage & cprod
    cdev & cstage & cprod -->|"pull :8082 (misma imagen)"| nexus
    jenkins -->|análisis + quality gate| sonar
    jenkins -->|push imágenes X.Y.Z| nexus
```

- Cada ambiente es una VM, un *security group* y una IP elástica propios. SSH a los ambientes solo desde las IPs del equipo y desde el SG de tools; el registro de Nexus solo desde los SG de los ambientes; el puerto 80 de cada ambiente es público.
- Los secretos de cada ambiente se generan **en su VM** la primera vez que se despliega (`deploy.sh`) y nunca pasan por el repo ni por Jenkins.
- Apagar y encender: `infra/bootstrap/down.sh` / `up.sh` (idempotente). Ver [`infra/bootstrap/README.md`](../../infra/bootstrap/README.md).

## 3. Pipeline (CI en cada PR, CD en `main`)

```mermaid
flowchart LR
    subgraph ci["CI · en cada PR y en main"]
        prep["Preparar<br/>changedServices()"] --> lint["Commitlint<br/>título del PR y commits"]
        lint --> build["Build"] --> test["Unit tests + JaCoCo<br/>umbral 60 % por servicio"]
        test --> sonarq["SonarQube<br/>quality gate bloqueante"] --> it["Integration tests<br/>Testcontainers"]
    end
    subgraph cd["CD · solo en main"]
        rel["Release<br/>semantic-release → vX.Y.Z"] --> pub["Package + Publish<br/>9 imágenes X.Y.Z + sha"]
        pub --> ddev["Deploy dev<br/>+ smoke"] --> e2e["E2E Playwright<br/>contra dev"]
        e2e --> dstage["Deploy stage<br/>+ smoke"] --> input{"input<br/>¿promover a prod?"}
        input -->|aprobado| dprod["Deploy prod<br/>+ smoke"]
        input -->|2 h sin respuesta| abort["Abortado<br/>(queda en dev y stage)"]
    end
    it --> rel
    sonarq -. "gate en rojo:<br/>check del PR falla" .-> stop["No se puede fusionar"]
    e2e -. "E2E en rojo" .-> stop2["No se promueve a stage"]
    rb["Job circleguard-rollback<br/>ENV + VERSION"] -.->|"deployCompose con<br/>versión anterior"| ddev & dstage & dprod
```

| Pieza | Dónde |
|---|---|
| Pipeline declarativo | [`Jenkinsfile`](../../Jenkinsfile) |
| Shared Library (pasos reutilizables) | [`jenkins/vars/`](../../jenkins/vars) · `changedServices`, `commitLint`, `runGradle`, `sonarScan`, `dockerPublish`, `semanticRelease`, `deployCompose`, `smokeTest`, `e2eTests`, `releaseHistory`, `notifyTeam` |
| Job de rollback | [`jenkins/rollback.Jenkinsfile`](../../jenkins/rollback.Jenkinsfile), creado por JCasC |
| Jenkins como código | [`infra/tools/jenkins/casc/jenkins.yaml`](../../infra/tools/jenkins/casc/jenkins.yaml) (credenciales, librería, Sonar, jobs) |
| Versionado | [`.releaserc.json`](../../.releaserc.json) · [`docs/release-notes/`](../release-notes/README.md) |
| Compose por ambiente | [`deploy/compose/`](../../deploy/compose/README.md) |
| Avisos | [`jenkins/vars/notifyTeam.groovy`](../../jenkins/vars/notifyTeam.groovy) → Discord y WhatsApp (CallMeBot) en fallo, espera de aprobación, éxito y rollback |

**Ejecución selectiva:** en un PR solo se construyen y analizan los servicios cuyos archivos cambiaron (un cambio en Gradle, `Jenkinsfile` o `jenkins/` construye todos). En `main` la CI sigue siendo selectiva, pero la publicación es siempre de las 9 imágenes con la misma versión, porque el compose de cada ambiente usa un solo `IMAGE_TAG`.
