#!/usr/bin/env bash
# watch_and_deploy.sh
# Watches Swift files and auto-builds + re-launches the app on the simulator

export DEVELOPER_DIR=/Users/imacmantra/Downloads/Xcode.app/Contents/Developer

PROJ_DIR="/Users/imacmantra/Documents/KPKNFit/ios-native/KPKNFit"
SCHEME="KPKNFit"
BUNDLE_ID="com.example.kpkn.KPKNFit"
SIM_UDID="8BFCC9B0-7876-4290-94B2-E211D7960583"  # iPhone 15 Pro (Booted)

BUILD_DIR="$PROJ_DIR/DerivedData"

build_and_launch() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "🔨 Cambio detectado — compilando..."
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    xcodebuild \
        -scheme "$SCHEME" \
        -destination "id=$SIM_UDID" \
        -derivedDataPath "$BUILD_DIR" \
        -quiet \
        2>&1 | tail -5

    if [ ${PIPESTATUS[0]} -eq 0 ]; then
        APP_PATH=$(find "$BUILD_DIR" -name "KPKNFit.app" -path "*/Debug-iphonesimulator/*" | head -1)
        echo "✅ Build OK — instalando en simulador..."
        "$DEVELOPER_DIR/usr/bin/xcrun" simctl terminate "$SIM_UDID" "$BUNDLE_ID" 2>/dev/null || true
        "$DEVELOPER_DIR/usr/bin/xcrun" simctl install "$SIM_UDID" "$APP_PATH"
        "$DEVELOPER_DIR/usr/bin/xcrun" simctl launch "$SIM_UDID" "$BUNDLE_ID"
        echo "🚀 App relanzada en iPhone 15 Pro"
    else
        echo "❌ Build FAILED — revisa los errores arriba"
    fi
}

# Run once immediately on start
build_and_launch

# Watch for Swift file changes
echo ""
echo "👁  Watching for changes in $PROJ_DIR/KPKNFit..."
echo "    (Ctrl+C para detener)"
echo ""

while true; do
    # Use find + checksum to detect changes (no fswatch needed)
    CURRENT=$(find "$PROJ_DIR/KPKNFit" -name "*.swift" -newer "$PROJ_DIR/.last_build_stamp" 2>/dev/null | wc -l | tr -d ' ')
    if [ "$CURRENT" -gt 0 ]; then
        touch "$PROJ_DIR/.last_build_stamp"
        build_and_launch
    fi
    sleep 2
done
