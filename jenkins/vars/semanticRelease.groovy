// Calcula y publica la versión en main con semantic-release (HU-09, #16): lee los Conventional Commits
// desde el último tag, crea el tag vX.Y.Z y la GitHub Release con las notas, y deja CHANGELOG.md en el workspace.
// Devuelve "1.2.0" si hubo versión nueva, o "" si ningún commit la amerita (docs, ci, test, chore...).
// Sin @semantic-release/git: main solo acepta PR, así que el CHANGELOG no se commitea; se adjunta a la
// Release y queda como artefacto del build (ver docs/release-notes/README.md). Configuración: .releaserc.json.
String call() {
    sh 'rm -rf .release CHANGELOG.md'
    withCredentials([usernamePassword(credentialsId: 'github-token', usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
        // El checkout de multibranch no trae los tags; sin ellos semantic-release empezaría siempre en 1.0.0.
        sh '''repo="$(git config --get remote.origin.url | sed -E 's#.*github.com[/:]##; s#\\.git$##')"
              git fetch --quiet "https://${GH_USER}:${GH_TOKEN}@github.com/${repo}.git" \
                  '+refs/tags/*:refs/tags/*' "+refs/heads/${BRANCH_NAME}:refs/remotes/origin/${BRANCH_NAME}"
              echo "[semanticRelease] Último tag: $(git describe --tags --abbrev=0 2>/dev/null || echo 'ninguno (primera versión: 1.0.0)')"'''
        docker.image('node:22.23.4-bookworm').inside('--entrypoint=') {
            sh '''set -e
                export HOME="$WORKSPACE/.release/home" npm_config_cache="$WORKSPACE/.release/npm"
                export GITHUB_TOKEN="$GH_TOKEN" GIT_BRANCH="$BRANCH_NAME" CI=true
                mkdir -p "$HOME"
                npx -y -p semantic-release@24 -p @semantic-release/changelog@6 -p @semantic-release/exec@7 \
                       -p conventional-changelog-conventionalcommits@9 semantic-release'''
        }
    }
    String version = fileExists('.release/version') ? readFile('.release/version').trim() : ''
    echo version ? "[semanticRelease] Versión nueva: v${version}" : '[semanticRelease] Ningún commit amerita una versión nueva: no se publica ni se despliega'
    return version
}
