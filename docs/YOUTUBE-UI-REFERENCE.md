# Original YouTube TV APK UI reference for MyTube 0.5

Inspected 2026-11-01. This is a bounded static reference investigation, not a runtime reproduction of every YouTube screen.

## Target and provenance

- Original caller-selected base APK: `youtube-5.30.340/base.apk` (relative to the repository root).
- Actual SHA-256: `b1d9a3db9929be5c659821bdd8c360c89b3a766fa9426db1a2b55266f80e6129`. It exactly matches the saved REA package Evidence `ev_da108a8a2629f9543eb862cd926113430cf09570f41f668af80bb7eb9ff8c79d` in `youtube-5.30.340/rea-android-package.json`.
- Observed package/version: `com.google.android.youtube.tv`, `5.30.340`, version code `530340320`, Android min SDK 24/target 34.
- Existing ABI companion: `split_config.armeabi_v7a.apk`, SHA-256 `3b2a127c64c754535da6bc48f74dede0d96cb3200abc0549f4299e4612112042`.
- Current REA CLI: 6.7.0. Focused class/method queries use the existing JADX 0.7.1 headless provider and existing JDK 21; nothing was installed or reconfigured. The first sandboxed query could not compile REA's macOS ownership helper into the user Clang cache; the authorized escalated query succeeded.

## Observations

1. **The declared main UI is a Cobalt web application.** The saved REA manifest names `com.google.android.apps.youtube.tv.activity.MainActivity` and `StandalonePlayerActivity`, both with `cobalt.APP_URL=https://www.youtube.com/tv` and `android.app.lib_name=cobalt`. Both use `CobaltTheme`. The standalone player declares a separate Android process. Evidence: `ev_da108a8a2629f9543eb862cd926113430cf09570f41f668af80bb7eb9ff8c79d`, decoded AndroidManifest application activities.
2. **The activity constructs a Cobalt bridge.** REA class inventory `ev_7ed9a0dcea06f77ee9c1beabbffef296e8dec9bd59011f097be5d01ad61e4e19` exposes `v(String[], String): dev.cobalt.coat.StarboardBridge`. Focused method Evidence `ev_a1660b3cc3bbe79beeb99f43083aa88590f61d3aca314629d0158654c9140173` decompiles that override of `dev.cobalt.coat.CobaltActivity`: it constructs a `StarboardBridge` and registers bridge components. This is observed decompiled structure, not execution.
3. **Packaged entry points include Android search/deep links.** The manifest's exported `ShellActivity` handles `VIEW`, `MEDIA_PLAY_FROM_SEARCH`, ordinary YouTube hosts, `vnd.youtube` / `vnd.youtube.launch`, and Google TV cast launch actions. This establishes declared entry points; it does not establish precise search, focus, skip or settings behavior. Evidence: package manifest above.
4. **The ordinary ZIP-member view contains only splash HTML in its named web assets.** Base APK's HTML/JS/CSS/SVG suffix selection found `assets/web/youtube_splash_screen.html` and `assets/web/music_splash_screen.html`; it found no `.js` or `.css` members and no `res/layout*` paths. The YouTube splash contains startup lozenge/wordmark/spinner elements, not the requested main grid/settings pages. The ABI split includes `lib/armeabi-v7a/libcobalt.so` (36,874,168 bytes). These are ordinary Python ZIP observations, **not new REA Evidence**. Exact selected member hashes are in `/private/tmp/mytube-youtube-ui-archive-observations.json`.

## Inference and implementation consequence

The manifest's remote page URL plus the actual Cobalt bridge strongly support treating this APK as a native/Cobalt host for the remotely supplied TV application. It is not a conventional package of recoverable Android XML layouts for every main-page/player/settings state. The APK is useful for application entry points, platform integration, startup assets and the engine boundary; the user's photographs and authorized current TV observations are better evidence for exact visible spacing, focus and settings rows.

For the active redesign, preserve the observed/photo-supported behaviors in independent MyTube source: microphone/search header; direct D-pad navigation; rounded grey settings rows with an off-white focused row and current-value subtitle; a centered transport group using bar-plus-triangle skip glyphs; Back-driven modal navigation. The photographs establish these visual requirements. Their geometry has **not** been recovered from this Java code. No official executable code, native library, HTML startup document, certificates or resources need be embedded in MyTube to implement them.

## Remaining unknowns / bounds

- Static inspection does not establish the current server page's DOM/CSS, exact pixel geometry, grid-shuffle/recommendation algorithm, every remote-key transition, or feature availability without login.
- No ordinary `.js` ZIP member is a bounded negative observation; it does not rule out code embedded/compressed in a native binary or a runtime cache. No deep native analysis was run.
- No original YouTube app was launched, changed or driven by this investigation; no traffic was intercepted and no account/sign-in was accessed.
- This evidence covers version 5.30.340 and the photographed states. It cannot prove a different APK version or a future remote UI is identical.

## Retained focused outputs

- `/private/tmp/mytube-youtube-main-class.json`
- `/private/tmp/mytube-youtube-main-bridge-method.json`
- `/private/tmp/mytube-youtube-ui-archive-observations.json`

Retained project copies of the three focused outputs are in `artifacts/rea-ui-reference/`, using the same basenames. The original package Evidence remains in `youtube-5.30.340/rea-android-package.json`.
