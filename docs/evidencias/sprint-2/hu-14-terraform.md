# HU-14 · Infraestructura con Terraform — evidencia (E2.1)

Issue [#21](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/21) · Sprint 2 · Responsable A (`jmrojas06`). Código en [`infra/terraform/`](../../../infra/terraform/README.md), diagrama y decisiones en [`docs/arquitectura/infra-terraform.md`](../../arquitectura/infra-terraform.md).

Las pruebas reales se hicieron el 13 de nov de 2026 en la cuenta de AWS del proyecto (us-east-1). Los archivos de salida están en esta carpeta y **no llevan** el ID de la cuenta, el correo de las alarmas, la IP del administrador ni la llave pública (se reemplazan por `<ID-CUENTA>`, `<CORREO-ALARMAS>`, `<IP-ADMIN>` y `<LLAVE-PUBLICA>`).

## 1. Criterios del issue y de la rúbrica (P2.2)

| Criterio | Resultado | Evidencia |
|---|---|---|
| Módulos reutilizables: `network`, `compute`, `registry`, `storage`, `observability` | Los 5 existen. `network`, `compute` y `observability` se instancian en los 4 ambientes; `registry` en tools y `storage` en dev, stage y prod | [`infra/terraform/modules/`](../../../infra/terraform/modules) |
| 3 ambientes por variables (más tools) | `envs/dev`, `stage`, `prod` y `tools` llaman a los mismos módulos; solo cambian nombre, rango de red, tipo de instancia y llave del estado | `diff` entre `envs/dev` y `envs/stage`/`envs/prod` |
| Estado remoto con bloqueo | Bucket S3 versionado y cifrado más tabla DynamoDB, creados por `bootstrap/`; cada ambiente usa su propia llave de estado y el bloqueo con DynamoDB **y** `use_lockfile` | [`hu-14-bootstrap-apply.txt`](hu-14-bootstrap-apply.txt) (6 recursos) |
| `terraform plan` limpio | Después del `apply`, el segundo `plan -detailed-exitcode` dice «No changes» y sale con código 0 en los 4 ambientes | [`tools`](hu-14-plan-limpio-tools.txt) · [`dev`](hu-14-plan-limpio-dev.txt) · [`stage`](hu-14-plan-limpio-stage.txt) · [`prod`](hu-14-plan-limpio-prod.txt) |
| Se puede destruir sin residuos | `destroy` de los 4 ambientes y consulta a la API de AWS: no queda ninguna VPC, instancia, repositorio ECR, alarma ni tema SNS de Terraform | [`tools`](hu-14-destroy-tools.txt) · [`dev`](hu-14-destroy-dev.txt) · [`stage`](hu-14-destroy-stage.txt) · [`prod`](hu-14-destroy-prod.txt) |
| Diagrama coherente con lo desplegado | El diagrama refleja los recursos creados (verificados abajo) | [`infra-terraform.md`](../../arquitectura/infra-terraform.md) |
| `fmt`, `validate` y `tflint` (y Trivy) | `./infra/terraform/run.sh check` sale con código 0: formato, 10 directorios validados, 22 pruebas de módulos, tflint y Trivy sin hallazgos HIGH ni CRITICAL | ver sección 3 |
| Detección de *drift* | Se cambió a mano la etiqueta `Role` de la VM de dev; `run.sh drift dev` salió con código 2 y mostró la diferencia; tras restaurar con `apply` salió con código 0 | [detectado](hu-14-drift-detectado.txt) · [detalle del plan](hu-14-drift-plan.txt) · [restaurado](hu-14-drift-restaurado.txt) |
| *Plan programado* de *drift* en Jenkins | **Pendiente de HU-16 (#23):** requiere las credenciales de AWS dentro de Jenkins, que se crean en esa historia. El comando que ejecutará ya está probado | — |

## 2. Qué se creó en cada ambiente

| Ambiente | Recursos | VM | Resultado |
|---|---|---|---|
| tools | 41 | `t3.xlarge`, disco 60 GB | VPC `10.0.0.0/16`, VM de herramientas, 9 repositorios ECR (tags inmutables, escaneo al subir), alarmas. Plan limpio. [apply](hu-14-apply-tools.txt) · [salidas](hu-14-output-tools.txt) |
| dev | 31 | `t3.large`, disco 40 GB | VPC `10.10.0.0/16`, nodo k3s, bucket de respaldos, alarmas. Plan limpio y `kubectl get nodes` en `Ready`. [apply](hu-14-apply-dev.txt) · [k3s](hu-14-k3s-dev.txt) |
| stage | 31 | `t3.large`, disco 40 GB | VPC `10.20.0.0/16`, igual que dev. Plan limpio y nodo `Ready`. [apply](hu-14-apply-stage.txt) · [k3s](hu-14-k3s-stage.txt) |
| prod | 31 | `t3.xlarge`, disco 50 GB | VPC `10.30.0.0/16`, igual que dev. Plan limpio y nodo `Ready`. [apply](hu-14-apply-prod.txt) · [k3s](hu-14-k3s-prod.txt) |

Los 3 nodos k3s respondieron `Ready` con la misma versión, `v1.37.2+k3s1`, pocos segundos después de que `kubectl` estuvo disponible.

### Verificación contra la API de AWS (no solo contra Terraform)

Para comprobar que lo desplegado cumple lo que dicen las pruebas, se consultó a AWS directamente después del `apply`:

- **tools** ([`hu-14-verificacion-aws-tools.json`](hu-14-verificacion-aws-tools.json)): 9 repositorios ECR con tags `IMMUTABLE`, escaneo al subir y cifrado AES256; la VM exige IMDSv2; el disco es gp3 de 60 GB y está cifrado; el puerto 22 no está abierto al mundo; las 2 alarmas existen y están en `OK`.
- **dev** ([`hu-14-verificacion-aws-dev.json`](hu-14-verificacion-aws-dev.json)): IMDSv2 obligatorio; disco gp3 de 40 GB cifrado; SSH (22) y API de Kubernetes (6443) solo desde IP restringidas (administración y tools), y 80 y 443 abiertos; el rol de la VM solo puede **leer** de ECR; el bucket de respaldos tiene versionado, cifrado AES256 y los 4 bloqueos de acceso público; las 2 alarmas en `OK`.

## 3. Controles estáticos

```text
./infra/terraform/run.sh check
  fmt        sin diferencias
  validate   10 directorios: bootstrap, 5 modulos y 4 ambientes
  test       22 pruebas (compute 5, network 4, observability 5, registry 4, storage 4), 0 fallos
  lint       tflint con el ruleset de AWS 0.50.0, sin hallazgos
  scan       Trivy 0.76.2 (--severity HIGH,CRITICAL), codigo 0
```

Las exclusiones de Trivy (`# trivy:ignore:`) están junto a cada recurso con su justificación: ingreso y salida abiertos a propósito (D2-08), S3 y DynamoDB sin llave KMS propia (costo), tema SNS sin cifrado propio (CloudWatch no puede publicar en uno cifrado con la llave administrada de AWS) y repositorios ECR con cifrado AES256.

## 4. Incidente durante la prueba: límite de IP elásticas

El primer `apply` de dev falló con `AddressLimitExceeded`. AWS permite **5 IP elásticas por región** y las 4 de la Parte 1 siguen reservadas aunque sus VM estén apagadas; con la IP de `tools` ya eran 5. Se cortó la secuencia, se destruyeron `tools` y los restos parciales de dev y stage (26 recursos cada uno), y los ambientes se probaron **de a uno** (crear, comprobar, destruir). El error original está en [`hu-14-incidente-limite-eip.txt`](hu-14-incidente-limite-eip.txt).

Lección para el cambio de plataforma: no se pueden tener a la vez las 4 IP nuevas y las 4 actuales (8 > 5). Por eso las IP existentes se **importan** a Terraform (`import`) en lugar de crear nuevas; el total se mantiene en 4 y, de paso, las URL entregadas en la Parte 1 siguen apuntando al mismo lugar.

## 5. Integración con el bootstrap de la Parte 1

`scripts/write-hosts-env.sh` convierte las salidas de Terraform en el `hosts.env` que leen los scripts de `infra/bootstrap/`. Se probó contra dev: produce las mismas tres claves por ambiente (`DEV_INSTANCE_ID`, `DEV_PUBLIC_IP`, `DEV_PRIVATE_IP`) que el archivo de la Parte 1 y con permiso 600. [`hu-14-hosts-env.txt`](hu-14-hosts-env.txt).

## 6. Costo de la prueba

Los 4 ambientes se encendieron de uno en uno durante unos minutos cada uno, más el estado remoto (S3 y DynamoDB, que se queda). Estimado con precios bajo demanda de us-east-1: **menos de USD 0,50 en total**; la factura de AWS puede tardar un día en reflejarlo. El presupuesto del proyecto es de USD 50 al mes.

## 7. Qué NO se hizo (a propósito)

- **El cambio de plataforma:** no se importaron las IP elásticas actuales ni se destruyeron las VM de la Parte 1. Hay que hacerlo después de que la Parte 1 esté calificada y con confirmación aparte. El procedimiento está en [`infra/terraform/README.md`](../../../infra/terraform/README.md).
- **Desplegar la aplicación en k3s:** es HU-15 (#22). Aquí solo se comprueba que el clúster arranca.
- **El *plan programado* en Jenkins:** HU-16 (#23), por las credenciales de AWS.
