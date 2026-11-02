# MyTube 0.6.0 validation

Executed 2026-11-02 on the already paired Chromecast (`sabrina`, Android 14). APKs use the existing local Android development signing key, with R8 and resource shrinking for the normal release APK. This is a personal testing build.

## Reference and implementation

The original YouTube APK search callback forwards into Cobalt's native bridge. The packaged manifest declares the remotely loaded TV application. The keyboard, history storage, recommendation algorithm and exact layout were not recovered from Android source. The user's `search-bar.jpeg` and `search-feature.jpeg` establish the visible requirements. Exact identity, REA Evidence IDs and bounded static findings are in [YOUTUBE-SEARCH-REFERENCE.md](YOUTUBE-SEARCH-REFERENCE.md); complete focused records are in `artifacts/rea-search-reference/`.

Implemented independently in MyTube:

- Compact, fully rounded Home search pill beside the circular microphone; MyTube branding remains at the right.
- Separate search route with no sidebar, a Back arrow, embedded D-pad keyboard, local recent searches/live autocomplete at the left, and a video row below. Search uses no system IME. The keyboard includes numbers, accents, delete, clear and space; hardware typing and the system speech-recognition result contract share the same search flow.
- Home's existing recommendations appear before submission. Search results replace the lower row, focus the first video and append in batches of ten. Search continuation belongs to its search session; Home requests cannot overwrite it. Original publisher titles use the existing resolver.
- Back returns to the previous browse screen and its scroll position. Saving the submitted phrase as an interest is retained.
- Additive SQLite schema 3→4 migration adds only `search_history`; up to 20 unique submitted phrases survive restart, while direct video URLs are omitted. Settings can clear search history independently of watch history and preferences.

## Executed checks

`./scripts/build.sh` passed `testDebugUnitTest`, `lintDebug`, `lintRelease`, `assembleDebug` and `assembleRelease`. `assembleDebugAndroidTest` also passed. Fifteen local unit tests passed, including Unicode-aware deletion; three opt-in live/research tests were skipped. Exact unit summaries are in `artifacts/unit-test-summary.json`.

Five visible Chromecast instrumentation checks passed (`search-0.6-device-test.log`):

- Remote letters/delete/numbers/space/clear, hardware typing, live autocomplete, search submission and first-result focus.
- Stable append of more results, retaining the first result; Up returns to the keyboard.
- Physical Back and the on-screen arrow restore Home's measured scroll position and header focus; reopening displays the saved phrase.
- Isolated schema 3 upgrade, durable/deduplicated/bounded search history, omitted direct URLs and preserved subscriptions/interests.
- Existing sidebar layout/Right navigation, system voice-result contract and suggestion selection.

The independent `LibraryStoreTest` suite passed all four checks, including historical schema 1 migration and saved watch progress/preferences (`library-0.6-device-test.log`). The voice check intercepts the system recognition contract and provides a result; it does not record real speech.

Copied-library snapshots before/after instrumentation have identical subscription/interest digests: 12 channels and one interest. The schema migration preserves their values. Evidence: `artifacts/performance/before-0.6-device-checks-preferences.json` and `after-0.6-device-checks-preferences.json`.

The optimized APK's v2 signature verifies with the existing certificate (`artifacts/signature-verification.txt`). APK identities and sizes are in `artifacts/build-info.json` and `SHA256SUMS`.

The final optimized APK also passed `python3 scripts/check-search-tv.py`: compact Home pill, D-pad entry, hidden sidebar, Back arrow, embedded keyboard, hardware URL typing, first-result focus, immediate playback, actual MediaSession PLAYING state, return to Search and then Home. The successful run is retained in `optimized-search-0.6.log` and `artifacts/search-0.6-optimized-checks.json`; screenshots are in `artifacts/screenshots/0.6-optimized/`. The first exploratory harness used ADB touch taps, which did not enter the TV search route; the final harness uses real remote keys. No production code changed for that harness correction. The installed package reports version 0.6.0/code 6, and the walkthrough left it on Home.

## Coverage bounds

One Chromecast and bounded public YouTube queries were used. The embedded keyboard is optimized for TV; phone/touch layouts and arbitrary physical keyboard layouts were not validated. Recent searches are device-local and independent of Google's signed-in history. Initial recommendations reuse MyTube's loaded browse candidates; they do not reproduce Google's private ranking. Network/extractor failures can still prevent autocomplete or playback. No new frame-time, CPU or memory benchmark was run, and no claim of measured performance improvement is made. Other player quality/subtitle/comment cases retain the 0.5 record; they were not all rerun for this search-only update.
