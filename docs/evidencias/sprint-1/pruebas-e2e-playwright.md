# HU-07 (#14) · Pruebas E2E con Playwright sobre el cliente web — 2026-11-07

`tests/e2e-playwright/` (TypeScript, Playwright 1.49.1, Chromium). Corren contra el `BASE_URL` de un ambiente:

```bash
BASE_URL=http://<ip-del-ambiente> ./tests/e2e-playwright/run.sh   # imagen oficial de Playwright, sin instalar nada
```

Reportes: HTML en `tests/e2e-playwright/reports/html/` y JUnit en `reports/junit.xml` (para publicarlos en Jenkins). El `global-setup` deja un cuestionario de salud activo si el ambiente no tiene uno (idempotente).

| Flujo | Spec | Qué verifica |
|---|---|---|
| Inicio de sesión | `login.spec.ts` | Usuario válido: JWT `Bearer` + `anonymousId` y pase de acceso con QR. Clave incorrecta: alerta `[401] Invalid username or password` y sigue en `/login` |
| Cuestionario de salud | `questionnaire.spec.ts` | Responde SÍ/NO, envía, form responde 200 con el reporte guardado y las respuestas viajan en el cuerpo; vuelve al inicio |
| Validación de acceso | `gate-validation.spec.ts` | Un usuario abre su pase (QR firmado por auth); el guardia, en otro navegador, lo valida en la pestaña Scan → `GREEN` / `ACCESS GRANTED`. Código falso → `RED` / `ACCESS DENIED` |

## Resultado (compose de dev en local, dos ejecuciones seguidas)

```
  ✓  1 › gate-validation.spec.ts › el guardia valida el QR de un usuario sano y le da acceso (13.8s)
  ✓  2 › gate-validation.spec.ts › un código falso o alterado es rechazado (6.7s)
  ✓  3 › login.spec.ts › un usuario válido entra y ve su pase de acceso (5.3s)
  ✓  4 › login.spec.ts › una clave incorrecta muestra el error y no entra (4.9s)
  ✓  5 › questionnaire.spec.ts › el usuario responde el cuestionario y su reporte llega a form (8.4s)
  5 passed (1.8m)
```

## Errores de la aplicación que encontraron los E2E

| Error | Cómo se vio | Arreglo |
|---|---|---|
| **El login fallaba desde el navegador** cuando el web no está en el puerto 80 | Con `curl` el login daba 200, pero en Chromium: `[403] Request failed with status code 403`. nginx reenviaba `Host: $host` (sin puerto) y Spring, al ver un `Origin` con puerto distinto, lo trataba como CORS y lo rechazaba | `proxy_set_header Host $http_host` (conserva el puerto) |
| **El cuestionario enviaba siempre `responses: {}`** | `DynamicForm` guardaba las respuestas en su propio estado y nunca se las pasaba a la pantalla: los síntomas reportados no llegaban a form | `DynamicForm` acepta `onChange` y la pantalla guarda las respuestas |
| **Ningún guardia veía el escáner** | La pestaña Scan exige `gate:scan`, pero el rol `GATE_STAFF` no tenía ningún permiso asignado en las migraciones | Migración `V6__grant_gate_scan_to_gate_staff.sql` |
| **El escáner no funcionaba en web** | Exige cámara y huella (`expo-local-authentication`), que un navegador no tiene | En web, campo para pegar el código o recibirlo de un lector de QR USB (escribe como teclado y termina con Enter); en el celular no cambia nada |

`jest` de la app (`DynamicForm.test.tsx`): 2/2 después del cambio.
