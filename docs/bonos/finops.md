# FinOps (HU-29, bono)

Cómo controlamos el costo de la nube, con números reales de la cuenta. Tres cosas que pide el bono: monitoreo de costos, *scale-to-zero* y Spot. La historia completa del gasto (estimado y real) está en [`docs/operaciones/costos.md`](../operaciones/costos.md); aquí va lo que hicimos para gobernarlo.

## 1. Monitoreo de costos

- **Presupuesto con alertas.** `circleguard-mensual`, USD 60, con avisos por correo al 50 % y al 80 % del gasto real y al 100 % del pronóstico. Hoy (22 de nov) marca **USD 53,56 gastados (89 %) y un pronóstico de USD 61,28**, así que ya se superaron los umbrales del 50 % y del 80 %, y el pronóstico pasa del 100 %. Está configurado **sin créditos** a propósito: mide el consumo bruto, que es el que costaría si los créditos de AWS se acabaran.
- **Cost Explorer.** La captura del 1 al 22 de noviembre, por día y servicio y sin créditos, está en [`hu-29-cost-explorer.jpg`](../evidencias/sprint-2/hu-29-cost-explorer.jpg). Los picos del 9 y 10 de nov y del 14 al 17 son dos descuidos nuestros (VM encendidas sin trabajo útil), que explican USD 41 de los 49 de entonces.
- **Un informe reproducible.** [`scripts/finops.sh`](../../scripts/finops.sh) solo lee de AWS y muestra qué está encendido ahora y cuánto cuesta por hora, el consumo del mes por servicio, el estado del presupuesto y el ahorro de Spot. Con `--alerta` sale con código 1 si hay una instancia encendida, para usarlo al cerrar una sesión o desde un cron. Salida real: [`hu-29-finops-informe.txt`](../evidencias/sprint-2/hu-29-finops-informe.txt).

## 2. Scale-to-zero

Tenemos cuatro VM (herramientas, dev, stage y prod) que hacen falta solo en las ventanas de trabajo, así que el estado por defecto es **todo apagado** y cada encendido es una ventana corta y planeada.

| | Costo |
|---|---|
| Jenkins (`t3.xlarge`) encendido 24 h | USD 3,99 por día |
| Jenkins, las **4 ventanas del fin de semana del 21 y 22 de nov** (≈ 2 h 23 min en total) | **≈ USD 0,40** |
| Las 4 VM encendidas a la vez, por hora | USD 0,50 |
| Piso con todo apagado (5 discos gp3 y 4 IP elásticas reservadas) | USD 0,93 por día |

- **Cómo se hace:** `infra/bootstrap/up.sh` enciende y `down.sh` apaga; nada se destruye, los datos y las IP se conservan. Cada ventana se cierra verificando con `DescribeInstances` que todo quedó `stopped` (`scripts/finops.sh --alerta` hace la misma comprobación).
- **Lo que costó aprenderlo:** antes de la regla, dejar las 4 VM dos días y Jenkins cuatro días nos costó unos USD 41. Desde el 18 de nov, la ventana típica dura entre 11 y 50 minutos.
- **El piso no es cero.** Las IP elásticas y los discos se pagan aunque nada esté encendido. Destruir las VM de la Parte 1 después de la evaluación y soltar sus IP baja el cierre del mes de unos USD 61 a unos USD 56 (escenario B de `costos.md`); queda pendiente por ser la URL de la entrega de la Parte 1.

## 3. Spot

Precios reales de Spot (`DescribeSpotPriceHistory`, promedio de los últimos 7 días, la zona más barata de us-east-1) frente al on-demand:

| Tipo | Spot | On-demand | Ahorro |
|---|---|---|---|
| `t3.large` (dev y stage) | USD 0,0255 por hora | USD 0,0832 | **69 %** |
| `t3.xlarge` (herramientas y prod) | USD 0,0322 por hora | USD 0,1664 | 81 % |

**Lo que implementamos:** el módulo `compute` de Terraform tiene la variable `spot` (por defecto `false`). Con `spot = true` la VM se pide como Spot **persistente con parada**, no se termina al interrumpirse, así que sigue valiendo el patrón de apagar y encender en las ventanas. Los ambientes `dev` y `stage` la exponen: `terraform apply -var spot=true`. Hay dos pruebas nuevas del módulo (por defecto es on-demand; con `spot = true` pide Spot persistente con interrupción `stop`) y `./infra/terraform/run.sh check` pasa completo (24 pruebas de módulos, `fmt`, `validate`, tflint y Trivy). Salida en [`hu-29-terraform-check-spot.txt`](../evidencias/sprint-2/hu-29-terraform-check-spot.txt).

**Dónde conviene y dónde no:**

| Ambiente | Spot | Por qué |
|---|---|---|
| dev y stage | sí | Son efímeros y se prueban en ventanas; que AWS los pare no pierde nada que no se pueda recrear |
| herramientas (Jenkins, Sonar, Nexus) | no | Una interrupción a mitad de un build deja un `main` roto y datos de Nexus y Sonar en el disco |
| prod | no | La interrupción es una caída de servicio, y la sustentación necesita estabilidad |

Una ventana con los cuatro ambientes cuesta USD 0,4992 por hora en on-demand; con dev y stage en Spot (el resto on-demand), unos **USD 0,3838**: **23 % menos**, USD 0,12 por hora. Es poco dinero en absoluto, porque ya trabajamos con ventanas cortas; el ahorro grande viene de apagar, no de Spot.

## 4. Otros ahorros que ya estaban en el diseño

- **k3s en vez de EKS:** USD 0,10 por hora por plano de control, unos USD 216 al mes con tres clusters.
- **Sin NAT Gateway:** unos USD 32 al mes cada uno; el aislamiento lo dan la VPC propia y los grupos de seguridad.
- **Un ambiente de Terraform a la vez** en las pruebas, también por el límite de 5 IP elásticas.
- **ECR con limpieza** de imágenes viejas y discos gp3.
- **Validar en local primero:** el cluster, el pipeline y la seguridad se probaron en un k3s local y en un Jenkins simulado, a costo cero.

## 5. Lo que no hicimos

- **Parada automática.** No hay una función programada (EventBridge y Lambda) que apague las instancias olvidadas. El control es la disciplina de cerrar cada ventana y `finops.sh --alerta`; una alarma que apague sola sería el paso siguiente.
- **Un dashboard de costos** en Grafana. Los números salen del script y de Cost Explorer.
- **Spot probado en AWS.** La opción está en el módulo y probada con el proveedor simulado y `plan`; no hicimos un `apply` real con Spot, así que la disponibilidad de capacidad Spot en la hora del ensayo es un riesgo sin comprobar.
- **Savings Plans o instancias reservadas.** No tienen sentido con un uso de pocas horas al mes.
