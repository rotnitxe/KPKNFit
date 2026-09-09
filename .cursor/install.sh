#!/usr/bin/env bash
set -euo pipefail

# Idempotent Cloud Agent bootstrap for KPKN Fit (Linux VM).
# The dashboard install command is: bash .cursor/install.sh
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

JAVA_CANDIDATE="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
if [[ ! -x "$JAVA_CANDIDATE/bin/java" ]]; then
  JAVA_CANDIDATE="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
fi
if [[ ! -x "$JAVA_CANDIDATE/bin/java" ]]; then
  echo "error: JDK not found (looked at JAVA_HOME and PATH)" >&2
  exit 1
fi
export JAVA_HOME="$JAVA_CANDIDATE"
export PATH="$JAVA_HOME/bin:$PATH"

if [[ -f "$ROOT/android-native/gradlew" ]]; then
  chmod +x "$ROOT/android-native/gradlew"
fi

PROFILE_SNIPPET="$HOME/.config/kpkn-cloud-java.sh"
mkdir -p "$(dirname "$PROFILE_SNIPPET")"
cat > "$PROFILE_SNIPPET" <<EOF
export JAVA_HOME="$JAVA_HOME"
export PATH="\$JAVA_HOME/bin:\$PATH"
EOF

if [[ -f "$HOME/.bashrc" ]] && ! grep -q 'kpkn-cloud-java.sh' "$HOME/.bashrc"; then
  printf '\n# KPKN Cloud Agent JDK\n[ -f %s ] && . %s\n' "$PROFILE_SNIPPET" "$PROFILE_SNIPPET" >> "$HOME/.bashrc"
fi

echo "KPKN Cloud install: java=$("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"
echo "KPKN Cloud install: ANDROID_HOME=${ANDROID_HOME:-unset}"
echo "KPKN Cloud install: complete"
