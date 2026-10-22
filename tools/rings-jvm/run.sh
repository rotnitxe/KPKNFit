#!/usr/bin/env bash
set -euo pipefail
here=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd "$here"
# Entorno cloud propio: sin la cola ni rutas de Windows. Se reutiliza el wrapper versionado del producto.
bash ../../android-native/gradlew --no-daemon --console=plain test "$@"
