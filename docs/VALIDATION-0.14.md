# MyTube 0.14.0 channel listing validation

Executed 2026-11-02. Application ID `app.mytube.tv`, version code 14; database schema remains 5.

## Root cause and change

The channel loader selected only the regular Videos tab and consumed only its initial response. CazéTV returned continuation-only initial grids; its Videos tab required two continuation requests to yield five uploads, and its Live tab required one to yield a broadcast. Before the fix, the channel candidate list omitted those broadcasts. The UI also reused Home’s “No unseen videos” message on channel pages, although channel pages do not exclude watched videos.

A shared tab reader now resolves deferred grids, with at most three continuation requests per tab. The channel/Home source combines Videos and Live tab previews and deduplicates URLs; the Live-only source uses the same reader before filtering active broadcasts. Unfinished previews report an error instead of pretending the channel is empty. Empty channel pages have their own message. These remain recent previews rather than complete channel archives.

## Executed checks

- Opt-in `ChannelFeedTest`, using the real public CazéTV channel and the pinned NewPipeExtractor: reproduced the omission before the fix; passed afterward with six merged entries, including the Live tab entry. Zero entries were marked actively live during that check. The test also checks upload retention and URL deduplication.
- `./scripts/build.sh`: passed in 68 seconds, including local unit tests, debug/release lint and both APK variants. Unit report: 37 discovered, 33 passed, four opt-in network tests skipped. The channel network test above ran separately with `-PliveTests=true`.
- `assembleDebugAndroidTest`: passed. Ran `ChannelAndAudioTest#channelIntentKeepsVideoGridWhenEnteringFromSubscriptionsLabel` on the connected Chromecast with an explicit CazéTV channel argument: one test passed in 22.007 seconds. It checks initial video focus, sidebar Left/Right navigation, retaining the channel page, moving to the second row, and absence of the incorrect Home empty state.
- Installed debug/test APKs using `install -r`, then restored the optimized 0.14.0 APK with `install -r`. Package manager reports version code 14 / version 0.14.0. No uninstall, clear-data operation or preference import was used.
- Optimized-app accessibility dump shows CazéTV, video titles and channel/publication metadata instead of the empty state. Captured an actual channel screenshot. Raw logs and device captures are ignored under `artifacts/validation/0.14/`.

This verifies channel listing and navigation on one device/network. It does not verify playback of a region-restricted football match, Surfshark connectivity, every channel tab or the full channel archive. Screenshots in the public README retain their individually documented older capture versions.
