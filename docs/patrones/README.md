# Patrones de diseño (E2.4)

| Patrón | Tipo | Dónde | Historia | Documento |
|---|---|---|---|---|
| Circuit Breaker + Retry | Resiliencia | `auth → identity`, `dashboard → promotion` | [HU-17 (#24)](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/24) | [`resiliencia.md`](resiliencia.md) |
| External Configuration + Feature Toggle | Configuración | K del k-anonimato en `dashboard` y login LDAP en `auth`, por el ConfigMap del chart | [HU-18 (#25)](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/25) | [`configuracion.md`](configuracion.md) |
| Cache-aside | Rendimiento | _(pendiente)_ | [HU-19 (#26)](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/26) | _(pendiente)_ |
| Patrones existentes de la arquitectura | — | — | [HU-19 (#26)](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/issues/26) | _(pendiente)_ |
