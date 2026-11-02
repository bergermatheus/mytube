# Development, Android CLI and Chromecast installation

Run project commands from the checkout root. Commands use a POSIX shell unless marked PowerShell. MyTube is a native Android app, not a web application: there is no development HTTP server, npm setup, backend service or required Google API key.

## 1. Install Java and the Android tools

Use JDK 17 or newer; this project has been developed with JDK 21. Check `java -version` and `./gradlew --version`. On macOS an installed JDK can be selected with `export JAVA_HOME="$(/usr/libexec/java_home -v 21)"`. For an existing Homebrew OpenJDK 21 installation, its Java home may instead be `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`. On Linux/Windows select your installed JDK's home. Installing Android CLI does not replace this project's Java requirement.

### Android CLI

Google's current [Android CLI download and documentation](https://developer.android.com/tools/agents) is the entry point for your host OS/architecture. Example for **Apple Silicon macOS only**, matching the development machine used for this project:

```sh
curl -fsSL https://dl.google.com/android/cli/latest/darwin_arm64/install.sh \
  -o /tmp/mytube-android-cli-install.sh
bash /tmp/mytube-android-cli-install.sh
export PATH="$HOME/.local/bin:$PATH"
android --version
android --help
```

Use Google's corresponding installer for Intel macOS, Linux or Windows. Do not run the ARM macOS installer on another platform. The command reference below was checked with local Android CLI `1.1.16717390`; consult `--help` on your installation when it differs. Optional `--no-metrics` can precede commands to disable Android CLI's own metrics; this is separate from MyTube, which has no analytics SDK. `android init` is optional environment/agent setup, not a MyTube build prerequisite. No Android CLI Google authentication is needed for the local workflows below.

### SDK location and PATH

Use the actual installed SDK path; the following are common defaults:

```sh
# macOS
export ANDROID_HOME="$HOME/Library/Android/sdk"
# Linux: use this instead of the macOS line if it is your SDK location.
# export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$HOME/.local/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

On Windows PowerShell, a common SDK path is:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path = "$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\emulator;$env:Path"
```

Persist your chosen exports in your shell/profile once after verifying them. Keep `local.properties` out of Git; an existing `sdk.dir` there must agree with your intended SDK. An SDK installed elsewhere is fine: point `ANDROID_HOME` and `android --sdk` to that same directory.

Install only the packages required by this project:

```sh
android --sdk "$ANDROID_HOME" sdk install platform-tools
android --sdk "$ANDROID_HOME" sdk install platforms/android-36
android --sdk "$ANDROID_HOME" sdk install build-tools/36.0.0
android --sdk "$ANDROID_HOME" sdk list
adb version
```

Accept SDK license prompts interactively when installing. `android` is the SDK/developer workflow CLI; **ADB (Android Debug Bridge)** is a separate program in `platform-tools` that communicates with devices. Successfully installing `android` alone does not put `adb` on PATH. If needed, verify it directly with `"$ANDROID_HOME/platform-tools/adb" version`.

The [Android CLI SDK reference](https://developer.android.com/tools/agents/android-cli/commands/sdk) documents slash-separated package paths. If you already manage the SDK through Android Studio, use SDK Manager to install the same API/platform-tools/build-tools; a second SDK installation is unnecessary.

## 2. Run on a local TV emulator

Android Studio's **Device Manager → Create virtual device → TV** is the simplest way to choose a TV profile and an available image for your host. Prefer an ARM64 image on Apple Silicon and an x86_64 image on an Intel/AMD host when available. Do not substitute a phone profile for TV focus testing. Runtime API may differ from compile API 36; the app supports API 24+.

Android CLI also exposes emulator management:

```sh
android --sdk "$ANDROID_HOME" sdk install emulator
android emulator create --list-profiles
# Choose an actual TV profile returned by the preceding command.
android emulator create YOUR_TV_PROFILE
android emulator list
android emulator start YOUR_AVD_NAME
adb devices -l
```

Replace both uppercase values; profile IDs, image availability and AVD names depend on the installed tools. If the CLI does not offer a TV profile/image for your host, create one in Android Studio rather than guessing an image identifier. See [Android TV setup](https://developer.android.com/training/tv/get-started/create). No TV emulator was created as part of writing this guide; the physical Chromecast is the recorded playback target.

Build, select the emulator's actual serial and launch:

```sh
./gradlew --no-daemon --console=plain assembleDebug
export MYTUBE_DEVICE="emulator-5554"  # Use the serial from adb devices -l.
adb -s "$MYTUBE_DEVICE" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$MYTUBE_DEVICE" shell am start -W -n app.mytube.tv/.MainActivity
```

Windows uses `gradlew.bat` instead of `./gradlew`; set `$env:MYTUBE_DEVICE` and use it in the equivalent ADB commands. Android Studio can also open this folder, sync Gradle, select the `app` configuration and run it on the TV device.

## 3. Connect a Chromecast or Google TV

This requires **Chromecast with Google TV or another installable Android TV/Google TV device**. Legacy cast-only Chromecasts cannot install this app. Use a device you own or have permission to develop on.

### Pair with Wireless debugging

1. Put the computer and TV on a network that permits communication between clients. A shared SSID alone does not rule out guest/AP isolation or a firewall.
2. On the TV, open Settings → System → About and select the Android TV OS build repeatedly to enable Developer options. Menu names vary by TV version.
3. In Developer options, enable Wireless debugging when available. Choose **Pair device with pairing code** and leave that dialog open.
4. Pair with the IP and temporary pairing port displayed in that dialog:

```sh
adb pair TV_IP:PAIRING_PORT
# Enter the current six-digit code at the prompt, preserving leading zeroes.
```

Return to the main Wireless debugging page and read its **IP address and connection port**. This is a different endpoint:

```sh
adb connect TV_IP:CONNECTION_PORT
adb devices -l
```

Modern ADB can reconnect a paired TV automatically through mDNS. Discovery can be inspected with `adb mdns services`. Select the exact connected serial, which may be an IP:port or an `adb-..._adb-tls-connect._tcp` name:

```sh
export MYTUBE_DEVICE="EXACT_SERIAL_FROM_ADB_DEVICES"
adb -s "$MYTUBE_DEVICE" get-state
adb -s "$MYTUBE_DEVICE" shell getprop ro.product.model
adb -s "$MYTUBE_DEVICE" shell getprop ro.build.version.release
```

Use current values; pairing dialogs expire and connection ports can change. Do not put real pairing codes, fixed personal network addresses or a contributor's device serial in shared documentation. Wireless-debugging availability depends on Android/TV firmware; see [Google's ADB guide](https://developer.android.com/tools/adb#connect-to-a-device-over-wi-fi) and the [AOSP pairing protocol overview](https://android.googlesource.com/platform/packages/modules/adb/+/HEAD/docs/dev/adb_wifi.md).

### Devices without the pairing UI

Use an authorized USB debugging connection if supported. If an older device permits TCP debugging after a USB connection, Google's documented workflow is:

```sh
adb -s "$MYTUBE_USB_DEVICE" tcpip 5555
adb connect TV_IP:5555
adb devices -l
```

Set `MYTUBE_USB_DEVICE` to the authorized USB serial first. Do not assume every TV exposes a usable USB data connection or already listens on 5555; do not guess that port on a TV using dynamic wireless debugging. See [ADB over Wi-Fi after USB](https://developer.android.com/tools/adb#connect-wirelessly-with-a-device-after-an-initial-usb-connection-only-option-available-on-android-10-and-lower).

### Install and start

```sh
./gradlew --no-daemon --console=plain assembleDebug
adb -s "$MYTUBE_DEVICE" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$MYTUBE_DEVICE" shell am start -W -n app.mytube.tv/.MainActivity
```

The `-r` update keeps existing app data when package/signing identity is compatible. Launch MyTube again after installing to use the new code; an old running activity is not a reliable version check. Verify with:

```sh
adb -s "$MYTUBE_DEVICE" shell dumpsys package app.mytube.tv
```

Inspect `versionName`/`versionCode`. The current build configuration is 0.13.0/code 13; `app/build.gradle.kts` is authoritative after a version bump. The [Android CLI run command](https://developer.android.com/tools/agents/android-cli/commands/run) offers an alternative install/launch operation; this app has one default launcher activity:

```sh
android run --device "$MYTUBE_DEVICE" --apks app/build/outputs/apk/debug/app-debug.apk
```

### Connection/install troubleshooting

| Symptom | Check |
| --- | --- |
| `adb: command not found` | Install platform-tools, add its directory to PATH, or use its absolute path. |
| `protocol fault` during pairing | Keep the pairing dialog open, use its current pairing port/code and retry with fresh values. |
| Connection timed out | Confirm current IP/port, network reachability, AP/client isolation, firewall and routing/VPN effects. Same Wi-Fi name is insufficient. |
| Pairing succeeds but no connected device | Use the main page's connection port with `adb connect`; inspect `adb mdns services`. |
| `unauthorized` / `offline` | Inspect the TV's authorization/debugging prompts, wake it and reconnect using its current endpoint. |
| More than one device | Use `adb -s "$MYTUBE_DEVICE"` everywhere; do not pick a device by list order. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Existing app and new APK use different signing keys. Use the matching key/build; do not uninstall and lose the library as an automatic workaround. |
| Version downgrade rejected | Confirm the intended artifact/version. Do not automatically force a downgrade. |
| Video extraction/playback fails | Keep the reported error, video URL and app/extractor version. Check network/upstream availability; installing an APK successfully does not prove every stream works. |

Restarting the host ADB server affects all connected development sessions; use it only when necessary. No factory reset, root access, account login or installation of an official YouTube APK is required.

## 4. Build optimized artifacts

```sh
./scripts/build.sh
```

This runs local unit tests, debug/release lint and debug/release assembly. It copies `artifacts/mytube-tv-0.13.0.apk`, `artifacts/mytube-tv-0.13.0-debug.apk` and `artifacts/SHA256SUMS`. The POSIX script expects `shasum`; use the underlying Gradle tasks if your host does not provide that utility. `artifacts/` and build directories are ignored.

```sh
adb -s "$MYTUBE_DEVICE" install -r artifacts/mytube-tv-0.13.0.apk
adb -s "$MYTUBE_DEVICE" shell am start -W -n app.mytube.tv/.MainActivity
```

Release enables R8/resource shrinking but currently uses the local debug signing configuration. The development keystore is private and machine-specific; another contributor normally has a different key. A stable private release-signing/distribution process is future work, not something to solve by committing a debug keystore. This source tree includes no guaranteed prebuilt public download.

Optional developer-owned preference import: `./scripts/apply-preferences.sh /path/to/preferences.json`. It adds supplied channels/topics through an explicit instrumentation operation, builds/installs test packages, then returns to the optimized APK. The personal JSON is ignored and is not required for startup. It is not a watch-history backup.

## 5. Verify changes

Start with checks relevant to the change:

```sh
./gradlew --no-daemon --console=plain testDebugUnitTest lintDebug
# Opt-in real extraction/network tests:
./gradlew --no-daemon --console=plain testDebugUnitTest -PliveTests=true
```

For explicit hardware tests, build and install in place. **Do not use `connectedDebugAndroidTest` on a personal TV**: Gradle's managed installation/cleanup may uninstall the target and its data.

```sh
./gradlew --no-daemon --console=plain assembleDebug assembleDebugAndroidTest
adb -s "$MYTUBE_DEVICE" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$MYTUBE_DEVICE" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$MYTUBE_DEVICE" shell am instrument -w \
  -e class app.mytube.tv.LibraryStoreTest \
  app.mytube.tv.test/androidx.test.runner.AndroidJUnitRunner
```

Choose UI/playback checks explicitly after authorization to interact with the TV:

```sh
adb -s "$MYTUBE_DEVICE" shell input keyevent KEYCODE_WAKEUP
adb -s "$MYTUBE_DEVICE" shell am instrument -w \
  -e class app.mytube.tv.RetainedPagesTest,app.mytube.tv.TvRedesignTest,app.mytube.tv.ChapterPlayerTest \
  -e uiTests true -e livePlayback true \
  app.mytube.tv.test/androidx.test.runner.AndroidJUnitRunner
```

Inspect the actual test source for available flags (`uiTests`, `livePlayback`, `liveTests`, `profileNavigation`, `captureDocs`, `applyPreferences`); they are not interchangeable. UI tests change the activity stack and real playback updates public-fixture history/progress. Preference import intentionally changes saved choices. They should not run while the owner is watching something else. Keep device selection explicit, check the foreground package before key sequences and report what actually ran. Restore the optimized APK for ordinary use after debug instrumentation.

Validation records are historical evidence in `docs/VALIDATION*.md`. Debug instrumentation overhead is not a release frame-rate benchmark; report equivalent workloads and measured/unmeasured costs separately.

## 6. Inspect logs and capture real previews

```sh
mkdir -p artifacts/diagnostics artifacts/screenshots
adb -s "$MYTUBE_DEVICE" shell pidof app.mytube.tv
# After obtaining that PID, use it in the command below.
adb -s "$MYTUBE_DEVICE" logcat --pid=MYTUBE_PID -d > artifacts/diagnostics/mytube-logcat.txt
adb -s "$MYTUBE_DEVICE" exec-out screencap -p > artifacts/screenshots/mytube.png
adb -s "$MYTUBE_DEVICE" shell uiautomator dump /sdcard/mytube-ui.xml
adb -s "$MYTUBE_DEVICE" exec-out cat /sdcard/mytube-ui.xml > artifacts/diagnostics/mytube-ui.xml
```

Android CLI also provides:

```sh
android screen capture --device "$MYTUBE_DEVICE" --output artifacts/screenshots/mytube-cli.png
android layout --help
```

Use a binary-safe shell for PNG redirection. On Windows, Android CLI's `--output` avoids shell text-encoding issues. Raw device captures/logs stay ignored; copy only reviewed public previews into `docs/images/` and update its [provenance notes](images/README.md). Native video surfaces may be absent/black in screenshots even when playback works. Do not fill them with a fabricated scene and present it as a real capture. For player previews, the [opt-in documentation capture helper](images/README.md#how-the-player-frames-were-captured) can render actual video into a capturable test-only surface.
