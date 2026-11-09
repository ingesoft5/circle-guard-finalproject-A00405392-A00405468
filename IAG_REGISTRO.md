# Registro de uso de IA generativa (IAG)

| | |
|---|---|
| **Nivel declarado** | **Nivel 3 — Colaboración con IAG** (política del curso) |
| **Herramienta** | Claude Code (asistente de programación en la terminal) |
| **Regla del equipo** | Cada uso se registra en el mismo PR donde entra su resultado ([`CONTRIBUTING.md` §6](CONTRIBUTING.md#6-uso-de-ia-generativa)). Quien abre el PR revisa, ajusta y debe poder explicar todo lo generado. Ningún commit lleva a la IA como autor o coautor. |

Formato de cada entrada: fecha, integrante, contenido propio entregado, prompt, qué produjo la IA, qué se verificó o cambió.

---

## Sprint 0

### 2026-11-04 · `jmrojas06` · Diagnóstico y plan del proyecto

- **Contenido propio entregado:** el enunciado del proyecto (`Proyecto_Final_CircleGuard.tex`/`.pdf`), el repositorio base y los requisitos de metodología pegados del enunciado.
- **Prompts (textuales):**
  1. *"Quiero que revises el repo, y me digas que hay que hacer, planees todo. Para mi compañero y yo."* (+ sección de requisitos del enunciado)
  2. *"Pero entonces ya esta dividido en sprints? hay que hacerlo bien. Hay que tener todo para el domingo, y lo ideal seria hacerlo entonces paso a paso con lo que nos piden, hay que sustentar hasta el sprint 1 el lunes. Y luego el sprint 2 es el resto del proyecto."*
- **Qué produjo:** diagnóstico del repositorio (archivos faltantes, secretos en texto plano, bug de `API_BASE_URL` en el cliente web) y un build en limpio dentro de un contenedor `gradle:8.14-jdk21` (8 servicios compilan, 10 de 50 pruebas fallan). Con eso redactó [`docs/agil/PLAN-PROYECTO.md`](docs/agil/PLAN-PROYECTO.md), reorganizado en Sprint 0, 1 y 2 con pasos por entregable.
- **Verificación / cambios del equipo:** se pidió reestructurar la primera versión porque no estaba dividida en sprints formales. Los fallos de las pruebas se comprobaron con los reportes JUnit del build.

### 2026-11-04 · `jmrojas06` · Backlog y tablero

- **Prompts:** *"Listo, y entonces en el backlog lo que vaya a hacer mi amigo. No te incluyas en los commits como colaborador porfavor…"*
- **Qué produjo:** 34 issues (7 tareas del Sprint 0 y 27 historias de usuario con criterios de aceptación y story points), etiquetas por entregable y responsable, milestones por sprint y el [tablero de GitHub Projects](https://github.com/orgs/ingesoft5/projects/8) con campos Status, Sprint, Story Points y Entregable.
- **Verificación / cambios del equipo:** se revisó que el reparto quedara equilibrado (26 puntos propios por integrante en el Sprint 1). El responsable B se confirmó como `pierrecuevas`.

### 2026-11-04 · `jmrojas06` · Estrategia de ramas y Sprint 0

- **Prompts:** *"Sí, arranca"* y *"Terminemos todo mi sprint 0, y se lo pasas para el review a mi amigo. Y que luego el haga su sprint 0 y me lo pase a mi para revision pr."* El equipo también preguntó si se cumplía el requisito de branching del enunciado.
- **Qué produjo:** [`CONTRIBUTING.md`](CONTRIBUTING.md) (GitHub Flow, Conventional Commits, protección de `main`, DoD), [`docs/agil/sprint-0.md`](docs/agil/sprint-0.md) con el planning del Sprint 1, el renombre del repositorio, la protección de `main` vía API (con evidencia en `docs/evidencias/sprint-0/`) y este registro.
- **Verificación / cambios del equipo:** aprobado por `pierrecuevas` en #35, #36 y #37 (4 nov), sin comentarios; la retro del Sprint 0 lo registra como punto a mejorar. _Revisión: completada por `pierrecuevas`._

### 2026-11-04 · `jmrojas06` · Revisión de la rúbrica

- **Prompt:** *"Revisa la rubrica, busca rubrica en todas partes para asegurar que saquemos 5. Tambien mantener al final en gitignore nuestros guiones y todo lo que hemos hecho para estudiar y sustentar."*
- **Qué produjo:** comparación del PDF con el `.tex` (idénticos), revisión del repo base del docente y de los READMEs de los talleres del curso, una checklist privada criterio por criterio, 2 historias nuevas (#38 burndown, #39 bonos), criterios agregados a 20 issues (marcados "(rúbrica)"), review y retro del Sprint 0 en borrador y la carpeta `estudio/` excluida de git.
- **Hallazgos clave:** el criterio P1.8 exige un **video** que el entregable E1.8 no menciona; la Parte 1 pide el primer release como `v1.0.0`, pipeline accesible al docente y evidencia por entregable.
- **Verificación / cambios del equipo:** el resultado entró por el PR #40, aprobado por `pierrecuevas` el 4 nov, sin comentarios. _Revisión: completada por `pierrecuevas`._

### 2026-11-04 · `pierrecuevas` · Plantillas de PR e issues (#4)

- **Contenido propio entregado:** la sección de metodología del enunciado y el backlog/tablero ya creados por el equipo.
- **Prompt:** *"Este es el proyecto de ingesoft, mi amigo ya cuadro el github projects y los issues, entonces la idea es que yo ya termine el sprint #0, lo que me falta, y hacer el pr para que mi compañero lo haga."* (+ requisitos de branching del enunciado)
- **Qué produjo:** `.github/pull_request_template.md` (título como Conventional Commit, `Closes #`, tipo de cambio con su efecto en semver, checklist alineado con la DoD) y `.github/ISSUE_TEMPLATE/{historia,bug,solicitud-cambio}.md` + `config.yml`. La solicitud de cambio usa la clasificación estándar/normal/emergencia del plan (paso 1.6).
- **Verificación / cambios del equipo:** revisado por `jmrojas06` en #41. Se pidió agregar `Closes #4` a la descripción, crear la etiqueta `change-request` (no existía y GitHub la ignoraba), usar URL completa para el enlace a `CONTRIBUTING.md` (el relativo no resolvía en la descripción del PR) y aplicar la etiqueta `historia` por defecto. Corregido en `995e049` y aprobado.

### 2026-11-04 · `pierrecuevas` · Código base limpio y en verde (#6)

- **Prompt:** el mismo de la entrada anterior (terminar el Sprint 0 de B).
- **Qué produjo:** limpieza del repo (C1-C3), wrapper de Gradle versionado y la corrección de las 10 pruebas rotas. Diagnosticó cada fallo con los reportes JUnit antes de tocar código: en promotion concluyó que el error estaba en la prueba (rol sin prefijo `ROLE_`) y no en el controlador, comparándolo con los *claims* que emite auth. Encontró que Testcontainers fallaba por la versión de la API de Docker, no por falta de Docker. En la revisión del PR se separó la prueba de rendimiento en la tarea `performanceTest` porque fallaba al azar bajo carga.
- **Verificación / cambios del equipo:** build completo desde un `git clone` limpio en `gradle:8.14-jdk21`: 51 pruebas, 0 fallas; `performanceTest` es inestable en la laptop (pasa 1 de 4, de 858 a 3013 ms), detallado en la evidencia ([evidencia](docs/evidencias/sprint-0/build-verde.txt)). **Cambios del revisor** (`jmrojas06`, #42): se pidió título en Conventional Commits, `Closes #6`, rebase sin el commit de #41, sacar de `test` la prueba de rendimiento (falló con 1035 ms en el build completo del revisor y pasó 3 de 3 corriéndola sola) y regenerar la evidencia con un commit de la rama. Todo aplicado; el revisor verificó `3ad0c09` desde un clon limpio: 51 pruebas, 0 fallas.

### 2026-11-05 · `jmrojas06` · Revisión de PR y presupuesto de AWS

- **Prompts:** *"revisa si pierre ha hecho algo"*, *"Ya el subio un PR. Revisemoslo"*, *"Y entonces como asi que alarma en aws budget? haz eso tu si puedes."*
- **Qué produjo:** la revisión de #41 y #42 (con un build en limpio que encontró la prueba de rendimiento inestable), la instalación verificada de AWS CLI y `infra/bootstrap/bootstrap-budget.sh`, que crea el presupuesto mensual con alertas y es idempotente (evidencia de 2 ejecuciones).
- **Decisiones del equipo:** las llaves de AWS las configuró `jmrojas06` con un usuario IAM propio (no la cuenta raíz); el correo de alertas entra por variable de entorno para no quedar en el repo.
- **Verificación / cambios del equipo:** el presupuesto entró por el PR #43, aprobado por `pierrecuevas` el 5 nov. Las revisiones de #41 y #42 quedaron aprobadas por `jmrojas06` ese mismo día. _Revisión: completada por `pierrecuevas`._

### 2026-11-06 · `jmrojas06` · Bootstrap de la nube, Jenkins como código y avisos (#8, #15, #38)

- **Prompts:** *"Pierre ya aprobo 43"* (arrancar el Sprint 1), *"no puedo hacer un github token ya que el repo pertenece a la organizacion"*, *"veamos si es posible crear un bot de whatsapp y por ahi enviar eso"*, *"Hagamos el de CallMeBot"*.
- **Qué produjo:** `infra/bootstrap/` (VMs, security groups por ambiente, IPs elásticas, Nexus/SonarQube/Jenkins configurados por API, `up.sh` y `down.sh`), `infra/tools/` (compose y Jenkins con JCasC), `jenkins/vars/notifyTeam.groovy` + `notify.sh` (WhatsApp por CallMeBot y Discord) y `docs/agil/metricas/burndown.sh`.
- **Verificación / cambios del equipo:** `shellcheck` sin advertencias; Jenkins probado en local con valores ficticios (JCasC sin errores, job multibranch creado, usuario `docente` con 200 en lectura y 403 en administración); envío real por Discord y WhatsApp. El equipo descartó el token *fine-grained* porque la organización no lo permite y usó uno *classic* con scope `repo`; eligió WhatsApp como canal extra y corrigió el formato del número (+57). Al probar se encontró que CallMeBot responde 203 y se ajustó el script. _Revisión: aprobada por `pierrecuevas` en #44 (6 nov) tras las correcciones (`cfc351b`): comprobó la política de la clave de SonarQube, que `ADMIN_CIDRS` agrega las IPs nuevas antes de retirar las viejas y que `numExecutors: 2` quedó bien._
- **Ronda de revisión de Pierre (6 nov):** *"Resuelve solo los comentarios y vuelvelo a subir, lo otro es para otro PR que el revisara."* La IA aplicó solo lo pedido: clave de admin de SonarQube con carácter especial (bloqueante), `ADMIN_CIDRS` con lista de IPs que retira las reglas viejas y `numExecutors: 2`. Verificación: 200 claves generadas cumplen la política de SonarQube; 12 comprobaciones del script de security groups con `aws` simulado (no se tocó la cuenta real). Queda sin probar contra AWS y SonarQube reales hasta la próxima corrida de `up.sh`.

### 2026-11-06 · `jmrojas06` · Primer despliegue real de la infraestructura (#8)

- **Prompt:** *"despleguemoslas para probarlas, y las volvemos a probar para el lunes. O cada vez que haya que probar algo, tampoco quiero gastar tanto."*
- **Qué produjo:** primera ejecución real de `up.sh` en AWS. Fallos encontrados y corregidos en el camino: el script de Nexus se cortaba sin error porque `docker compose exec` consumía la entrada del heredoc (ahora es `remote/configure-nexus.sh`); `tr | head` con `pipefail` cortaba la generación de claves; `grep -q` podía duplicar el webhook; la ruta del socket SSH superaba 108 caracteres; la sesión SSH reutilizada no veía el grupo `docker` (se cierra con `reconnect`). Se agregaron reintentos SSH solo para errores de conexión, el README de operación y la evidencia.
- **Verificación / cambios del equipo:** dos ejecuciones seguidas de `up.sh` (la segunda: todo "ya existe", 1 min 20 s); Jenkins 2.580.2 responde 403 anónimo, `docente` 200 en lectura y 403 en `/manage`; SonarQube UP y `admin:admin` inválido; Nexus `ALLOW_ONCE`; los 3 ambientes con `docker login` OK; webhook de GitHub con respuesta 200. Las VMs se apagaron con `down.sh` al terminar. _Revisión: aprobada por `pierrecuevas` en #45 (6 nov) después de verificar que el diff del rebase sobre `main` era idéntico al que ya había aprobado._

### 2026-11-06 · `jmrojas06` · Pipeline CI en Jenkins (#11)

- **Prompts:** *"Si haz eso. No entiendo, tu soluciona lo que hiciste mal. Y continuemos entonces con el sprint 1."*
- **Qué produjo:** `Jenkinsfile` multibranch y Shared Library (`changedServices`, `runGradle`, `sonarScan`, `commitLint`, `dockerPublish`), JaCoCo en Gradle, `commitlint.config.cjs`, quality gate "CircleGuard" en SonarQube y `up.sh tools` para encender solo Jenkins al probar.
- **Verificación / cambios del equipo:** probado en Jenkins real. PR-46 en verde (51 pruebas, 8 quality gates OK, estado `success` en GitHub). PR-47 de demo: solo construyó `gateway` y el quality gate lo bloqueó (estado `error` en GitHub, aviso por WhatsApp y Discord). En el camino se corrigió que SonarQube 26 copia las condiciones de "Sonar way" al crear un gate (80 % de cobertura y `new_violations > 0`), y se ajustó a 60 %. _Revisión: aprobada por `pierrecuevas` en #46 (6 nov). El PR estaba apilado sobre #45 y hubo que rebasarlo una vez al fusionarlo (lección de la retro del Sprint 1)._

### 2026-11-06 · `pierrecuevas` · Imágenes Docker y compose por ambiente (#9, #10)

- **Prompt:** *"vamos haciendo lo que nos toca por el sprint 1"*.
- **Qué produjo:** configuración y secretos de los 8 servicios por variables de entorno, Actuator con health, Dockerfiles multi-stage (8 servicios + web con nginx como reverse proxy), `deploy/compose/` (base + dev/stage/prod, `compose.sh`, `.env.example`) y la evidencia. Encontró en el camino tres errores del código base: `auth` tenía la URL de identity y la clave de LDAP escritas en el código, la app web importaba `API_BASE_URL` (no existe) y el QR vencía en 0.3 s porque la configuración estaba en segundos y el código la lee en milisegundos.
- **Verificación / cambios del equipo:** `./gradlew build` 51 pruebas, 0 fallas; las 9 imágenes construidas; `compose.sh dev up -d --wait` deja los 10 contenedores `healthy` en 2 min; login de `super_admin` (200), clave incorrecta (401) y QR emitido por auth y validado por gateway (`GREEN`), todo a través del nginx del cliente web ([evidencia](docs/evidencias/sprint-1/imagenes-y-compose-dev.md)). Se decidió no incluir LDAP en los ambientes (auth usa las cuentas locales si LDAP no responde) para ahorrar memoria en las VMs de 8 GB.
- **Revisión de `jmrojas06` (6-7 nov):** *"revisa el #48 de pierre y apruebalo o corrigelo."* y *"pierre ya aprobo creo, revisa lo que haya aprobado y continuemos"*. La IA leyó el diff completo y lo comprobó en una copia aparte: `./gradlew build` en JDK 21 con Docker disponible (51 pruebas, 0 fallas), los 3 compose con `config`, `shellcheck` de `compose.sh`, el nginx real contra un servidor de eco y el gateway arrancado con las variables del README. Corrigió tres cosas: el nginx entregaba la ruta decodificada al servicio (espacios sin codificar; ahora `rewrite … break` y `proxy_pass` sin ruta, con evidencia en `imagenes-y-compose-dev.md`), el README de desarrollo local no arrancaba sin las variables nuevas (ahora se documentan) y el conflicto de este archivo con `main`. Con #46 ya en `main`, se ajustó `dockerPublish.groovy` para construir con la raíz del repo como contexto (`-f <dir>/Dockerfile .`); se comprobó la sintaxis y el mismo `docker build` con el gateway, y la publicación real en Nexus queda por probar al levantar las VMs. No se levantó el compose completo; lo cubre la evidencia de Pierre. _Revisión: completada por `jmrojas06`._

### 2026-11-06 · `pierrecuevas` · Cobertura ≥ 60 % en los servicios núcleo (#12)

- **Prompt:** *"vamos haciendo lo que nos toca por el sprint 1"*.
- **Qué produjo:** línea base con JaCoCo por clase, 11 clases de prueba nuevas (unitarias con Mockito y de controlador con la `SecurityConfig` real) para auth, identity, form y promotion, y `jacocoTestCoverageVerification` con mínimo 0.60 en los 5 servicios núcleo.
- **Verificación / cambios del equipo:** auth 29 → 89.6 %, identity 50 → 84.4 %, form 43.6 → 88.6 %, promotion 35 → 68.4 %, gateway 81 %; 110 pruebas, 0 fallas. Se comprobó que el umbral bloquea subiéndolo al 95 % en gateway: el build falla con `Rule violated` ([evidencia](docs/evidencias/sprint-1/cobertura-servicios-nucleo.md)). La verificación se puso en el `build.gradle.kts` de cada servicio para no chocar con el JaCoCo del archivo raíz de #46. **Revisión de `jmrojas06` (8 nov):** *"revisemos y aceptemos las que pierre acaba de subir."* La IA comprobó que el rebase de #49 era idéntico a lo ya aprobado (solo cambia el contexto de `IAG_REGISTRO.md`) y lo construyó en una copia aparte con `gradle:8.14-jdk21` y Docker: `./gradlew build` en verde, 110 pruebas, 0 fallas, y `jacocoTestCoverageVerification` (60 %) pasando en los 5 servicios. Aprobado y fusionado el 8 nov. _Revisión: completada por `jmrojas06`._

### 2026-11-07 · `pierrecuevas` · Pruebas de integración entre servicios (#13)

- **Prompt:** *"vamos haciendo lo demas entonces"*.
- **Qué produjo:** 3 pruebas de integración con Testcontainers (form → Kafka → promotion con Neo4j y Redis; QR de auth → gateway con Redis; promotion → Kafka → notification) en una tarea `integrationTest` aparte, y la corrección de tres errores que esas pruebas encontraron: gateway no negaba el acceso a `SUSPECT`/`PROBABLE`/`CONFIRMED`, la consulta Cypher de promotion no actualizaba Redis ni publicaba el evento si el usuario no tenía contactos, y promotion, identity y notification no tenían serializador JSON para publicar a Kafka.
- **Verificación / cambios del equipo:** las pruebas se corrieron primero sin tocar el código y fallaron por esas causas (salidas en la [evidencia](docs/evidencias/sprint-1/pruebas-integracion.md)); después de corregir: 11 casos, 0 fallas, y `./gradlew build` en verde con las pruebas de propagación a contactos intactas. Una primera falla en Redis fue un error de la prueba (no esperaba la escritura asíncrona) y se corrigió en la prueba, no en el código. La regla de qué estados niegan el acceso se tomó del enunciado (cerco Sospechoso → Probable → Confirmado). **Revisión de `jmrojas06` (8 nov):** *"revisa, para aceptarle o hacer squash de algo mío. Mira que sigue abierto."* Como #54 chocaba con `main` tras fusionar #49, se pidió a Pierre el rebase en un comentario. La IA comparó el rebase con la versión ya revisada (commits idénticos; solo cambia la resolución de los conflictos), leyó los cambios de producción (gateway, la consulta Cypher de promotion y los serializadores de Kafka) y construyó el resultado sobre el `main` nuevo: `./gradlew build integrationTest` en verde, 121 pruebas (11 de integración), 0 fallas, con los umbrales de JaCoCo pasando en los 5 servicios. _Revisión: completada por `jmrojas06`._

### 2026-11-07 · `jmrojas06` · Entrega continua, versionado automático y rollback (#15, #16, #17)

- **Prompt:** *"Revisa, y entonces terminemos mi parte del sprint 1 y ojala revisar sprint 0."*
- **Qué produjo:** cinco pasos nuevos de la Shared Library (`semanticRelease`, `deployCompose`, `smokeTest`, `e2eTests`, `releaseHistory`) y `deploy.sh` (corre en la VM del ambiente); el `Jenkinsfile` con las etapas de `main`: Release (semantic-release) → Package + Publish de las 9 imágenes → Deploy dev → E2E → Deploy stage → aprobación (`input`) → Deploy prod; `jenkins/rollback.Jenkinsfile` y su job en JCasC; `.releaserc.json`; `docs/release-notes/README.md`.
- **Decisiones del equipo:** (1) sin `@semantic-release/git`: `main` está protegida también para administradores y no se quiso abrir una excepción para el *bot*, así que el `CHANGELOG.md` va a la Release y al artefacto del build, no a un commit; (2) `agent none` en el pipeline para que la espera de aprobación no ocupe uno de los 2 ejecutores; (3) los secretos de cada ambiente se generan con `openssl rand` en la propia VM la primera vez y nunca pasan por Jenkins ni por el repo; (4) en `main` se publican las 9 imágenes con la misma versión porque el compose usa un solo `IMAGE_TAG`; la ejecución selectiva queda para la CI de los PR; (5) el E2E no bloquea mientras `tests/e2e-playwright/run.sh` no exista (HU-07, de B) y bloquea la promoción a stage en cuanto exista; (6) el rollback no es un camino aparte: es `deployCompose` con una versión anterior, porque las imágenes son inmutables.
- **Verificación / cambios del equipo:** los dos `Jenkinsfile` validados con el linter declarativo de un Jenkins 2.580.2 local; `semantic-release --dry-run` contra el repositorio real calcula `1.0.0` y agrupa las notas por tipo; `deploy.sh` probado en local con un compose simulado (secretos una sola vez con permisos 600, segundo despliegue intacto, historial `RELEASES`); `shellcheck` y parse de Groovy sin errores ([evidencia](docs/evidencias/sprint-1/cd-validacion-local.md)). Falta la corrida real en AWS con las VMs encendidas: se hará en el ensayo general. _Revisión: aprobada por `pierrecuevas` en #51 (8 nov) tras verificar el rebase: fuera de `IAG_REGISTRO.md`, el diff era idéntico al que ya había aprobado. La corrida real en AWS (v1.0.0 y v1.1.0) quedó documentada en [`ensayo-pipeline-real.md`](docs/evidencias/sprint-1/ensayo-pipeline-real.md)._

### 2026-11-07 · `pierrecuevas` · Pruebas E2E con Playwright (#14)

- **Prompt:** *"mira a ver si hay algun pr y si no continua"*.
- **Qué produjo:** `tests/e2e-playwright/` con los 3 flujos (inicio de sesión, cuestionario y validación de acceso), `global-setup` que deja un cuestionario activo, `run.sh` con la imagen oficial de Playwright y reportes HTML y JUnit. Corrigió cuatro errores de la app que salieron al probarla en un navegador real: 403 por CORS en el login (nginx perdía el puerto del `Host`), el cuestionario descartaba las respuestas, el rol de guardia no tenía el permiso `gate:scan` y el escáner no se podía usar en web.
- **Verificación / cambios del equipo:** 5/5 E2E en verde dos veces seguidas contra el compose de dev; `jest` de `DynamicForm` 2/2 ([evidencia](docs/evidencias/sprint-1/pruebas-e2e-playwright.md)). La primera corrida falló 5/5: una por el 403 real y las demás por selectores (textos duplicados de pantallas ocultas y un mensaje que la app muestra en mayúsculas), que se corrigieron en las pruebas. Se decidió que en web el guardia pegue el código o use un lector USB, porque el navegador no tiene biometría. **Revisión de `jmrojas06` (8 nov):** la IA verificó que el rebase de #55 no cambiaba el parche (comparó el diff de antes y de después contra sus bases), que el arnés de Playwright coincidía con `jenkins/vars/e2eTests.groovy` (`npm ci`, `npx playwright test`, `BASE_URL` y reportes en `reports/junit.xml` y `reports/html`) y aplicó la migración V6 sobre V1-V5 en un Postgres limpio: `GATE_STAFF` pasó de 0 permisos a tener `gate:scan`, y es idempotente. No ejecutó la suite E2E por su cuenta: se apoyó en la evidencia de Pierre, y después los E2E corrieron dentro del pipeline (5/5 en el build #2 de `main`). _Revisión: completada por `jmrojas06`._

### 2026-11-07 · `jmrojas06` · Gestión de cambios, arquitectura y documentación operativa (#18, #19, #38)

- **Prompt:** *"Revisa, y entonces terminemos mi parte del sprint 1 y ojala revisar sprint 0."*
- **Qué produjo:** `docs/change-management/politica.md` y `solicitud-de-cambio.md` (tipos de cambio, quién aprueba qué, RFC, esquema de etiquetado, trazabilidad, rollback), `docs/arquitectura/README.md` (tres diagramas Mermaid: servicios/eventos/datos, ambientes en AWS y pipeline), la sección "Operación" del `README.md` (mapa de la documentación, URL de los ambientes, cómo llega un cambio a prod, comandos), el borrador de `docs/agil/sprint-1.md`, la foto del burndown del 7 de noviembre y la corrección de `sprint-0.md` (#6 terminado en el PR #42).
- **Verificación / cambios del equipo:** los tópicos de Kafka, los puertos y las dependencias del diagrama se tomaron del código (`grep` de `@KafkaListener` y `kafkaTemplate.send`, y los `application.yml`), no de memoria. Antes de tomar la foto del burndown se puso el tablero al día con el estado real de los PR (40 de 61 puntos pendientes al 7 nov). La fila de B en las retros del Sprint 0 y del Sprint 1 y su reflexión sobre IAG son texto de `pierrecuevas`, incorporado tal como lo envió (con ajustes mínimos de redacción); la *review*, la fila de A y la reflexión de A las completa el equipo el domingo. _Revisión: aprobada por `pierrecuevas` en #53 (8 nov): revisó la política de cambios, la guía de RFC, los diagramas, la sección de operación del README y `sprint-1.md`, y pidió dos ajustes a su reflexión, que se aplicaron._

### 2026-11-08 · `jmrojas06` · Versión visible en `/version` para el segundo release y el rollback (#17)

- **Prompt:** *"Sí, arma el feat"*, respuesta a la propuesta de la IA de un cambio pequeño en el web para tener un segundo release (v1.1.0) y un rollback con un antes y un después: el primer release dejó una sola versión desplegada.
- **Qué produjo:** la ruta `GET /version` en el nginx del web (devuelve `APP_VERSION`), `APP_VERSION` con valor por defecto en el `Dockerfile` del web, `APP_VERSION: ${IMAGE_TAG}` en el servicio `web` del compose, una prueba E2E (`version.spec.ts`) y la nota en `deploy/compose/README.md`.
- **Verificación / cambios del equipo:** se probó en local con el nginx real de la imagen: `/version` devuelve `1.1.0` en texto plano y sin caché, `/healthz` y `/` siguen respondiendo y `nginx -t` es correcto. Sin `APP_VERSION` nginx no arranca (`unknown "app_version" variable`), por eso la imagen trae un valor por defecto. `docker compose config` es válido en dev, stage y prod, y el web recibe `APP_VERSION=1.1.0`. La prueba E2E pasa con Playwright 1.49.1 contra ese nginx y falla contra uno sin la ruta. En el pipeline real, dev y stage mostraron `1.1.0` y prod seguía en `1.0.0` hasta aprobar ([`ensayo-pipeline-real.md` §5](docs/evidencias/sprint-1/ensayo-pipeline-real.md)). _Revisión: aprobada por `pierrecuevas` en #57 (8 nov) tras probarlo: construyó la imagen web de la rama y comprobó que `/version` responde `1.1.0` con la variable y `dev` sin ella, y corrió la suite de Playwright completa contra el compose de dev (6/6, incluida `version.spec.ts`)._

### 2026-11-08 · `jmrojas06` · Cierre del Sprint 1: review, retro, evidencia y bitácora (#20, #19, #38)

- **Prompt:** *"Sí, empieza"*, respuesta a la propuesta de la IA de empezar por `sprint-1.md`, el tablero y la bitácora mientras prod esperaba la aprobación.
- **Qué produjo:** `docs/agil/sprint-1.md` final (review con el estado y la evidencia reales de cada historia, demostración, métricas y retrospectiva), `docs/evidencias/sprint-1/ensayo-pipeline-real.md` (la corrida real de v1.0.0 y v1.1.0, la CI completa del PR #56, SonarQube, Nexus, los 3 ambientes y el aviso de fallo), las 12 revisiones que estaban pendientes en esta bitácora, la reflexión de `jmrojas06` y la actualización del tablero (HU-05 a HU-09 a Done).
- **Verificación / cambios del equipo:** las cifras salen de las APIs de Jenkins, SonarQube, Nexus y GitHub y de las propias VMs, consultadas después de cada corrida. Cada revisión completada en la bitácora se tomó del registro de aprobaciones de GitHub (quién, cuándo y lo que escribió) y no se le atribuyó a nadie nada que no estuviera escrito allí. La fila de la retro y la reflexión de `jmrojas06` son un borrador de la IA que `jmrojas06` revisa y ajusta antes de fusionar. _Revisión: por completar por el revisor del PR._

### 2026-11-08 · `jmrojas06` · Video del pipeline de punta a punta (#20)

- **Prompts:** *"si me pasas un prompt para que mi pc con claude en google pueda usarlo para el grabar el video y todo eso? solo grabar la web y ya, sin edicion"* y, con el video ya grabado, *"ponle un guion o algo, que diga como que fue grabado en mi pc, pero que se uso la depuracion de claude para pasar de paginas"*.
- **Qué produjo:** el prompt del recorrido para *Claude en Chrome* (14 direcciones, con pausas y la regla de no pulsar nada que cambie algo), los comandos de grabación y los usuarios de cada sitio. Después, la ficha [`video-pipeline.md`](docs/evidencias/sprint-1/video-pipeline.md) con el guion y la declaración de cómo se grabó. Para escribir el guion, la IA extrajo 16 cuadros del video (uno cada 15 s) y los revisó uno por uno.
- **Verificación / cambios del equipo:** el video dura 3 min 47 s y pesa 189 MB; no se editó. En los 16 cuadros no aparecen contraseñas ni tokens, pero la revisión es por muestreo y no cubre cada segundo. La navegación entre páginas la hizo *Claude en Chrome* (el aviso «“Claude” comenzó a depurar este navegador» se ve en todo el video) y el inicio de sesión en la aplicación lo hizo la persona a mano. _Revisión: por completar por el revisor del PR._

### 2026-11-08 · `jmrojas06` · Cobertura de SonarQube por encima del 60 % (rúbrica P1.6)

- **Prompt:** *"necesitamos tener eso por encima del 60% coverage, como se hace? hazlo, cumplamos las rubricas"*, con una captura de SonarQube que mostraba 56,7 % en `auth`.
- **Qué produjo:** el diagnóstico (SonarQube cuenta líneas y ramas; en `auth` las ramas estaban en 13,8 % y casi todas eran código que genera Lombok) y el archivo `lombok.config` con `lombok.addLombokGeneratedAnnotation = true`, que hace que JaCoCo deje fuera ese código. No se tocó ninguna prueba ni ningún umbral.
- **Verificación / cambios del equipo:** se corrieron las pruebas unitarias de los 5 servicios en Docker y se calculó la cobertura (líneas + ramas) del XML de JaCoCo: auth 90,6 %, identity 91,0 %, form 75,7 %, promotion 62,3 %, gateway 79,2 %. Son cifras locales; la medida oficial es la de SonarQube tras el build de `main`. _Revisión: por completar por el revisor del PR._

### 2026-11-08 · `jmrojas06` · Referencia del pipeline y CI completa sobre el código final (#20)

- **Prompt:** *"si, arma el PR"*, respuesta a la propuesta de la IA de abrir un PR que obligara a Jenkins a correr la CI completa: el primer build de `main` (v1.0.0) omitió Build, pruebas, SonarQube e integración porque lo último que había cambiado eran documentos.
- **Qué produjo:** `jenkins/README.md`: cómo se conecta el pipeline (multibranch, *webhooks*, librería cargada desde la misma rama), qué etapa corre en un PR y cuál en `main`, las reglas de la ejecución selectiva, la referencia de los 11 pasos de la *Shared Library* y de `deploy.sh` y `notify.sh`, credenciales y variables, rollback, aprobación y concurrencia, y dónde ver cada resultado. Al estar bajo `jenkins/`, `changedServices` lo trata como cambio común y construye los 9 servicios: es la primera vez que Jenkins ejecuta cobertura, SonarQube e integración sobre el código final.
- **Verificación / cambios del equipo:** cada paso se documentó leyendo su código fuente (`jenkins/vars/*.groovy`, `Jenkinsfile`, `rollback.Jenkinsfile`, `deploy.sh`, `notify.sh`); los IDs de credenciales, las variables globales y el nombre del repositorio de Nexus se tomaron de `jenkins.yaml` y de `configure-nexus.sh`, no de memoria. Al leer el `Jenkinsfile` se vio además que `disableConcurrentBuilds(abortPrevious: true)` aborta el build anterior de la rama, incluido uno que espera la aprobación de prod, y quedó documentado como regla operativa. _Revisión: por completar por el revisor del PR._

### 2026-11-08 · `jmrojas06` · Nexus visible para el docente y check obligatorio de Jenkins (#18, #38)

- **Prompt:** *"si, Abrelo y activa el check obligatorio de jenkins en main"*, tras detectar que el puerto 8081 de Nexus solo estaba abierto a las IP de administración y que la cuenta `docente` de Nexus del README no serviría desde otra red.
- **Qué produjo:** una regla de entrada 8081 a `0.0.0.0/0` en el security group de `tools` (con login obligatorio y sin acceso anónimo), el mismo cambio en `bootstrap-cloud.sh` (y sin la poda de Nexus por CIDR, que la habría retirado) y la protección de `main` con `continuous-integration/jenkins/pr-merge` como check obligatorio, conservando el resto de la configuración (1 aprobación, descarte de aprobaciones viejas, sin saltos para administradores, historial lineal, conversaciones resueltas).
- **Verificación / cambios del equipo:** se leyó la protección vigente antes de reemplazarla y se comprobó después con `gh api`; el contexto exacto (`continuous-integration/jenkins/pr-merge`) se confirmó en los estados del PR #59. La regla de AWS se verificó con `DescribeSecurityGroups`. Riesgo aceptado: Nexus queda expuesto a internet con login. _Revisión: por completar por el revisor del PR._

### 2026-11-08 · `jmrojas06` · Correcciones de documentación tras el cierre (cobertura, rollback y check obligatorio)

- **Prompt:** *"ya lo aprobo y el mismo hizo squash"* (sobre #60) y, antes, *"desde main entonces corregimos cosas de la documentacion, metricas y esas cosas"*.
- **Qué produjo:** la sección 2.1 de `ensayo-pipeline-real.md` con la cobertura de Sonar antes y después de `lombok.config` (leída de la API de SonarQube tras el build `main` #6), la actualización de la matriz de la rúbrica (rollback de prod hecho, `v1.1.0` aprobada, check obligatorio activo) y una nota en `video-pipeline.md` de que el video se grabó antes de ese cambio.
- **Verificación / cambios del equipo:** las cifras se contrastaron con `api/measures/component` de SonarQube; el check obligatorio con `gh api …/protection`; el rollback con la API de Jenkins y el `RELEASES` de la VM de prod. _Revisión: por completar por el revisor del PR._

---

## Reflexión crítica del equipo

_Se completa al cierre de cada sprint (en la retrospectiva): qué aportó la IA, qué errores cometió y cómo se detectaron, qué decisiones se tomaron en contra de su sugerencia y qué aprendimos haciendo el trabajo nosotros._

### Sprint 1 · Contribución individual de `pierrecuevas` (Pierre Cuevas)

Apoyarme en la IA para la parte técnica (Dockerfiles, compose por ambientes y la suite de pruebas unitarias, de integración y E2E) me permitió sacar tareas repetitivas en tiempo récord, como armar 11 clases de prueba y 9 Dockerfiles. Aun así, la experiencia dejó claro que no se puede confiar a ciegas en lo que genera. Hubo tropiezos que solo saltaron al ejecutar los comandos en local y revisar los logs a fondo:

- Una prueba de integración fallaba porque intentaba leer de Redis antes de que promotion terminara de escribir (el fallo era de la prueba, no del código).
- La IA marcó la prueba de rendimiento como superada cuando en mi máquina fallaba el 75 % de las veces.
- Los selectores iniciales de Playwright venían mal planteados y un script traía saltos de línea de Windows (CRLF) que rompían la ejecución en Linux.

Lo más rescatable fue que, al depurar y correr las pruebas de integración y E2E, aparecieron errores graves del sistema que habrían pasado desapercibidos: el gateway dejaba ingresar a personas contagiadas, promotion omitía la actualización en Redis si el usuario no tenía contactos registrados y el formulario enviaba payloads vacíos. Enfrentarlos me obligó a entender la arquitectura a fondo para explicarlos y corregirlos.

**Criterio y decisiones tomadas:**

- Mantener la autoría real en el historial de Git (cero créditos automáticos a la IA en commits o PR).
- Revisar cada cambio de José antes de aprobarlo y no aceptar un "ya funciona" sin ver la prueba corriendo.
- Exigir la ejecución exitosa de las pruebas como único criterio de aceptación; nada de dar por hecho un "ya funciona".

**Compromiso para el Sprint 2:** comprender al 100 % el impacto de cada cambio antes de subirlo y validar todo en un navegador real desde el día uno.

*Redacté esta reflexión con apoyo de la IA a partir de lo que pasó en el sprint; la revisé y la ajusté.*

### Sprint 1 · Contribución individual de `jmrojas06`

Usé la IA en casi todo el trabajo de plataforma: el *bootstrap* de AWS con scripts Bash, Jenkins y su *Shared Library* como código, la entrega continua con semantic-release, el despliegue por SSH y el rollback, y también para revisar los PR de Pierre y redactar la documentación. Me permitió tener en cuatro días una plataforma que se reconstruye con un comando (`up.sh`, 3,5 min) y un pipeline que llevó `v1.0.0` y `v1.1.0` hasta los tres ambientes. Pero también se equivocó o dio por bueno algo que no lo era, y casi todo salió a la luz al ejecutar y consultar los datos reales:

- Al reconstruir Jenkins con `up.sh` desde una copia local atrasada, el job de rollback no entró. Se vio por una capa `CACHED` en el log y se confirmó en la API de Jenkins y en el `jenkins.yaml` local (0 menciones de `rollback`, contra 5 en `main`).
- La cobertura de líneas medida con JaCoCo (p. ej. auth, 89,6 %) no coincidía con la que mostraba SonarQube (56,7 %). Sonar combina líneas y ramas; se confirmó con su API (líneas 88,6 %, ramas 13,8 %) y se explicó en la evidencia para que no se lea como menos del 60 %.
- El primer build de `main` omitió build, pruebas y SonarQube porque lo último que había cambiado eran documentos, y varios PR se habían fusionado con Jenkins apagado. La CI completa sobre el código final solo existió cuando abrimos un PR que tocaba `jenkins/`.
- En la revisión de #48 la IA detectó cosas que el código generado pasaba por alto: el nginx entregaba la ruta decodificada a los servicios y el README de desarrollo local no arrancaba sin las variables nuevas.

**Criterio y decisiones tomadas:**

- Todo paso a producción lo aprueba una persona en Jenkins (Pierre en `v1.0.0`); la IA no fusionó ni aprobó nada sin una instrucción explícita mía.
- Cada afirmación de la evidencia se contrastó con la API o con la propia VM (Jenkins, SonarQube, Nexus, GitHub y `docker ps`), no con lo que la IA recordaba.
- El equipo descartó el *token* fine-grained que la IA proponía porque la organización no lo permite, y usó uno *classic* con scope `repo`.
- Se provocó a propósito un fallo de despliegue en dev para comprobar los avisos, en lugar de dar por hecho que funcionaban.

**Compromiso para el Sprint 2:** correr `git pull` antes de `up.sh`, activar el check obligatorio de Jenkins en `main` para que ningún PR se fusione sin CI y seguir exigiendo evidencia real antes de dar algo por terminado.

*Redacté esta reflexión con apoyo de la IA a partir de lo que pasó en el sprint; la revisé y la ajusté.*
