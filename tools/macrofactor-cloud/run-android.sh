#!/usr/bin/env bash
set -euo pipefail
if [[ "${KPKN_CLOUD_EXECUTION:-}" != "1" ]]; then
  echo "Solo en Codex Cloud: exige KPKN_CLOUD_EXECUTION=1. No ejecutar en el equipo del usuario." >&2
  exit 2
fi
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) echo "Gradle local en Windows esta prohibido para este traslado." >&2; exit 2;; esac
if [[ $# -lt 1 || ! -d "$1/android-native/app" ]]; then
  echo "Uso: $0 <ruta-al-worktree-remoto> <argumentos de Gradle...>" >&2
  exit 2
fi
worktree=$(cd "$1" && pwd)
shift
case "$worktree" in /c/kw/*|/mnt/c/kw/*|/c/Users/*|/mnt/c/Users/*) echo "No se permite usar un checkout del equipo local." >&2; exit 2;; esac
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
if [[ $# -lt 1 ]]; then echo "Falta la tarea Android y sus clases concretas --tests." >&2; exit 2; fi
export KPKN_RENDER_DIR="${KPKN_RENDER_DIR:-/tmp/kpkn-mfp-cloud/render}"
mkdir -p "$KPKN_RENDER_DIR"
cd "$worktree/android-native"
exec bash ./gradlew --no-daemon --console=plain --max-workers=2 "$@"
