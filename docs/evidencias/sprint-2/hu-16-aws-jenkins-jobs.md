# HU-16 · Ejecuciones reales de los jobs de Jenkins contra AWS (22 a 23 de noviembre de 2026)

Datos de la API de Jenkins (`wfapi/describe`) del Jenkins de AWS. Es la primera vez que estos jobs corren contra la nube; los fallos están a la vista porque son parte de la evidencia.

| Job · build | Qué es | Resultado | Duración | Etapas | Qué pasó |
|---|---|---|---|---|---|
| `circleguard-infra` #1 | ECR (ENV=tools, TARGET=module.registry, ACTION=apply) | **SUCCESS** | 4.5 min | Validar el código (199 s) success<br>Trivy sobre la infraestructura (7 s) success<br>Plan (35 s) success<br>Apply (17 s) success | Crea los 9 repositorios ECR con tags inmutables |
| `circleguard-infra` #2 | dev apply, primer intento | **FAILED** | 1.8 min | Validar el código (98 s) success<br>Trivy sobre la infraestructura (5 s) success<br>Plan (1 s) failed<br>Aprobación (0 s) failed<br>Apply (0 s) failed | Falló: TARGET vacío (`T_TARGET: unbound variable`); corregido en jenkins/infra.Jenkinsfile |
| `circleguard-infra` #3 | dev apply, Replay con el Jenkinsfile corregido | **FAILED** | 2.1 min | Validar el código (99 s) success<br>Trivy sobre la infraestructura (5 s) success<br>Plan (17 s) failed<br>Aprobación (0 s) failed<br>Apply (0 s) failed | Falló en el `plan`: el correo de las alarmas era el texto `pendiente` y el módulo lo rechaza |
| `circleguard-infra` #4 | dev apply, Replay con el correo válido | **SUCCESS** | 3.6 min | Validar el código (95 s) success<br>Trivy sobre la infraestructura (5 s) success<br>Plan (32 s) success<br>Apply (73 s) success | Crea VPC, subred, grupo de seguridad, nodo k3s `t3.large`, IP elástica y alarmas (plan y apply del mismo `tfplan`) |
| `circleguard-k8s` #1 | dev <- 1.5.0, primer intento | **FAILED** | 12.2 min | Validar (0 s) success<br>Desplegar en Kubernetes (717 s) failed | Plataforma y secretos sellados bien; las imágenes no bajaban de ECR (`no basic auth credentials`). Pasó el límite de 10 min y `--atomic` revirtió solo |
| `circleguard-k8s` #2 | dev <- 1.5.0, con el nodo ya autorizado en ECR | **SUCCESS** | 6.1 min | Validar (0 s) success<br>Desplegar en Kubernetes (359 s) success | Despliega, la versión coincide y el login real devuelve token |
| `circleguard-k8s` #3 | dev <- 1.4.0 (una versión anterior) | **SUCCESS** | 3.9 min | Validar (0 s) success<br>Desplegar en Kubernetes (226 s) success | Despliegue de otra versión, base del rollback |
| `circleguard-k8s` #4 | dev <- 1.5.0 con TLS_ISSUER=letsencrypt | **SUCCESS** | 1.2 min | Validar (0 s) success<br>Desplegar en Kubernetes (68 s) success | Certificado real de Let's Encrypt |
| `circleguard-drift` #1 | drift diario (manual) | **UNSTABLE** | 1.6 min | Detectar drift (93 s) unstable | dev coincide con el código; tools difiere porque solo se aplicó `module.registry`; stage y prod omitidos (no existen) |
