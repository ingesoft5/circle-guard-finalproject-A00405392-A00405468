# Despliegue con Docker Compose (Parte 1)

Un compose por ambiente sobre una base común. Los tres ambientes usan **las mismas imágenes**; solo cambian `IMAGE_TAG` y la configuración.

| Archivo | Contenido |
|---|---|
| `base.yml` | Middleware (PostgreSQL, Neo4j, Kafka en KRaft, Redis), los 5 servicios núcleo (`auth`, `identity`, `form`, `promotion`, `gateway`) y el cliente `web`. Healthchecks y `depends_on: service_healthy` |
| `dev/`, `stage/`, `prod/` | Override por ambiente: nombre del proyecto, `CIRCLEGUARD_ENV`, nivel de log, límites de memoria y política de reinicio |
| `compose.sh` | `docker compose` con la base + el override del ambiente |
| `.env.example` | Variables necesarias. El `.env` real **nunca** se versiona |
| `postgres/init-db.sql` | Crea una base de datos por servicio |

## Uso

```bash
cp deploy/compose/.env.example deploy/compose/dev/.env   # y llenar los secretos
./deploy/compose/compose.sh dev up -d --wait              # todo queda healthy (~2 min)
./deploy/compose/compose.sh dev ps
./deploy/compose/compose.sh dev down                      # -v borra también los datos
```

El pipeline define `REGISTRY` (Nexus) e `IMAGE_TAG` (versión semántica). En local se usan imágenes construidas con:

```bash
docker build -f services/circleguard-auth-service/Dockerfile -t local/circleguard/circleguard-auth-service:dev .
docker build -f mobile/Dockerfile -t local/circleguard/circleguard-web:dev .
```

Los Dockerfiles se construyen **desde la raíz del repo**, porque el build de Gradle es un monorepo.

## Despliegue desde Jenkins (HU-08, HU-10)

En cada *merge* a `main` el pipeline ([`Jenkinsfile`](../../Jenkinsfile)) calcula la versión con semantic-release, publica las 9 imágenes en Nexus con ese tag y las promueve: **dev** (automático) → E2E con Playwright → **stage** (automático si los E2E pasan) → aprobación manual (`input`) → **prod**. Cada despliegue lo hace `deployCompose(env, version)` ([`jenkins/vars/deployCompose.groovy`](../../jenkins/vars/deployCompose.groovy)) por SSH a la VM del ambiente:

1. Copia esta carpeta a `/opt/circleguard/compose/` (sin tocar el `.env`).
2. Corre [`deploy.sh`](../../jenkins/resources/scripts/deploy.sh) en la VM: la **primera vez genera los secretos** del ambiente en `<env>/.env` con `openssl rand` (nunca salen de la VM, ni al repo ni a Jenkins); luego fija `REGISTRY` e `IMAGE_TAG`, hace `compose pull` y `up -d --wait`, y anota la versión en `/opt/circleguard/RELEASES`.
3. `smokeTest(env)` comprueba `/healthz` y el `readiness` de los 5 servicios a través del nginx.

**Rollback:** el job `circleguard-rollback` de Jenkins ([`jenkins/rollback.Jenkinsfile`](../../jenkins/rollback.Jenkinsfile)) recibe `ENV` y `VERSION` y llama al mismo `deployCompose` con una versión anterior: las imágenes son inmutables y siguen en Nexus, así que volver atrás es cambiar `IMAGE_TAG`. En prod pide confirmación.

**Qué versión corre:** el nginx del web expone `GET /version` con la versión desplegada en el ambiente (su `IMAGE_TAG`, p. ej. `1.1.0`): `curl http://<ip-del-ambiente>/version`. Una imagen anterior a esta función no tiene la ruta, así que tras un rollback a una de ellas la referencia es el tag de las imágenes en `docker ps` y el historial `/opt/circleguard/RELEASES`.

## Cómo se comunican

El cliente web es la única entrada pública (puerto `WEB_PORT`). Su nginx reenvía `/api/<servicio>/...` al servicio correspondiente dentro de la red de compose, así que el navegador nunca habla directo con los servicios y no hay problemas de CORS. Los servicios no publican puertos.

## Usuarios de prueba

`super_admin`, `health_user` y `staff_guard`, todos con clave `password` (migración `V2__seed_test_users.sql` de auth). LDAP es opcional: si no está configurado, auth usa estas cuentas locales.
