# MyTube 0.4.0 — navigation, search suggestions and player controls

Executed 2026-11-01 on the paired Chromecast (sabrina), Android 14. All code and artifacts remain in `mytube`. Older records below are historical.

## Implementation

The previous sidebar animated its measured width inside the grid's parent Row. That repeatedly changed adaptive-column and thumbnail constraints. The new sidebar has a fixed rail footprint and draws its expanding labels over the content; only the menu observes its expansion state. Grid cards keep stable heights, decode thumbnails to a bounded size, and reuse their image requests. The first scroll reset waits for the first committed batch; later ten-video batches never reset the grid. The grid exposes its committed count as an accessibility state.

Initial/page loading, autocomplete, player extraction/buffering and comment fetching use indeterminate spinners. Existing Home tiles remain available during page requests. Autocomplete debounces 300 ms, permits one request at a time, conflates pending input, discards stale replies, and does not share the feed's network mutex. Selecting a suggestion submits the search and hides the keyboard.

The bottom dock contains a circular channel avatar and Subscribe on the left, previous/play-pause/next at the screen center, and local reactions/comments/Settings on the right. The Videos button is removed. Previous/next retain session navigation history. The channel avatar opens that channel's feed. The right settings drawer provides available quality caps, subtitle tracks/Off, and persistent 0.25×–2× speed. Changing quality/subtitles preserves position and pause state. The comments drawer shows the first page of real comments. Physical TV Back and system Back use the same registered navigation callback. Down/Up retain hidden → dock → related and the reverse sequence.

The primary APK is now R8-optimized with resource shrinking. The debug APK is retained for instrumentation. Both use the existing local Android development certificate, allowing `adb install -r` without replacing the database. This is a personal-testing signing setup. Source-derived Rhino dependencies/rules preserve extractor reflection and exclude the unused desktop `javax.script` adapter; see the upstream link in `app/proguard-rules.pro`.

## Executed checks

- `scripts/build.sh`: unit tests, debug/release lint, and both APK assemblies passed. Ten local unit tests passed; three opt-in live/research tests were skipped by the default build.
- Search autocomplete hardware flow passed: partial typing produced real suggestions; selecting one loaded video results.
- Live extractor hardware check passed: suggestions, twenty comments, two subtitle streams and a channel avatar were obtained for a public test video.
- Full player upgrade hardware flow passed: centered transport, right drawer bounds, actual speed changes, selected text track **and timed caption cues**, comments, previous/next playback, and avatar-to-channel navigation. See `player-upgrade-0.4-device-test.log`.
- Existing Down/Up player navigation and direct related-card playback passed; independent playback verification reported rendered decoder output buffers and persisted progress. See `player-navigation-0.4-device-test.log` and `decoded-frame-0.4-device-test.log`.
- Sidebar collapse/expand and channel picker navigation passed. Four isolated SQLite/migration tests passed. See `sidebar-0.4-final-device-test.log` and `library-0.4-device-test.log`.

- Final paging check passed: Down committed twenty videos, the first card remained unchanged after scrolling back, Select opened playback directly, and returning retained the twenty-video session.
- ADB-only paired navigation passed on the exact final optimized APK: frame-deadline misses were 43.95% in 0.3 debug versus 1.22% in 0.4 optimized; 95th-percentile frame time was 300 ms versus 40 ms. CPU, elapsed time, memory, exact key sequence and limits are in `artifacts/performance/README.md`.
- Read-only before/after snapshots retained identical identities for **eleven saved channels and one topic**. No database was cleared or uninstalled.

The final optimized APK also passed the complete unattended ADB walkthrough: searching a public URL opened playback, its MediaSession entered PLAYING, the bottom controls and right settings/comments drawers rendered, physical Back returned to the dock, and the avatar opened the actual channel feed. Screenshots are in `artifacts/screenshots/0.4-optimized/`; see `optimized-adb-playback-0.4.log` and `artifacts/optimized-playback-results.json`. The script waits for the channel feed and moves focus into content before checking its heading, because the expanded menu overlays that heading.

Final optimized-build navigation/playback and paging results are recorded in `artifacts/final-device-checks.json`; APK digests/signing information are in `artifacts/build-info.json`, `artifacts/SHA256SUMS` and `artifacts/signature-verification.txt`.

## Verification limits

Tests exercise a bounded set of public videos on one Chromecast. They do not establish every codec, quality, subtitle language, every IME, long-duration playback, audiovisual synchronization, or phone behavior. Subtitle verification above includes actual timed cues, rather than inventory alone. Comments currently expose the first response page. Likes/dislikes/subscriptions remain local preferences. Network or upstream service changes can affect extraction.

Earlier Compose-test navigation timings include substantial framework/debug overhead and are diagnostic only. Native-harness comparisons and their resource/coverage limits are recorded separately under `artifacts/performance/`. Early attempts to attach debug instrumentation to the minified APK failed in the test runner because its Kotlin dependencies had been removed/renamed; direct ADB checks avoid that mismatch. The incompatible source probes were removed. Failed exploratory checks are not counted as successful checks; useful diagnostics remain in the local logs. Some automation screenshots omit the native video surface; those captures establish UI layout, while the decoder-buffer check establishes rendered video output.

---

# MyTube 0.3.0 — TV redesign, stable paging and direct playback

Executed 2026-10-30 on the real paired Chromecast (sabrina), Android 14. Source and APK are in `mytube`; older validation below is historical.

## Executed results

- Final `scripts/build.sh`: passed build, lint (no errors), and **10 local unit tests**. Three opt-in live/research tests were skipped by the default build; they are not counted as executed here. See `artifacts/unit-test-summary.json`.
- Four `LibraryStoreTest` hardware tests passed. These include additive schema migration through version 3, preservation of existing library facts/preferences, publication timestamp/approximation retention across reopen and metadata updates, and local likes surviving history deletion.
- **HomePagingTest passed on hardware:** pressing Down near the end loaded a second ten-video batch; the first card remained unchanged after scrolling back. A single D-pad Select opened PlayerActivity directly, and returning to Home retained the same twenty-video session. See `home-paging-device-test.log`. Pure pager tests additionally check first-source sampling, immutable committed prefixes, per-page size, deduplication, eventual exhaustion, and partial failures.
- **Player navigation test passed on hardware:** initial controls hidden; Down shows the bottom dock; Down again shows related videos; Up returns to controls; Up again hides them. The grid has no Back-to-player button. Selecting a related card with D-pad Select changed the video and actual playback advanced. The test also verifies that the default centered Media3 controller is disabled and the custom dock occupies the bottom of the screen. See `player-navigation-device-test.log`.
- **Sidebar navigation test passed on hardware:** focus into content collapses the sidebar to icons, focus back expands it, and selecting Subscriptions opens the channel picker. See `sidebar-device-test.log`.
- **Additional native playback check passed:** real playback advanced beyond two seconds, the ExoPlayer video decoder reported rendered output buffers, and resume progress persisted after leaving playback. This is stronger than a clock-only playback check. See `decoded-frame-device-test.log`.
- Latest APK installed in place; saved preferences restored/retained: the nine requested canonical channel identities and Lucas Montano topic. The official YouTube package was not changed.
- APK signature verified using the local Android debug key. Exact APK bytes/hash are in `artifacts/build-info.json`, `artifacts/SHA256SUMS` and `artifacts/signature-verification.txt`.

## Resulting behavior

Home commits at most ten videos per batch. Initial loading samples three saved sources for variety (fetching further sources only if necessary to fill the batch). Later Down/More requests use buffered candidates and another deferred source, append at most ten videos, and never reorder existing batches. Duplicate or filtered candidates do not create an endless empty pager. Freshness/shuffling applies to each new batch; this is not a claim to have examined every upload from every channel. Returning from playback retains the active Home session; explicit Refresh creates a new session. Loading and partial errors stay below the existing Home grid.

Normal Select plays immediately; long-press retains video actions. All player controls are in a bottom dock. Likes/dislikes/subscriptions are local app preferences. The launcher asset is a white circle with a curved red play-button shape. Thumbnail publication metadata retains source uncertainty, and missing dates are shown as unavailable.

## Coverage limits and diagnostic history

Initial UI attempts ran with the Chromecast asleep; subsequent runs explicitly woke it and check this precondition. Early tests used touch-style activation and sampled padded bounds/transitional layouts; the final tests use real D-pad Select events and the dock container's outer bounds. The previous continuously reshuffled Home representation was replaced by a session-owned append-only pager; final paging/sidebar cases passed independently.

Screenshots are under `artifacts/screenshots/redesign-tests/`. Some automation captures show a black native-video region; those images establish UI layout only. The additional rendered-buffer test independently verifies that the video decoder produced rendered frames. No independent audiovisual-sync measurement, complete codec/quality matrix, long-duration soak, or physical-phone validation was performed. No cold/warm latency, CPU or peak-memory benchmark is claimed.

Pagination drains candidates from the initial response pages of the configured sources; it does not exhaustively crawl entire channel back catalogs. YouTube/network changes can still interrupt extraction. This is a personal-testing debug APK, not a production-signed/store-ready release.

---

# MyTube 0.2.0 — saved interests and personal channel preferences

Executed 2026-10-30. The 0.1.0 validation is retained below as historical evidence.

- Final `scripts/build.sh`: passed unit tests, lint (no errors) and APK assembly. Five local tests cover URL handling, recommendation ranking, short explicit interest keywords such as AI, and normalized interest identity. Network/research helpers skip unless explicitly opted in.
- Explicit live tests passed: search returned 20 results, the test video exposed 15 formats, and its actual uploader channel returned 30 video candidates. An initial guessed channel handle returned no videos; the fixture now derives the channel identity from the test video's producer metadata.
- Three `LibraryStoreTest` instrumentation tests passed on Chromecast Android 14. These cover the original storage rules, duplicate-safe topic/import persistence, saved examples surviving history deletion, explicit disinterest overriding a saved example, and migration from the exact 0.1.0 database schema without losing history, progress, hidden choices or subscriptions. Tests use separate named databases.
- Updated app installed in place with `adb install -r`. Gradle's connected-test task was not used for this update, to avoid uninstalling the existing app and its data.
- An explicit ADB instrumentation import saved the nine resolved requested channel identities plus the Lucas Montano search interest in the actual application database. Import assertions passed. Existing choices/history were not removed. See `preferences-applied.log`, `preferences.json` and `docs/preference-resolution-evidence.xml`. The import helper is not an exported component of the shipped application.
- Latest APK signature verified (local Android debug key); checksum and build version are in `artifacts/build-info.json` and `artifacts/SHA256SUMS`.

Home now requests candidates from every saved topic and followed channel, plus related videos from saved examples and the two most recent watched videos. Rows load progressively; more saved sources mean more requests and longer refreshes. Per-source failures preserve successful rows and surface errors. Saved topics are sent to YouTube to fetch matching results; local ranking excludes watched/hidden videos and uses channel affinity and explicit/title keywords. It is a simple heuristic, not Google's recommendation algorithm.

Playback code is unchanged from the previous verified version. A new phone test, every quality format, and a complete remote-keyboard-to-playback regression were not rerun. The new preference import/migration is verified on hardware; full interactive management of every preference control is not automated.

---

# Validation

Executed on 2026-10-30, macOS arm64, JDK 21, Gradle 8.13; device: Chromecast (sabrina), Android 14.

| Check | Observed result |
| --- | --- |
| `./scripts/build.sh` | Passed: `testDebugUnitTest`, `lintDebug`, `assembleDebug`; final run 13 seconds |
| Local unit tests | 3 passed: canonical YouTube URLs and rejection of foreign schemes/hosts, watched/dismissed exclusion and deduplication, channel/keyword recommendation ranking |
| `testDebugUnitTest -PliveTests=true` | Passed, 0 skipped in the live test: real search returned 20 videos; Big Buck Bunny yielded 15 video formats. This establishes extraction, not decoding all formats. |
| `connectedDebugAndroidTest` | SQLite persistence regression passed on the Chromecast: reopen retains history/progress/subscriptions, replay preserves hidden choices, history deletion preserves hidden choices and subscriptions, preference reset/unsubscribe work |
| Opt-in `PlaybackSmokeTest` | Passed on the Chromecast: a real public video entered playback, playback position advanced past 2 seconds, and resume progress was saved after leaving the player. Approximately 15 seconds for the test. |
| APK signature | Verified using APK signature scheme v2; signed with the local Android **debug** key. See `artifacts/signature-verification.txt`. |
| TV UI observation | Main activity launched; home content and local recommendation rails rendered. A black-on-dark heading issue was observed, corrected, rebuilt and visually rechecked. D-pad focus moved to Search, and Select opened the Search screen. See `artifacts/screenshots/search.png`. |

The final change after the playback test sets default TV text color; final build/unit/lint checks passed after that change. The exact final APK was installed with `adb install -r`; the official YouTube package was not replaced. Gradle's initial connected-test run cleaned up its installed target package; MyTube was subsequently reinstalled for live playback and final delivery.

Default unit-test builds intentionally skip the live extractor test. The explicit live-test invocation above ran it; a later default build can overwrite its XML report with a skipped result. Logs remain in `live-extractor-test.log`, `device-tests.log`, `playback-device-test.log` and `build.log`.

## Remaining verification and limitations

- Not a complete security audit, and no claim that every video or stream format works. Upstream service changes, region/account restrictions and request throttling can affect extraction.
- Phone layout has not been tested on a physical phone. It uses the same application/core with compact navigation.
- Subscription-feed UI, paginated search, every remote/IME combination, captions, every quality cap, long-duration playback, and network-loss recovery have not all been exercised end to end on hardware. Core subscription persistence was tested separately.
- The live playback test verified position advancement, not an independent measurement of audiovisual synchronization or output quality. It uses an actual public YouTube video and can create a local history entry.
- No automated test of the complete remote-keyboard → search → card dialog → playback sequence. Search/extraction, the player entry point, SQLite storage and selected D-pad navigation were checked separately.
- Lint has no errors; remaining warnings include newer dependency versions, vector-banner sizing and preference-edit style. Warnings were not suppressed with a baseline.
- This is a personal-testing debug APK, not a production-signed or store-ready release. Cross-device sync, Google login, voice input, casting, SponsorBlock, downloads and a dedicated Shorts screen are outside this version.

## Re-run the opt-in playback check

The test briefly plays video on the authorized device. Run it only when that is desired:

```sh
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class app.mytube.tv.PlaybackSmokeTest \
  -e livePlayback true app.mytube.tv.test/androidx.test.runner.AndroidJUnitRunner
```

The copied official APKs are research artifacts only; no official executable code or resources are included in the MyTube build.
