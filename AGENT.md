# MyTube agent guide

## Start here

This is an independent, ad-free, GPL-3.0-or-later YouTube client for Google TV / Android TV. It was started out of frustration with YouTube ads and is intended for open community contribution. Read [CONSTITUTION.md](CONSTITUTION.md) for product principles and [README.md](README.md) for the public introduction. Follow the user's authorized scope; these are project defaults, not a reason to ask again about already authorized work.

This folder is the Android app repository. A neighboring `rea` repository, local official APK copies and UI reference photographs are optional research inputs, not source dependencies. Do not implement the app in `rea`. Use ordinary source tools for normal app work. REA is useful only when a question needs original-artifact/runtime evidence; existing findings are in `docs/YOUTUBE-*-REFERENCE.md`.

## Runtime and startup

- Application ID: `app.mytube.tv`; launcher: `app.mytube.tv/.MainActivity`.
- Kotlin + Jetpack Compose for TV, AndroidX Media3, NewPipeExtractor, Coil, OkHttp and SQLite. There is one `app` Gradle module and no web server, npm install or backend to start.
- Android minimum API 24; compile/target API 36. JDK 17 minimum, 21 used locally; Gradle 8.13 is pinned by the wrapper. Dependency/plugin versions live in `app/build.gradle.kts` and `build.gradle.kts`.
- MyTube currently uses no Google login or YouTube API key. It still needs internet for actual YouTube content. Local subscriptions/likes/dislikes never update a Google account.
- Run from this repository root. Read [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for SDK/PATH setup, Android CLI, emulator creation, pairing, installation and diagnostics.

Quick development loop after configuring Java/SDK and connecting an emulator or TV:

```sh
./gradlew --no-daemon --console=plain testDebugUnitTest lintDebug assembleDebug
adb devices -l
# Set to the exact serial printed above; never choose the first device implicitly.
export MYTUBE_DEVICE="emulator-5554"
adb -s "$MYTUBE_DEVICE" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$MYTUBE_DEVICE" shell am start -W -n app.mytube.tv/.MainActivity
```

For the full local packaging gate use `./scripts/build.sh`. It checks local unit tests and both lint variants, builds debug + optimized release, and copies versioned APKs/hashes to ignored `artifacts/`. The current release uses the local Android debug keystore; do not treat it as a public release-signing setup. Another contributor's key cannot replace an existing differently signed installation.

## Code ownership and context

Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the data flow and source map. The important owners are:

| Concern | Owner |
| --- | --- |
| Composition/root navigation | `MainActivity.kt`, `Sidebar.kt`, `BrowseHeader.kt` |
| Route data, jobs and invalidation | `BrowseViewModel.kt`, `BrowsePages.kt` |
| Retained page composition/focus | `RetainedPages.kt` |
| User-facing YouTube failures | `YouTubeErrors.kt` (typed exceptions, never class-name matching) |
| Extraction, metadata and channel images | `ExtractorGateway.kt`, `OriginalTitles.kt` |
| Home batches, filtering and channel mix | `HomePager.kt`, `Models.kt`, `HomeVideoGrid.kt` |
| Embedded keyboard/search/results | `SearchPage.kt` |
| Durable preferences/history/progress | `LibraryStore.kt`, `WatchProgress.kt` |
| Local display identity | `ProfileStore.kt`, `ProfileSettings.kt` |
| Player lifecycle/session/navigation | `PlayerActivity.kt` |
| Bottom dock/related grid/drawers | `PlayerControls.kt`, `PlayerDrawer.kt` |
| Chapter metadata and timeline | `VideoChapters.kt`, `ChapterSeekBar.kt` |
| Audio language identity/original selection | `AudioLanguages.kt` and `PlayerActivity.kt` |
| Autoplay deadline/cancellation | `UpNext.kt` and its owner in `PlayerActivity.kt` |

Current behavior to preserve:

- Sidebar order: Search, Home, Subscriptions, Library, separator, Live; Settings at the bottom. Focus activates a page without Enter. Right enters content; Left from the first grid column returns to the sidebar. Expanded navigation reserves width instead of covering videos.
- Visited browse pages retain composition/scroll for the current activity; jobs update their captured page. Inactive pages must not draw, expose descendants to accessibility or receive focus. Explicit destination focus restoration takes precedence over automatic group entry.
- Home commits ten videos at a time. Further loading appends; it must not reorder the loaded prefix or disable navigation/playback. A centered spinner is for the initial load; paging is a passive footer.
- The Home source/filter/mix stages are distinct. `Recommendations.rank` contains affinity scores, but `HomeFeed.mix` starts by sorting URLs and shuffling; do not describe those scores as final grid percentages. Trace the committed output when changing recommendations.
- Select opens playback immediately. Down focuses progress, then play/pause actions, then related videos. Seeking is allowed only on the focused progress bar; video focus permits play/pause only. Up traverses related rows before returning to controls; only the first row exits. Back closes drawers/panels before leaving playback.
- Chapters come from extraction metadata. Do not invent chapters, dates or completion. Titles remain in their supplied language. Progress reflects saved position/duration; completion is set by an actual playback-end event.
- Up next has a ten-second monotonic deadline and cancels on user interaction/backgrounding. No stale job may switch the wrong video after cleanup or a new selection.

## How to make and verify changes

1. Inspect the producer and affected consumers before patching symptoms. Keep blocking extraction/SQLite/preparation work off the UI thread. Give jobs and caches a clear lifecycle and invalidation rule.
2. Preserve saved data and defaults. Library schema is currently 5; migrations must be additive and tested against historical schemas. Never hardcode the owner's channels, interests, device serial/IP or profile into shared source or documentation.
3. Use focused unit tests for data semantics and device tests for focus/layout/playback. For a docs-only change, check links, shell syntax and screenshot paths; a full Android rebuild is unnecessary.
4. For device tests, build `assembleDebugAndroidTest`, install both APKs with `adb install -r`, then run the explicit instrumentation class. Avoid `connectedDebugAndroidTest` on a personal TV because managed installation/cleanup can remove the app. See the development guide for opt-in flags and known test side effects.
5. Device interaction requires the user's authorized device/task scope. Discover fresh serials/ports, use `adb -s`, verify the foreground package before remote key sequences and preserve explicit preferences. Never reuse old pairing codes from chat. Do not wipe/uninstall/reset a device to repair a connection.
6. Record only checks actually run and their limits. Instrumented debug timings do not establish optimized release smoothness. Reproduce performance workloads before/after and separate app CPU/RSS from unmeasured process-family cost.
7. Keep generated artifacts/logs under ignored `artifacts/` or a temporary directory, not the project root. Shareable curated screenshots belong in `docs/images/` with provenance. Never commit keystores, credentials, personal `preferences.json`, local APK research, SDKs or build caches.
8. Before packaging, keep versionCode/versionName, `scripts/build.sh`, versioned install paths and validation notes consistent. Use in-place installation only when authorized and verify the actual installed version. Public release signing and publishing are separate from local development.

Continue requested reversible work without unnecessary confirmation. Ask for genuinely missing requirements or destructive actions; do not turn ordinary fixes/builds into an approval flow. Keep unrelated user changes intact and make the final result reviewable.

## Where facts live

- [Development and device commands](docs/DEVELOPMENT.md)
- [Architecture and cache/data ownership](docs/ARCHITECTURE.md)
- [Feature and remote-control behavior](docs/FEATURES.md)
- [Contribution guide](CONTRIBUTING.md)
- [Latest recorded app validation](docs/VALIDATION-0.16.md) (historical evidence, not a fresh test run)
- [License](LICENSE) and [third-party notices](THIRD_PARTY_NOTICES.md)
