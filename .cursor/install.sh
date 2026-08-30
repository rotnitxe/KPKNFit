#!/usr/bin/env bash
# Cloud Agent install script for KPKN Fit.
# Idempotent bootstrap for the primary Android app (android-native/) and the
# optional FastAPI analysis backend (backend/). Safe to run repeatedly.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
CMDLINE_TOOLS_VERSION="11076708"
ANDROID_PLATFORM="platforms;android-36"
ANDROID_BUILD_TOOLS="build-tools;36.0.0"

log() { printf '\n\033[1;36m[install]\033[0m %s\n' "$*"; }

ensure_apt_pkg() {
  # Install a Debian package only when it is missing. Uses sudo when available.
  local pkg="$1"
  if dpkg -s "$pkg" >/dev/null 2>&1; then
    return 0
  fi
  log "Installing system package: $pkg"
  if command -v sudo >/dev/null 2>&1; then
    sudo apt-get update -qq
    sudo apt-get install -y -qq "$pkg"
  else
    apt-get update -qq
    apt-get install -y -qq "$pkg"
  fi
}

# ---------------------------------------------------------------------------
# 1. System prerequisites (JDK 21 and Python 3.12 ship with the base image).
# ---------------------------------------------------------------------------
ensure_apt_pkg curl
ensure_apt_pkg unzip
ensure_apt_pkg python3.12-venv

# ---------------------------------------------------------------------------
# 2. Android SDK (command-line tools, platform, build-tools, platform-tools).
# ---------------------------------------------------------------------------
SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  log "Downloading Android command-line tools ($CMDLINE_TOOLS_VERSION)"
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
  tmp_zip="$(mktemp --suffix=.zip)"
  curl -fsSL -o "$tmp_zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
  rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/tmp-extract"
  unzip -q "$tmp_zip" -d "$ANDROID_SDK_ROOT/cmdline-tools/tmp-extract"
  rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  mv "$ANDROID_SDK_ROOT/cmdline-tools/tmp-extract/cmdline-tools" \
     "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/tmp-extract" "$tmp_zip"
fi

log "Accepting Android SDK licenses"
yes | "$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null 2>&1 || true

log "Installing Android SDK packages (platform-tools, $ANDROID_PLATFORM, $ANDROID_BUILD_TOOLS)"
"$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" \
  "platform-tools" "$ANDROID_PLATFORM" "$ANDROID_BUILD_TOOLS" >/dev/null

# Point Gradle at the SDK (machine-specific, git-ignored).
echo "sdk.dir=$ANDROID_SDK_ROOT" > "$REPO_ROOT/android-native/local.properties"
log "Wrote android-native/local.properties -> sdk.dir=$ANDROID_SDK_ROOT"

# ---------------------------------------------------------------------------
# 3. Debug signing keystore.
# The debug build reuses the "release" signing config; its credentials are
# already committed in app/build.gradle.kts, so this keystore holds no secret.
# Generate it locally when absent (it is git-ignored).
# ---------------------------------------------------------------------------
KEYSTORE="$REPO_ROOT/android-native/app/kpkn-release.keystore"
if [ ! -f "$KEYSTORE" ]; then
  log "Generating local debug/release keystore"
  keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -storepass kpkn2024 -keypass kpkn2024 -alias kpkn \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=KPKN Dev, OU=Dev, O=KPKN, L=NA, S=NA, C=NA" >/dev/null 2>&1
fi

# ---------------------------------------------------------------------------
# 4. Warm the Gradle build (downloads the wrapper + dependencies and validates
#    the Android toolchain by assembling the base debug APK).
# ---------------------------------------------------------------------------
log "Warming Gradle build (assembleBaseDebug)"
(
  cd "$REPO_ROOT/android-native"
  ANDROID_HOME="$ANDROID_SDK_ROOT" ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT" \
    ./gradlew --no-daemon --console=plain :app:assembleBaseDebug
)

# ---------------------------------------------------------------------------
# 5. Optional FastAPI backend Python environment.
# ---------------------------------------------------------------------------
log "Setting up backend Python virtualenv"
(
  cd "$REPO_ROOT/backend"
  if [ ! -x ".venv/bin/python" ]; then
    python3 -m venv .venv
  fi
  . .venv/bin/activate
  python -m pip install --quiet --upgrade pip
  python -m pip install --quiet -r requirements.txt
)

log "Install complete."
