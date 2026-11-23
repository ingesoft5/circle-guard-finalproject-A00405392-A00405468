# HU-15 · Los 8 servicios juntos y el HPA bajo carga: evidencia (E2.2)

Complementa [`hu-15-kubernetes.md`](hu-15-kubernetes.md), que dejó dos límites abiertos: `dashboard`, `file` y `notification` solo estaban validados con `helm template`, y el HPA solo se había leído en reposo (`cpu: 5%/70%`). Aquí se cierran los dos. Salidas completas, sin editar: [`hu-15-hpa-carga.txt`](hu-15-hpa-carga.txt).

## Dónde se corrió

Un k3s local en Docker de la misma versión que instala Terraform (`rancher/k3s:v1.37.2-k3s1`), en el portátil del equipo (15 GB de RAM, 12 núcleos), con el chart de `main` y `values-local.yaml`, más:

```bash
./deploy/helm/local.sh deploy --set services.dashboard.enabled=true --set services.file.enabled=true --set services.notification.enabled=true
```

Las imágenes `local/circleguard-*:local` son de un build de hace cuatro días (anterior a #74 y #76); para esta prueba no importa, porque no se ejercita nada de lo que cambiaron. No es AWS: el nodo de AWS de producción tiene 16 GB y no se probó aquí.

## 1. Los 8 servicios y el middleware corren juntos

13 pods en `Running` y `1/1` (auth, identity, form, promotion, gateway, dashboard, file, notification, web, PostgreSQL, Neo4j, Kafka y Redis), 9 HPA creados y la prueba de humo (`local.sh smoke`: sondas de los 5 núcleo, versión y login real de `super_admin`) en `TODO OK`. Tardó unos 5 minutos en estar todo listo desde `helm install`.

Memoria: en reposo, los pods de aplicación usan entre 129 MiB (file) y 310 MiB (promotion); Neo4j 527 MiB y Kafka 325 MiB. Con todo arriba, el PC pasó de 5,5 a ~9,2 GB usados (incluido el k3s y el resto de lo que ya corría). **Cabe en 15 GB, pero con poco margen: es la razón por la que `dev` y `stage` (8 GB) siguen con esos tres servicios apagados.**

## 2. El HPA escala bajo carga real

Carga: Locust 2.32.3 con el escenario `LoginUser` de `tests/performance` (50 usuarios, rampa de 10/s, 4 minutos) contra el Ingress del k3s. El login es el cuello de botella conocido (BCrypt, ver `docs/pruebas/informe-calidad.md`).

| Momento (UTC) | auth | Qué pasó |
|---|---|---|
| 02:37:47 | 1 réplica, `cpu: 7%/70%` | línea base, sin carga |
| 02:37:50 | | empieza Locust |
| 02:38:17 | 1 réplica, `cpu: 6597%/70%` | la CPU de auth supera con mucho su `request` de 100m |
| **02:38:38** | **2 réplicas** | el HPA sube auth de 1 a 2 (el máximo del chart) |
| 02:38 a 02:42 | 2 réplicas | se mantiene mientras dura la carga (4847 % a 9451 % del request) |

Locust: 5 337 logins, **0 fallos**, 22,3 req/s, mediana 1,2 s, p95 2,0 s, máximo 3,6 s.

Otras dos observaciones del mismo cluster:

- **Escala hacia abajo.** Al arrancar, la JVM de auth consumió 655 % de su request y el HPA lo subió a 2 réplicas sin que hubiera carga; cuando se calmó, bajó a 1 a los ~5 minutos (02:37:55), que es la ventana de estabilización por defecto de Kubernetes.
- **No solo auth escaló.** Al terminar la prueba, `identity` (la llamada que hace auth en cada login) y `notification` también estaban en 2 réplicas. El de `notification` coincide con su propio arranque, no con la carga; el de `identity` es consecuencia de los logins.

## Qué no se pudo o no se probó

- **El máximo es 2 réplicas** (`autoscaling.maxReplicas: 2` del chart), así que se demostró 1 → 2, no una escalera más larga. Subir el máximo es un cambio de `values`, no de código.
- **El porcentaje que reporta el HPA es enorme (6 597 %)** porque el `request` de CPU de los servicios es de 100m y el login con BCrypt consume varios núcleos del portátil; en un nodo `t3.large` de 2 vCPU el techo es mucho más bajo. Sirve como prueba de que el HPA reacciona, no como medida de capacidad.
- **El k3s local no aplica `limits` de CPU** como lo haría un nodo con cgroups de producción; no se cambió ningún `request` para forzar el escalado (no hizo falta).
- **Los 8 servicios corrieron en un portátil, no en AWS.** Falta repetirlo en un nodo `t3.xlarge` de producción durante el ensayo en la nube.
- Las imágenes no son las de `v1.5.0` (ver arriba).
