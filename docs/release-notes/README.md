# Versionado y release notes (HU-09, #16)

Nadie escribe versiones a mano. En cada *merge* a `main`, la etapa **Release (semantic-release)** del [`Jenkinsfile`](../../Jenkinsfile) ejecuta [semantic-release](https://semantic-release.gitbook.io/) con la configuración de [`.releaserc.json`](../../.releaserc.json):

1. Lee los **Conventional Commits** desde el último tag (en `main` cada commit es el título del PR, validado por commitlint).
2. Decide la versión: `fix`/`perf` → PATCH, `feat` → MINOR, `feat!` o `BREAKING CHANGE` → MAJOR; `docs`, `test`, `ci`, `build`, `chore`, `refactor` → **no hay versión** y el pipeline no despliega nada.
3. Crea el tag `vX.Y.Z` y la **GitHub Release** con las notas agrupadas (Funcionalidades, Correcciones, Rendimiento, Reversiones).
4. Genera `CHANGELOG.md`, lo adjunta a la Release y lo archiva en el build de Jenkins.
5. Deja la versión en `.release/version`; con ella el pipeline etiqueta las 9 imágenes (`X.Y.Z` y `sha-<commit>`, nunca `latest`) y las despliega.

La primera versión publicada es **`v1.0.0`** (no había tags).

## Dónde están las release notes

| Dónde | Qué |
|---|---|
| [Releases en GitHub](https://github.com/ingesoft5/circle-guard-finalproject-A00405392-A00405468/releases) | Una entrada por versión con las notas y `CHANGELOG.md` adjunto |
| Artefactos del build de `main` en Jenkins | `CHANGELOG.md` de esa versión |
| Nexus (`circleguard/<servicio>:X.Y.Z`) | Las imágenes de esa versión, con etiquetas OCI `version`, `revision` y `source` |
| `/opt/circleguard/RELEASES` en cada VM | Historial de lo desplegado en ese ambiente: fecha, versión, motivo (`deploy` o `rollback`), commit y build |

## Por qué el `CHANGELOG.md` no se commitea a `main`

`main` está protegida: solo acepta cambios por PR aprobado, también para administradores. El plugin `@semantic-release/git` necesitaría hacer *push* directo del changelog, lo que obligaría a abrir una excepción a la protección para el *bot*. Se prefirió mantener la protección intacta y publicar las notas donde ya quedan versionadas: la Release (fuente de verdad) y el artefacto del build.

## Cómo probar el versionado sin publicar

```bash
docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp/h -e npm_config_cache=/tmp/h/npm \
  -e GITHUB_TOKEN="$(gh auth token)" -v "$PWD":/repo -w /repo node:22.23.4-bookworm \
  npx -y -p semantic-release@24 -p @semantic-release/changelog@6 -p @semantic-release/exec@7 \
         -p conventional-changelog-conventionalcommits@9 semantic-release --dry-run --no-ci
```

Imprime la versión que saldría y las notas, sin crear tag ni Release.
