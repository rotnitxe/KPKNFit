#!/usr/bin/env bash
set -euo pipefail

# Idempotent Cloud Agent bootstrap for KPKN Fit (Linux VM).
# Dashboard install command: bash .cursor/install.sh
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip"
CMDLINE_TOOLS_SHA256="4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
export ANDROID_SDK_ROOT
export ANDROID_HOME="$ANDROID_SDK_ROOT"

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

install_cmdline_tools() {
  local sdkmanager="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
  if [[ -x "$sdkmanager" ]]; then
    return 0
  fi
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
  local tmp zip
  tmp="$(mktemp -d)"
  zip="$tmp/cmdline-tools.zip"
  echo "KPKN Cloud install: downloading Android cmdline-tools"
  curl -fsSL "$CMDLINE_TOOLS_URL" -o "$zip"
  echo "$CMDLINE_TOOLS_SHA256  $zip" | sha256sum -c -
  unzip -q "$zip" -d "$tmp"
  rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  rm -rf "$tmp"
}

accept_licenses() {
  mkdir -p "$ANDROID_SDK_ROOT/licenses"
  printf '%s\n' '24333f8a63b6825ea9c5514f83c2829b004d1fee' > "$ANDROID_SDK_ROOT/licenses/android-sdk-license"
  printf '%s\n' '84831b9409646161da1c5280261abc11' > "$ANDROID_SDK_ROOT/licenses/android-sdk-preview-license"
}

install_sdk_packages() {
  local sdkmanager="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
  accept_licenses
  echo "KPKN Cloud install: ensuring Android SDK packages"
  "$sdkmanager" --sdk_root="$ANDROID_SDK_ROOT" --install \
    "platform-tools" \
    "platforms;android-36" \
    "build-tools;36.0.0"
}

write_local_properties() {
  local props="$ROOT/android-native/local.properties"
  mkdir -p "$(dirname "$props")"
  cat > "$props" <<EOF
sdk.dir=$ANDROID_SDK_ROOT
EOF
}

write_profile() {
  local snippet="$HOME/.config/kpkn-cloud-env.sh"
  mkdir -p "$(dirname "$snippet")"
  cat > "$snippet" <<EOF
export JAVA_HOME="$JAVA_HOME"
export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="\$JAVA_HOME/bin:\$ANDROID_SDK_ROOT/platform-tools:\$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:\$PATH"
EOF
  if [[ -f "$HOME/.bashrc" ]] && ! grep -q 'kpkn-cloud-env.sh' "$HOME/.bashrc"; then
    printf '\n# KPKN Cloud Agent toolchain\n[ -f %s ] && . %s\n' "$snippet" "$snippet" >> "$HOME/.bashrc"
  fi
  # Drop the older JDK-only snippet if present.
  if [[ -f "$HOME/.bashrc" ]]; then
    sed -i '/kpkn-cloud-java.sh/d' "$HOME/.bashrc" || true
  fi
}

maybe_add_kvm_group() {
  if [[ -e /dev/kvm ]] && command -v sudo >/dev/null && sudo -n true 2>/dev/null; then
    sudo -n usermod -aG kvm "$USER" 2>/dev/null || true
  fi
}

install_cmdline_tools
install_sdk_packages
write_local_properties
write_profile
maybe_add_kvm_group
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"

echo "KPKN Cloud install: java=$("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"
echo "KPKN Cloud install: ANDROID_HOME=$ANDROID_HOME"
echo "KPKN Cloud install: adb=$(command -v adb || echo missing)"
echo "KPKN Cloud install: complete"
