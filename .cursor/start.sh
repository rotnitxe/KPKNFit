#!/usr/bin/env bash
set -euo pipefail

# Per-boot Cloud Agent start. Restore toolchain env; do not launch Gradle
# or the emulator here (those belong in agent tasks, not VM boot).
if [[ -f "$HOME/.config/kpkn-cloud-env.sh" ]]; then
  # shellcheck disable=SC1091
  . "$HOME/.config/kpkn-cloud-env.sh"
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -n "${ANDROID_HOME:-}" && -d "$ANDROID_HOME" && -d "$ROOT/android-native" ]]; then
  printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$ROOT/android-native/local.properties"
fi

echo "KPKN Cloud start: JAVA_HOME=${JAVA_HOME:-unset} ANDROID_HOME=${ANDROID_HOME:-unset}"
