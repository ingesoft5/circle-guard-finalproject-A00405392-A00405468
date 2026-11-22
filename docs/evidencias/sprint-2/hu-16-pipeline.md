# HU-16 · Pipeline que gobierna Terraform, Trivy y Kubernetes — evidencia (E2.3)

Issue [#23](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/23) · Sprint 2. Diseño y razones: [`docs/arquitectura/pipeline-k8s.md`](../../arquitectura/pipeline-k8s.md).

**Dónde se probó.** Todo lo que no necesita Jenkins ni AWS se corrió de verdad: las herramientas dentro de un «Jenkins» simulado (contenedor con el socket de Docker del host y un volumen `jenkins_home`, [`scripts/sim-jenkins.sh`](../../../scripts/sim-jenkins.sh)) y `promote.sh` contra un **k3s real local** de la misma versión que instala Terraform ([`hu-16-k3s-promote.txt`](hu-16-k3s-promote.txt)). **Los jobs y las etapas dentro de un Jenkins real y todo lo que toca AWS están escritos pero NO ejecutados**: necesitan una ventana de Jenkins y una de nube. Esa frontera está en la última sección.

## Criterios de aceptación del issue

| Criterio | Qué se hizo | Estado |
|---|---|---|
| `terraform plan/apply` en Jenkins con aprobación en prod | Job `circleguard-infra` ([`infra.Jenkinsfile`](../../../jenkins/infra.Jenkinsfile)): `fmt` + `validate`, Trivy IaC, `plan -out=tfplan` (se archiva `plan-<ambiente>.txt`), **aprobación** en prod y en todo `destroy`, y `apply` del **mismo** plan revisado | **Escrito.** Probado fuera de Jenkins: `terraform fmt`/`validate` de los 10 directorios dentro del simulador con credenciales por variables ([`hu-16-simulador-jenkins.txt`](hu-16-simulador-jenkins.txt)); `state list` por variables contra el estado **real** de S3 (solo lectura: 0 recursos en dev y tools, que están destruidos). **No ejecutado en Jenkins ni con `apply` real.** |
| Trivy (imágenes e IaC) con umbral bloqueante | En el [`Jenkinsfile`](../../../Jenkinsfile), en **cada build**: gitleaks del árbol, Trivy IaC y Trivy dependencias; en `main` con versión nueva, Trivy sobre las 9 imágenes recién publicadas, antes de promover. HIGH/CRITICAL bloquea; excepciones por CVE y con vencimiento | **Escrito.** Los mismos comandos corren dentro del simulador (`secrets-tree`, `iac`, `deps`, `selftest`, los 6 controles en verde). **No ejecutado en Jenkins** (la primera vez baja ~3 GB de bases de datos de Trivy). |
| `helm upgrade --install` por ambiente con promoción y aprobación a prod | Parámetro `TARGET=k8s`: `deployK8s` (ECR → kubeconfig por SSH → [`promote.sh`](../../../deploy/helm/promote.sh)) en dev, stage y prod con el `input` de prod de siempre | `promote.sh` **probado en un k3s real** (abajo). `deployK8s`, ECR y la aprobación en Jenkins **sin ejecutar**. |
| Notificación de fallos | `notifyTeam` sin cambios en el flujo principal; los dos jobs nuevos avisan éxito, fallo, espera de aprobación y drift | **Escrito.** Los avisos de los jobs nuevos no se han disparado. |
| Plan programado de drift (pasó de HU-14) | Job `circleguard-drift` ([`drift.Jenkinsfile`](../../../jenkins/drift.Jenkinsfile)): cada día, solo ambientes desplegados; INESTABLE si difiere, rojo si no puede leer el estado | **Escrito.** La lógica de «¿está desplegado?» se probó contra el estado real (vacío). El drift con un ambiente vivo se probó en HU-14 con `run.sh drift`. |
| Archivar el registro de cambios (`release.sh history --md`) | `promote.sh` lo escribe y `deployK8s` lo archiva con el build | Generado en la prueba local (tabla al final de [`hu-16-k3s-promote.txt`](hu-16-k3s-promote.txt)); el archivado en Jenkins sin ejecutar. |

## Lo que se midió

**Las herramientas dentro del contenedor de Jenkins.** El simulador corre `helm check` (76 pruebas), `secrets-tree`, `iac`, `deps` y `selftest` con salida 0 en 31 s, y `terraform fmt` + `validate` de los 10 directorios. **Control:** el código **anterior** (sin la traducción de rutas) en el mismo simulador **falla** con `ignore file not found: .trivyignore.yaml`: Docker monta una carpeta vacía porque la ruta del workspace no existe en el host.

**`promote.sh` en un k3s real** (cluster nuevo, imágenes locales, `values-dev.yaml`):

| Corrida | Qué comprueba | Resultado |
|---|---|---|
| A | cluster con la plataforma pero **sin** secretos (se borraron): debe volver a sellar y seguir | selló, desplegó `--atomic` (revisión 1), humo 7/7 sondas + versión + **login real** |
| B | la misma orden otra vez | **no toca** cert-manager, Sealed Secrets ni los secretos (`ya estan instalados` / `ya existe`); upgrade a la revisión 2; humo igual |
| control 1 | `smoke.sh --version v9.9.9` con otra versión corriendo | **falla** (`/version no menciona v9.9.9`, salida 1) |
| control 3 | `promote.sh dev v404` (imagen inexistente) | `--atomic` **revirtió** a la revisión 2 (la 3 quedó `failed`, la 4 `Rollback to 2`), `promote.sh` salió con error **sin correr el humo**, y `GET /version` siguió en `local` |

## Lo que encontró el trabajo

1. **Las herramientas no funcionaban dentro de Jenkins.** Los cuatro runners hacían `docker run -v "$PWD:/work"`; desde el contenedor de Jenkins esa ruta no existe en el host y la herramienta ve una carpeta vacía (Trivy y `helm` fallan, o peor, dan «verde» sobre nada). Pasó desapercibido porque todo se había probado en el portátil. Se arregló con [`scripts/hostpath.sh`](../../../scripts/hostpath.sh), que traduce cada ruta con los montajes del propio contenedor, **falla en voz alta** si la ruta no está en un volumen, y devuelve la ruta tal cual fuera de un contenedor. Se encontró **antes** de gastar una ventana de Jenkins.
2. **Un bug de `platform.sh` que solo aparece encadenado.** `cmd_seal` dejaba una trampa `RETURN` que sigue viva cuando vuelve `cmd_ensure`; con `set -u` fallaba con `cert: unbound variable` justo después de sellar con éxito. Las pruebas anteriores corrían `seal` como último comando y no lo veían. Corregido y verificado en la corrida A.
3. **El cluster de pruebas no agendaba nada con el disco del portátil casi lleno.** El kubelet pone el taint `disk-pressure` si queda menos del 10 % libre. `local.sh up` baja el umbral de desalojo del cluster de pruebas a 1 % (`K3S_EVICTION_HARD`). No afecta a los nodos de AWS.
4. **Cambiar `Jenkinsfile` o `jenkins/` construye los 8 servicios** (`changedServices`): el check de este PR tarda lo de un build completo.
5. **La prueba de humo tarda varios minutos en fallar** contra un host caído (reintenta cada sonda); un puerto cerrado no se midió hasta el final. Con un despliegue sano termina en segundos.
6. **El módulo `registry` (ECR) vive en `envs/tools`** junto con la VM, y la cuenta solo permite 5 IP elásticas. Para la ventana, `circleguard-infra ENV=tools ACTION=apply TARGET=module.registry` crea solo los 10 repositorios, sin VM ni IP.

## Limitaciones (lo que NO está probado)

- **Nada de esto se ha ejecutado en un Jenkins real:** ni el linter declarativo de los Jenkinsfile (solo `groovyc` y el YAML del JCasC), ni los jobs `circleguard-infra`/`circleguard-drift`, ni las etapas nuevas del flujo principal, ni la aprobación de prod con el rol de Desarrollador. Se prueba en la ventana de Jenkins.
- **Nada con AWS:** `terraform apply`, kubeconfig por SSH al nodo, subida a ECR (`amazon/aws-cli`), Let's Encrypt sobre `sslip.io` y el humo por HTTPS. Se prueba en la ventana de nube.
- **Credenciales:** falta un usuario de IAM propio para Jenkins (`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` al correr `up.sh`). No se pidieron ni se tocaron.
- Los escaneos nuevos corren en **todos** los builds: si la VM no puede bajar la base de datos de Trivy, bloquean también los PR.
- Trivy escanea las imágenes **después** de publicarlas en Nexus: bloquea la promoción, no la publicación.
- El simulador reproduce el montaje de volúmenes y el socket, no todo Jenkins (plugins, `withCredentials`, `input`).
