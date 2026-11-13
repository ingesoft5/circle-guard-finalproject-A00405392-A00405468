# Infraestructura con Terraform (HU-14, E2.1)

Declara en código toda la infraestructura de AWS de la Parte 2: una red por ambiente, un nodo de Kubernetes (k3s) por ambiente, la VM de herramientas, el registro de imágenes, los buckets de respaldo y las alarmas. Todo se crea y se destruye con un comando y queda un estado remoto con bloqueo.

Se ejecuta con [`run.sh`](run.sh), que corre Terraform, tflint y Trivy **dentro de Docker con versiones fijas**: el equipo y Jenkins usan exactamente las mismas, y no hay que instalar nada más que Docker.

> Diagrama y decisiones de diseño: [`docs/arquitectura/infra-terraform.md`](../../docs/arquitectura/infra-terraform.md) · Evidencia: [`docs/evidencias/sprint-2/hu-14-terraform.md`](../../docs/evidencias/sprint-2/hu-14-terraform.md)

## Estructura

```
infra/terraform/
  run.sh                 un solo punto de entrada (Terraform, tflint y Trivy en Docker)
  bootstrap/             bucket S3 + tabla DynamoDB del estado (estado local, se aplica una vez)
  modules/
    network/             VPC, subred pública, internet gateway, rutas, security group
    compute/             IP elástica, rol IAM, VM Ubuntu 24.04 (IMDSv2, disco cifrado), k3s o tools
    registry/            repositorios ECR con tags inmutables y escaneo al subir
    storage/             bucket S3 de respaldos (versionado, cifrado, sin acceso público)
    observability/       alarmas de CloudWatch, tema SNS y grupo de logs
  envs/
    tools/               VM de Jenkins, SonarQube y Nexus + ECR           10.0.0.0/16
    dev/  stage/  prod/  un nodo k3s + bucket + alarmas cada uno          10.10.x · 10.20.x · 10.30.x
  scripts/
    write-hosts-env.sh   salidas de Terraform -> infra/bootstrap/.state/hosts.env
```

Cada módulo trae sus pruebas en `tests/` (`terraform test` con un proveedor simulado: no tocan AWS). Los ambientes solo llaman a los módulos con sus variables; la lógica vive en los módulos.

## Requisitos

- Docker (y tu usuario en el grupo `docker`).
- Credenciales de AWS en `~/.aws` (perfil `default`).
- La llave SSH `infra/bootstrap/.state/circleguard-key.pem` de la Parte 1.

## Primera vez

```bash
# 1. Estado remoto (una sola vez por cuenta): crea el bucket S3 y la tabla DynamoDB.
./infra/terraform/run.sh bootstrap apply
./infra/terraform/run.sh bootstrap output -raw backend_config > infra/terraform/backend.hcl

# 2. Variables de cada ambiente (no se versionan: llevan tu IP, tu correo y tu llave pública).
for e in tools dev stage prod; do cp infra/terraform/envs/$e/terraform.tfvars.example infra/terraform/envs/$e/terraform.tfvars; done
#    edita cada terraform.tfvars con tus valores
```

`backend.hcl` y `terraform.tfvars` **no se versionan**: llevan el ID de la cuenta, una IP y un correo, y este repositorio es público. Para que otra persona trabaje se le pasa `backend.hcl` por un canal privado.

## Uso diario

| Comando | Qué hace |
|---|---|
| `./infra/terraform/run.sh check` | `fmt`, `validate`, pruebas de los módulos, `tflint` y Trivy (lo mismo que correrá Jenkins) |
| `./infra/terraform/run.sh fmt-fix` | Arregla el formato |
| `./infra/terraform/run.sh dev plan` | Muestra qué cambiaría; no toca nada |
| `./infra/terraform/run.sh dev apply` | Crea o cambia (pide confirmación) |
| `./infra/terraform/run.sh dev destroy` | Destruye el ambiente (pide confirmación) |
| `./infra/terraform/run.sh drift [ambiente...]` | Compara lo desplegado con el código; sale 2 si alguien cambió algo a mano |
| `./infra/terraform/scripts/write-hosts-env.sh tools dev stage prod` | Escribe `hosts.env` con las IP, para los scripts de `infra/bootstrap/` |

Para entrar a un nodo: `terraform output ssh` (por ejemplo `./infra/terraform/run.sh dev output -raw ssh`) y, dentro, `sudo k3s kubectl get nodes`.

## Detección de *drift*

`run.sh drift` corre `plan -detailed-exitcode` en cada ambiente: sale **0** si todo coincide con el código, **2** si alguien cambió algo a mano en la consola y **1** si no pudo planificar. Es lo que ejecutará Jenkins de forma programada (HU-16, cuando Jenkins tenga las credenciales de AWS). La evidencia de un *drift* provocado y detectado está en `docs/evidencias/sprint-2/`.

## Costos (estimados, precios bajo demanda de us-east-1)

| Qué | Costo |
|---|---|
| Estado remoto (S3 + DynamoDB) | centavos al mes |
| tools (`t3.xlarge`) | ~USD 0,17 por hora encendida |
| dev y stage (`t3.large`, cada uno) | ~USD 0,08 por hora encendida |
| prod (`t3.xlarge`) | ~USD 0,17 por hora encendida |
| Discos (gp3) e IP elásticas | ~USD 0,05 por hora entre los 4 |
| **Los 4 ambientes encendidos** | **~USD 0,55 por hora** |

El presupuesto del proyecto es de USD 50 al mes (`infra/bootstrap/bootstrap-budget.sh`): se enciende para trabajar y se destruye o apaga al terminar.

## Cambio de plataforma (más adelante, con confirmación aparte)

Hoy conviven la infraestructura de la Parte 1 (VMs creadas con Bash en `infra/bootstrap/`, con sus IP elásticas) y esta. Para migrar:

1. Importar cada IP elástica a su ambiente (el webhook de GitHub, el README y el informe apuntan a ellas):
   `./infra/terraform/run.sh <ambiente> import module.compute.aws_eip.this <eipalloc-...>`
2. `./infra/terraform/run.sh <ambiente> apply`: la IP pasa a la VM nueva y la vieja la pierde.
3. `./infra/terraform/scripts/write-hosts-env.sh tools dev stage prod` y `./infra/bootstrap/bootstrap-tools.sh` para dejar Jenkins, SonarQube y Nexus en la VM nueva.
4. Destruir las VMs de Bash. Hacerlo **después** de que la Parte 1 esté calificada.

## Problemas conocidos

- **`AddressLimitExceeded` al crear un ambiente**: AWS permite 5 IP elásticas por región y las de la Parte 1 siguen reservadas aunque sus VM estén apagadas (4 de 5). Con un solo cupo libre se prueba **un ambiente a la vez** (`apply`, comprobar, `destroy`). En la migración no hace falta cupo extra porque las IP existentes se importan (ver arriba).

- **`tflint --init` falla con `connection reset by peer`** al bajar el plugin de AWS: pasa en algunas redes. Se instala a mano desde el release oficial y se comprueba su checksum:
  ```bash
  D="$HOME/.cache/circleguard-iac/tflint/github.com/terraform-linters/tflint-ruleset-aws/0.50.0"; T="$(mktemp -d)"; B=https://github.com/terraform-linters/tflint-ruleset-aws/releases/download/v0.50.0
  (cd "$T" && curl -sSfL -O "$B/tflint-ruleset-aws_linux_amd64.zip" -O "$B/checksums.txt" && grep ' tflint-ruleset-aws_linux_amd64.zip' checksums.txt | sha256sum -c - && mkdir -p "$D" && unzip -o -q tflint-ruleset-aws_linux_amd64.zip -d "$D" && chmod +x "$D/tflint-ruleset-aws"); rm -rf "$T"
  ```
- **El proveedor simulado de las pruebas valida el formato de los ARN**: si una prueba nueva usa un recurso con ARN, hay que darle uno válido con `override_resource` (ver `modules/observability/tests/`).
- **Cambiar `k3s_version` no reinstala k3s** en una VM existente (el `user_data` corre una sola vez): se reemplaza la VM con `run.sh <ambiente> apply -replace=module.compute.aws_instance.this`.
