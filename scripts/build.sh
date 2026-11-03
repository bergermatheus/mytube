#!/bin/sh
set -eu
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
cd "$PROJECT_DIR"
./gradlew --no-daemon --console=plain testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease
mkdir -p artifacts
cp app/build/outputs/apk/debug/app-debug.apk artifacts/mytube-tv-0.16.0-debug.apk
cp app/build/outputs/apk/release/app-release.apk artifacts/mytube-tv-0.16.0.apk
shasum -a 256 artifacts/mytube-tv-0.16.0.apk artifacts/mytube-tv-0.16.0-debug.apk > artifacts/SHA256SUMS
echo "APK: $PROJECT_DIR/artifacts/mytube-tv-0.16.0.apk (optimized, local development signing key)"
