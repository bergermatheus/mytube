# MyTube 0.15.0 grid boundaries and broadcast investigation

Executed 2026-11-02. Application ID `app.mytube.tv`, version code 15; database schema remains 5.

## Grid focus fix

The shared vertical grid consumed Down at its bottom boundary only when rendering Home. Other pages could let Compose find the Settings button below the grid. The boundary now applies on every use of that grid, including channel pages, Library and Live. Home’s load-more trigger remains Home-only. Up and horizontal navigation still work; Left from the first column explicitly returns to the sidebar.

`ChannelAndAudioTest#channelIntentKeepsVideoGridWhenEnteringFromSubscriptionsLabel` now traverses to the actual last channel row, presses Down three times while asserting unchanged card focus, navigates Up, and exits explicitly with Left. The real CazéTV device test passed in 67.468 seconds after the VPN route change. This verifies the channel route on one Chromecast; Library/Live use the same fixed component but did not receive separate device runs.

## Exact broadcast investigation

Requested public video: `https://www.youtube.com/watch?v=cWEmbuUTIBI` — Felipe Okano, table tennis at Dakar 2026.

- Android reported an active VPN whose application ranges included official YouTube but excluded MyTube. MyTube’s direct extraction of this URL returned `GeographicRestrictionException`.
- The owner added MyTube to Surfshark’s Route via VPN list and reconnected to Brazil. Subsequent channel responses included the exact video. No VPN settings were changed by the implementation or test code.
- A separate playback metadata request then received YouTube’s `Unavailable with a VPN or proxy` response. That is an observed upstream rejection for this client/request; playback success in the official app does not establish acceptance of MyTube’s request.
- During the investigation the broadcast ended. A fresh public channel response showed duration `4:09:44`, a default duration badge rather than a live badge, and `Streamed 4 minutes ago`. The suspicion that the extractor was misclassifying this entry was not supported by this later response. No title-based live detection or fabricated live flag was added.
- The live-only assertion failed for the now-completed fixture, as expected for a replay. The final exact-broadcast regression explicitly uses `expectedLive=false` to verify channel membership and exclusion from the active-live feed. It passed on the Chromecast in 12.283 seconds.

Temporary raw-response capture code was removed from the test source. Public response captures and device logs stay ignored under `artifacts/validation/0.15/`; they are not suitable for publication without review.

## Build and scope

`./scripts/build.sh` passed in 67 seconds: debug/release lint, both APK builds and local unit tests. Unit report: 37 discovered, 33 passed, four opt-in network tests skipped. `lintDebug assembleDebugAndroidTest` passed after final regression edits. The optimized APK is restored with an in-place update; no uninstall, clear-data operation or preference import is used.

These checks do not establish reliable playback through this VPN endpoint, acceptance of another anonymous client, or future live availability. Trying a different Brazilian VPN endpoint may change upstream acceptance; it is not a guaranteed app-side fix.
