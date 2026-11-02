# MyTube 0.8.0 validation

Executed 2026-11-02 on the paired Chromecast (`sabrina`, Android 14). The normal APK uses R8/resource shrinking and the existing local Android development signing key. Library schema remains 4.

## Implemented behavior

- Sidebar order: Search, Home, Subscriptions, Library, separator, Live; Settings at the bottom. Focus activates each route without Enter. Search previews beside the sidebar until Right enters the keyboard; the top search pill retains its full-screen route. Left from the first video/channel column returns to the matching menu item, avoiding accidental activation of a vertically adjacent route.
- Compact 38dp menu rows, outlined reference-style search/home/subscription/bookmark/broadcast/settings vector icons, and a clock/return-arrow icon for recent searches. The obsolete Local Library/No Google sign-in footer is removed.
- Top-left local profile circle/name; empty identity displays User/U. Settings edits the name, imports a photo, accepts an HTTPS image URL, and removes a photo. Imported photos are sampled/copied into app-private storage. Profile preferences and photos are independent of the existing library and add no Google login or storage/account permissions.
- Live checks followed channels' livestream tabs, saved interests and channels inferred from saved examples/recent browsing. Three source checks run concurrently, publishing stable additions progressively. Explicit live-state metadata is required; recordings, upcoming premieres and explicit paid/member-only items are excluded. Unknown access classification remains distinct from the live-state fact. Results show LIVE and Live now labels. Sources are refreshed on entry; broadcasts can end after discovery.
- Home retains its committed session while menu focus changes. Pager preparation is transactional and serialized: cancelling an uncommitted batch cannot consume its source cursor or videos when the session is resumed. A new explicit Home session invalidates the old snapshot.

## Executed checks

`scripts/build.sh` passed unit tests, debug/release lint and both APK builds (`build-0.8.log`). `assembleDebugAndroidTest` passed (`build-0.8-tests.log`). Twenty-three local tests passed and three opt-in research/live tests were skipped. Coverage includes live-state vs availability/recording/paid/upcoming classification, filtering/ranking, profile fallback identity, relative Live labels, background pager execution and cancellation/resumption without losing candidates. Exact summary: `artifacts/unit-test-summary.json`.

Eight distinct Chromecast checks passed across the main run and focused rerun:

1. Automatic menu activation and exact requested route order; compact spacing/separator; clock icons; keyboard entry with Right; retained Home cards after visiting Search; profile settings visible.
2. A followed CNN Brasil channel returns active live candidates. Fresh player metadata reports LIVE_STREAM and an HLS manifest; the personalized Live page renders a LIVE card and retains sidebar focus while loading other sources.
3. Local profile name/photo survive reopening, photo removal removes its owned copy, and invalid HTTP photo URLs do not replace the existing photo.
4. Profile form saves a typed name through its configured IME action to its isolated durable profile store.
5. Sidebar reserves real space, viewport edges remain correct, and Right returns to video zero after scrolling.
6. Existing full-screen search keyboard/autocomplete, ten-result append, history, Back and Home-scroll preservation.
7. Live network Home paging appends ten without changing the first card and retains direct player navigation.
8. Loaded-card navigation/Select remain available while a next batch is deliberately held pending.

The main run (`sidebar-0.8-device-test.log`) passed seven checks; its profile form step timed out after hiding the IME and immediately sending a remote activation. The focused rerun through the field's actual IME action passed (`profile-0.8-final-test.log`). That timed-out key sequence is not counted as a passing check. Earlier exploratory logs retain the live/availability mismatch that led to separating UNKNOWN access from an explicit LIVE_STREAM state.

Profile tests use isolated preference names and image files, preserving the user's actual profile. Before/after copied-library snapshots have identical subscription/interest digests: 12 channels and one interest (`artifacts/performance/before-0.8-device-checks-preferences.json`, `after-0.8-device-checks-preferences.json`). The optimized APK signature verifies under the existing certificate; hashes/sizes are in `artifacts/build-info.json`, `SHA256SUMS` and `signature-verification.txt`. UI evidence is retained in `artifacts/screenshots/0.8-debug/`.

## Coverage bounds

One Chromecast and bounded public channels/queries were exercised. Current CNN live metadata and an HLS URL were verified; an entire live broadcast playback session was not measured. Failed source checks are reported and do not establish that a channel has no current broadcast. No signed-in YouTube recommendations/account data are used. The image import/store was tested with a local image; external document-picker/provider availability depends on the TV, with HTTPS URL entry available as an alternative. Remote photo-server failures use the profile initial fallback. No new frame-rate, CPU/memory speedup or paired performance benchmark is claimed. Other player quality/subtitle/comment cases retain their prior validation records; phone/touch layouts remain unverified.

The optimized APK was installed in place successfully. Android reports versionName 0.8.0/versionCode 8, and its explicit MainActivity launch completed. The installed Home screenshot is `artifacts/screenshots/0.8-optimized/installed-home.png`; it confirms the new sidebar/profile, removed footer and centered initial spinner.
