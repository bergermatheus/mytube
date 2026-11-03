# MyTube 0.16.0 readable YouTube failures

Executed 2026-11-02. Application ID `app.mytube.tv`, version code 16; database schema remains 5.

## Root cause and change

The optimized app displayed `YouTube couldn't load this content (b). Try again; an extractor update may be needed.` The previous handler used exception class display names for both classification and text. R8 mapped NewPipe’s `ContentNotAvailableException` to a class whose simple name was `b`, hiding useful meaning. The generic fallback also suggested extractor compatibility even when the server explicitly rejected a request.

`YouTubeErrors.kt` now owns typed error presentation for verification, country restrictions, an explicit VPN/proxy rejection, age verification, private/paid content, networking and parsing failures. Wrapped causes can retain their restriction message. Internal class names are not displayed. Actual parsing failures retain the compatibility guidance. `ContentNotAvailableException` is handled before its `ParsingException` superclass.

## Executed checks

- `./scripts/build.sh`: passed in 67 seconds, including debug/release lint and assembly. Unit report: 40 discovered, 36 passed, four opt-in network tests skipped. Three new unit tests cover VPN versus parsing, wrapped restrictions and typed verification, and generic unavailable content without an update claim.
- `assembleDebugAndroidTest` passed; final `lintDebug assembleDebugAndroidTest` also passed after the optimized UI regression was added.
- Exact CazéTV metadata probe on Chromecast: `cWEmbuUTIBI` failed with `ContentNotAvailableException` and upstream `Got error UNPLAYABLE: "Unavailable with a VPN or proxy"`. This is an observed playback limitation, not a passing network playback test.
- Public control metadata probe on the same connection: `jNQXAC9IVRw` passed and returned playable stream metadata for “Me at the zoo”. This demonstrates that extraction is not uniformly broken on that connection; it does not establish why the official client is accepted while this request is rejected.
- Both APK updates used `install -r`. The optimized 0.16.0 APK was restored after debug diagnostics. No uninstall, data clear, subscription import or VPN setting change was performed.

The optimized APK was verified through its actual channel grid: focused the Felipe Okano replay, selected it with the remote, and read the resulting accessibility tree. It displayed “YouTube rejected this VPN or proxy connection…” with Retry/Back actions, without `(b)` or an extractor-update claim. Package manager reports version code 16 / version 0.16.0, and APK signature verification passed.

A preliminary attempt to run the debug instrumentation runner against the optimized APK crashed in the runner because R8 had removed `kotlin.jvm.internal.Intrinsics`. That attempt does not establish an app runtime crash; ordinary optimized launch and the subsequent shell-driven UI verification succeeded. The unsupported temporary optimized-test class was removed, and production keep rules/exported activities were not broadened for testing. Raw build, metadata and UI logs remain ignored under `artifacts/validation/0.16/`.

This release corrects error reporting. It does not remove upstream VPN restrictions or establish successful CazéTV playback. Another Brazilian VPN endpoint may be accepted, but that remains unverified. The metadata probes do not establish decoded playback for the control video.
