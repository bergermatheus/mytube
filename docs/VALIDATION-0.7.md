# MyTube 0.7.0 validation

Executed 2026-11-02 on the paired Chromecast (`sabrina`, Android 14). The normal APK uses R8/resource shrinking and the existing local Android development signing key for in-place installation. Database schema remains 4.

## Changes and root cause

Home's suspendable pager previously performed ranking, mixing and preparation on its caller's UI thread between network waits. `HomePager.next()` now owns a worker dispatcher for the complete preparation pipeline. The view model publishes only completed batches. A local unit check verifies that source handling, ranking and preparation do not execute on the caller thread.

The previous full-width pagination action could also receive focus after Down on the final loaded card. The Home grid now keeps focus on that last row while waiting; its status footer is passive. Up, Left, Right and Select continue to operate on existing cards. Prefetching still starts near the end and appends ten videos without changing committed cards.

The initial Home/channel spinner is centered in the whole viewport and does not consume input. Publication labels appear below titles beside channel names, using local calendar days: Today, 1 day ago, 2 days ago, weeks, months and years. Future and unavailable timestamps remain explicit. Duration stays on thumbnails; source timestamps/text remain preserved in the model/library.

Subscriptions now renders a focusable grid of circular real channel photos with names. Metadata loads independently, with at most three requests at once and a 64-entry, 24-hour in-memory gateway cache shared with Home channel extraction. Failed/missing photos use a person placeholder. Right from the sidebar safely scrolls to/focuses the first channel; selecting a channel opens its feed. No preference or database schema changes were needed.

## Executed validation

- `scripts/build.sh`: unit tests, debug/release lint and both APK builds passed (`build-0.7.log`). Eighteen local unit tests passed; three opt-in research/live tests were skipped. Summaries: `artifacts/unit-test-summary.json`.
- `assembleDebugAndroidTest` passed (`build-0.7-tests.log`).
- Six Chromecast instrumentation tests passed (`home-0.7-device-test.log`, 144.42 seconds). They check spinner center coordinates; date placement below titles beside channel names; a real HTTPS channel avatar and circular-grid layout/selection; stable live ten-video paging and direct player launch; sidebar geometry; search keyboard/history/pagination and preserved Home scroll; and additive historical search-schema migration.
- A controlled pending-page check uses the actual production grid with a deliberately unreleased next batch. Up/Right/Left/Down/Select all work before release, only one page is requested, and focus remains on the same card when the next batch arrives. This is a component regression, not a network speed or frame-rate benchmark.
- Relative-date units/plurals, local-midnight boundaries, missing timestamps and future dates are covered by local tests.
- Before/after copied-library snapshots have identical subscription/interest digests: 12 channels and one interest. See `artifacts/performance/before-0.7-device-checks-preferences.json` and `after-0.7-device-checks-preferences.json`.
- APK signature verification passed using the existing certificate. Identities and sizes: `artifacts/build-info.json`, `SHA256SUMS`, `signature-verification.txt`.

Screenshots from instrumentation are retained in `artifacts/screenshots/0.7-debug/`.

## Bounds

One Chromecast and bounded public channels/queries were exercised. No production frame-time improvement, CPU reduction or end-to-end speedup is claimed; no paired before/after performance benchmark was run. Image metadata availability and video extraction still depend on YouTube. The photo cache lasts for the app process; Coil manages decoded/disk image caching. Existing player-specific quality/subtitle/comment checks retain their earlier records and were not all rerun for this UI change. Phone/touch layouts and long sessions remain unverified.

## Final optimized installation

The optimized 0.7.0/code 7 APK is installed and verified through Android package metadata. The additional ADB-only `check-home-tv.py` walkthrough captured Home metadata but did not finish: `uiautomator dump` timed out. A later foreground-state check observed the official YouTube activity; the reason the foreground changed was not established. This failed extra harness is retained in `optimized-home-0.7.log` and is not counted as a passing check. The six completed Chromecast instrumentation checks above remain the executed behavior validation. MyTube was subsequently brought to the foreground by its explicit activity component.
