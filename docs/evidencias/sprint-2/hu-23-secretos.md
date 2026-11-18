# HU-23 · Secretos en el repositorio — evidencia (E2.6, primer tramo)

Issue [#30](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/30) · Sprint 2. Cómo se usa y por qué: [`security/README.md`](../../../security/README.md). HU-23 incluye más criterios (Trivy, Sealed Secrets, RBAC, TLS); este documento cubre «cero credenciales en el repositorio» y «secretos del código base eliminados del historial o rotados».

## Qué se hizo

Se corrió **gitleaks v8.30.1** sobre los 91 commits del repositorio (todas las ramas) con sus reglas por defecto: **15 hallazgos**. Se clasificó cada uno **sin imprimir los valores** ([`hu-23-gitleaks-hallazgos.txt`](hu-23-gitleaks-hallazgos.txt)):

| Hallazgos | Qué son | Resolución |
|---|---|---|
| 2 (`bootstrap-tools.sh`) | **Falso positivo:** `curl -s localhost:$SONAR_PORT/...` se leyó como `usuario:clave`. No hay credencial | Excepción por línea exacta |
| 9 en `src/test/resources/**/application.yml` (los 8 servicios con `config/` y el de identity) | Un mismo valor de **64 caracteres hexadecimales**; decodificado es texto ASCII legible que empieza por «test», no una clave aleatoria. Solo lo usan las pruebas | Excepción por archivo, solo en pruebas |
| 1 en `IdentityEncryptionConverterTest.java` | Una clave de prueba de 32 caracteres que contiene «test» y «secret» en claro | Excepción por archivo |
| 3: `identity/src/main/resources/application.yml` y `identity/bin/**` (commit del import original) | **El único caso con consecuencias:** el código original traía ese mismo valor de prueba como `vault.secret` **por defecto** del servicio que cifra los correos. Hoy el archivo lee `${VAULT_SECRET}` **sin valor por defecto** y `bin/` ya no está en `main` | Excepción acotada a ese commit y ese archivo |

**Ningún ambiente usó ese valor:** cada ambiente genera sus secretos al azar (`openssl rand` en `deploy.sh`, `/dev/urandom` en `lib.sh`), nunca salen de la VM, y el valor histórico no aparece en ningún archivo de configuración real (se comprobó comparando su huella, sin imprimirlo). Por eso se considera **rotado en la práctica**.

## Resultado

[`hu-23-gitleaks-check.txt`](hu-23-gitleaks-check.txt) (`./security/run.sh check`):

- historial completo: **91 commits escaneados, 0 hallazgos** fuera de las excepciones revisadas;
- archivos actuales: **0 hallazgos**;
- **control negativo:** un token de GitHub falso generado al vuelo hace fallar el escaneo, y un directorio limpio pasa. Sin ese control, un escaneo que no detecta nada no demuestra nada.

## Lo que encontró el trabajo

1. **gitleaks decía «no leaks found» con 0 commits escaneados** cuando se corría dentro de un worktree de git (el archivo `.git` apunta fuera del directorio montado en Docker). Era un falso verde. El runner ahora escanea un clon espejo y **falla si se escanearon 0 commits**.
2. **El modo `dir` y el modo `git` reportan rutas distintas** (`/repo/services/...` frente a `services/...`): las excepciones por ruta con `^` funcionaban en uno y no en el otro. Se escribieron para aceptar los dos.
3. **La regla `curl-auth-user` leyó `localhost:$SONAR_PORT` como `usuario:clave`:** el valor que «filtró» no era secreto. La excepción se aplica a la línea y no al valor.

## Limitaciones

- **El escaneo aún no está en el pipeline de Jenkins.** Se agrega junto con Trivy en el mismo tramo de HU-23 y HU-16 (necesita Jenkins encendido para probarse).
- **El historial no se reescribió** (decisión documentada en `security/README.md`): los 15 valores siguen en los commits antiguos, aceptados por no ser credenciales reales.
- **Las excepciones de `src/test` son por archivo:** una credencial real puesta por error en esos dos archivos pasaría inadvertida; en el resto de pruebas sí se detecta.
