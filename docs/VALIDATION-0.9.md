# MyTube 0.9.0 validation

Executed 2026-11-02 on the paired Chromecast (`sabrina`, Android 14). The optimized APK uses R8/resource shrinking and the existing local development signing key. Android reports version 0.9.0/code 9 after in-place installation and explicit MainActivity launch.

## Behavior and ownership

Thumbnails have a red progress strip at their bottom edge. It shows saved position/duration for partial viewing and a full strip after an actual Media3 playback-end event. Unwatched and ongoing live/unknown-duration items do not acquire a fabricated partial percentage. Completion is an explicit library fact; a near-end position alone does not mark a video completed.

Library schema 4→5 adds only `videos.completed` with a default of false, preserving existing positions and preferences. The library owns bounded progress snapshots and their merge into current video metadata. Home, channel, search, Library and related cards consume those facts. Saved-player changes notify the browse model, including its retained Home/search sessions, so asynchronous final saves cannot leave stale thumbnail markers after returning. Clearing watch history clears progress/completion while retaining explicit preferences. Completed videos restart when explicitly watched again; partial videos retain their saved resume position.

At a natural video end, the player pins the first available related recommendation and a monotonic ten-second deadline. Up next shows its thumbnail/title/channel, countdown, Play next and Cancel. Play next starts immediately. Cancel or Down stops autoplay and opens related videos; Up stops it and reveals the control dock. Other app-received input stops the timer while preserving an explicit Play next option. Backgrounding/stopping/error/switching cancels the owned job. The target/deadline is cleared before automatic transition so it is consumed once. A video finishing while the user is already browsing related videos or editing a drawer does not interrupt them with autoplay. No recommendation means no scheduled next video.

## Executed checks

`scripts/build.sh` passed `testDebugUnitTest`, `lintDebug`, `lintRelease`, `assembleDebug` and `assembleRelease` (`build-0.9.log`). Twenty-eight local tests passed; three opt-in live/research tests were skipped. Local coverage includes partial/completed/clamped/live/unknown progress, snapshot absence vs unrequested records, exact ten-second deadline/ceiling display, cancellation and independent next-video deadlines. Summaries: `artifacts/unit-test-summary.json`.

`assembleDebugAndroidTest` passed. Ten Chromecast checks passed in `endscreen-0.9-device-test.log` (85.462 seconds):

- Schema 4→5 migration, preserved prior positions/preferences/searches, durable completion, exact resume behavior, progress lookup over 1,001 requested URLs and history clearing.
- Four existing library regressions, including historical schema 1 upgrade, publication/rating persistence, saved preferences and watch progress.
- Historical schema 3 search-history migration using a frozen historical fixture rather than today's onCreate.
- Actual partial/full thumbnail geometry and progress semantics, with screenshots.
- Real decoded public video playback, a seek near its actual end and the resulting Media3 end event.
- Automatic transition to the pinned recommendation without input after the ten-second interval.
- Cancellation through remote navigation/Cancel, waiting beyond the deadline without switching, Down into related videos, replay and immediate explicit Play next.
- Moving the player task into the background before expiry and observing no late switch.

An additional production UI check passed in `watch-progress-0.9-browse-test.log` (34.545 seconds). It opened the public completed video through the actual search UI, checked a full thumbnail strip, replayed it, returned through physical Back and observed partial progress on the same thumbnail. The test waits for keyboard focus before typing to avoid dispatching text during the search-page transition. This validates the actual library→model→card path, beyond the standalone card geometry check.

Before/after copied-library snapshots have identical subscription/interest digests: 13 channels and one interest. Evidence: `artifacts/performance/before-0.9-device-checks-preferences.json` and `after-0.9-device-checks-preferences.json`. A local pre-migration database copy was retained at `/private/tmp/mytube-before-watch-completion-schema/`. Public test-video history was updated by the playback checks; explicit user preferences were not changed.

The APK's signature verifies under the existing certificate. Sizes/hashes are in `artifacts/build-info.json`, `SHA256SUMS` and `signature-verification.txt`. UI screenshots are in `artifacts/screenshots/0.9-debug/`, including partial/full red strips, Up next at ten, canceled related navigation and persisted search progress. The optimized APK was installed successfully and left open on Home.

## Bounds

One Chromecast and bounded public videos were used. An automatic target switch and playback preparation were verified; not every recommended target/codec/live-stream end case was decoded. If extraction fails for the next target, the existing player error/retry UI applies. Native video surfaces may not appear in screenshots; the real-player tests independently observed playback/decoded-frame counters and end events. End-card timers use elapsed realtime, not wall clock. No new comparative frame-rate/CPU/memory performance claim is made. The recommendation scoring/mixing behavior was not changed by this playback/progress update.
