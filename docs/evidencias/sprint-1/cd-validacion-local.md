# HU-08, HU-09, HU-10 (#15, #16, #17) · Validación local de la entrega continua — 2026-11-07

Las VMs de AWS estaban apagadas al escribir el pipeline de CD, así que antes de encenderlas se validó todo lo que se puede validar sin ellas. La corrida real (versión `v1.0.0`, despliegue en dev → stage → aprobación → prod, avisos y rollback) se documenta aparte cuando se ejecute en Jenkins.

## 1. Sintaxis declarativa de los dos pipelines (linter de Jenkins)

Jenkins 2.580.2 local (misma imagen de `infra/tools/jenkins/`), endpoint `pipeline-model-converter/validate`:

```
$ curl -X POST -F "jenkinsfile=<Jenkinsfile"                  .../pipeline-model-converter/validate
Jenkinsfile successfully validated.
$ curl -X POST -F "jenkinsfile=<jenkins/rollback.Jenkinsfile" .../pipeline-model-converter/validate
Jenkinsfile successfully validated.
```

`groovy -e 'new GroovyShell().parse(...)'` sobre los 6 pasos nuevos de la Shared Library: sin errores. `shellcheck deploy.sh`: sin advertencias.

## 2. semantic-release en modo `--dry-run` contra el repositorio real

Misma imagen y mismos paquetes que usa el pipeline (`node:22.23.4-bookworm`, `semantic-release@24.2.9`, `@semantic-release/changelog@6`, `@semantic-release/exec@7`, `conventional-changelog-conventionalcommits@9`), sobre la rama del PR (sin tags todavía):

```
[semantic-release] › ✔  Allowed to push to the Git repository
[semantic-release] [@semantic-release/github] › ℹ  Verify GitHub authentication
[semantic-release] › ℹ  No git tag version found on branch feat/15-entrega-continua
[semantic-release] › ℹ  Found 16 commits since last release
[@semantic-release/commit-analyzer] › ℹ  Analyzing commit: docs(deploy): documenta el versionado automatico ...   → should not trigger a release
[@semantic-release/commit-analyzer] › ℹ  Analyzing commit: ci(jenkins): agrega el job de rollback ...                → should not trigger a release
[@semantic-release/commit-analyzer] › ℹ  Analyzing commit: feat(deploy): agrega imagenes docker inmutables ... (#48) → The release type for the commit is minor
[@semantic-release/commit-analyzer] › ℹ  Analyzing commit: fix(infra): hace robusto el bootstrap ... (#45)           → The release type for the commit is patch
[semantic-release] › ℹ  There is no previous release, the next release version is 1.0.0
[semantic-release] › ⚠  Skip v1.0.0 tag creation in dry-run mode
[semantic-release] › ℹ  Release note for version 1.0.0:
## 1.0.0 (2026-11-07)
### Funcionalidades
    * **deploy:** agrega imagenes docker inmutables y compose por ambiente (#48), closes #9 #10
    * **infra:** agrega bootstrap de la nube, jenkins como codigo y avisos del pipeline (#44), closes #8 #38 #15
    * **infra:** agrega presupuesto mensual de aws y cierra los riesgos del sprint 0 (#43)
### Correcciones
    * **infra:** hace robusto el bootstrap tras el primer despliegue real (#45), closes #8
```

Se comprueba que: los commits `docs`/`ci` no generan versión, `feat` sube MINOR y `fix` PATCH, la primera versión es **1.0.0** y las notas se agrupan por tipo en español.

## 3. `deploy.sh` en local con un `compose.sh` simulado

```
$ CIRCLEGUARD_HOME=/tmp/cg bash jenkins/resources/scripts/deploy.sh dev 1.0.0 172.31.32.157:8082 deploy abc1234 http://j/1
[deploy dev] Primer despliegue: generando los secretos del ambiente en /tmp/cg/compose/dev/.env
[deploy dev] Descargando imágenes 172.31.32.157:8082/circleguard/*:1.0.0
[deploy dev] Aplicando el compose (up -d --wait): espera a que todo quede healthy
[deploy dev] Versión 1.0.0 desplegada (deploy). Últimos despliegues:
2026-11-07T14:02:47-05:00	1.0.0	deploy	abc1234	http://j/1

$ stat -c '%a' /tmp/cg/compose/dev/.env
600
$ CIRCLEGUARD_HOME=/tmp/cg bash jenkins/resources/scripts/deploy.sh dev 1.1.0 172.31.32.157:8082 rollback
$ diff <(grep -v ^IMAGE_TAG before.env) <(grep -v ^IMAGE_TAG /tmp/cg/compose/dev/.env) && echo "secretos intactos"
secretos intactos
$ grep ^IMAGE_TAG /tmp/cg/compose/dev/.env
IMAGE_TAG=1.1.0
$ cat /tmp/cg/RELEASES
2026-11-07T14:02:47-05:00	1.0.0	deploy	abc1234	http://j/1
2026-11-07T14:02:47-05:00	1.1.0	rollback	?	?
```

Se comprueba que: los secretos se generan una sola vez (permisos `600`), un segundo despliegue o rollback solo cambia `IMAGE_TAG`, y cada despliegue queda en el historial `RELEASES` que lee el job de rollback.

## Pendiente de evidencia real (con las VMs encendidas)

- Build de `main` en Jenkins: etapa Release con `v1.0.0`, 9 imágenes en Nexus, dev y stage `healthy`, pausa de `input`, prod desplegado.
- Avisos de Discord/WhatsApp: APPROVAL, SUCCESS y un FAILURE provocado.
- Job `circleguard-rollback` devolviendo prod a la versión anterior.
