#!/usr/bin/env bash
# hostpath <ruta>: la ruta de ese archivo o carpeta tal como la ve el Docker del HOST (se usa con source).
#
# Jenkins corre en un contenedor con el socket de Docker del host montado. Un `docker run -v "$PWD:/work"` lanzado
# desde ahí le pasa al host una ruta que solo existe dentro del contenedor: Docker monta una carpeta vacía y la
# herramienta no ve el código (el comentario de jenkins/vars/e2eTests.groovy cuenta lo mismo). Aquí se traduce con los
# montajes del propio contenedor (`docker inspect`). Fuera de un contenedor devuelve la ruta tal cual, así que los
# mismos scripts sirven en el portátil y en Jenkins.
#
# Si la ruta no está en ningún volumen del contenedor (p. ej. /tmp) NO se adivina: falla en voz alta, porque montar
# una ruta que el host no tiene da un «verde» falso con una carpeta vacía.
hostpath() {
  local p; p="$(readlink -f -- "$1")"
  if [[ ! -f /.dockerenv ]]; then printf '%s\n' "$p"; return 0; fi
  docker inspect "${HOSTNAME:-$(hostname)}" --format '{{range .Mounts}}{{.Destination}}{{"\t"}}{{.Source}}{{"\n"}}{{end}}' 2>/dev/null |
    awk -F'\t' -v p="$p" '
      NF == 2 && (p == $1 || index(p, $1 "/") == 1) && length($1) > best { best = length($1); src = $2 }
      END { if (best) { print src substr(p, best + 1); exit 0 } exit 1 }' ||
    { printf 'hostpath: %s no está en un volumen de este contenedor: el Docker del host no podría verla\n' "$p" >&2; return 1; }
}
