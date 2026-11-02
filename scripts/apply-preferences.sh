#!/bin/sh
set -eu
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$PROJECT_DIR"
PREFERENCES_FILE="${1:-preferences.json}"
test -f "$PREFERENCES_FILE"
MYTUBE_ADB="${MYTUBE_ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi
./scripts/build.sh
./gradlew --no-daemon --console=plain assembleDebugAndroidTest
"$MYTUBE_ADB" install -r artifacts/mytube-tv-0.13.0-debug.apk
"$MYTUBE_ADB" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
PREFERENCES_PAYLOAD=$(base64 < "$PREFERENCES_FILE" | tr -d '\n')
IMPORT_RESULT=$("$MYTUBE_ADB" shell am instrument -w -e class app.mytube.tv.PreferenceImportTest \
  -e applyPreferences true -e preferences "$PREFERENCES_PAYLOAD" \
  app.mytube.tv.test/androidx.test.runner.AndroidJUnitRunner)
printf '%s\n' "$IMPORT_RESULT"
case "$IMPORT_RESULT" in
    *"OK (1 test)"*) ;;
    *) exit 1 ;;
esac

# Leave the optimized build installed after the debug instrumentation import.
"$MYTUBE_ADB" install -r artifacts/mytube-tv-0.13.0.apk
