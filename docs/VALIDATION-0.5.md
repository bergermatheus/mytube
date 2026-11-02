# MyTube 0.5.0 validation

Executed 2026-11-01–2026-11-02 on the paired Chromecast (sabrina), Android 14. The optimized and debug APKs use the existing local development certificate and update in place. No original YouTube package was modified.

## Implemented behavior

- Global header search and system-recognizer microphone; curved red/white play mark and MyTube at the top right. Search is removed from the sidebar. Keyboard/recognition results focus the first result.
- No outer browse-page padding. The grid reaches the right/bottom viewport boundaries and the sidebar begins at the left boundary; small internal control/focus gutters remain.
- A real expanded sidebar footprint moves/resizes the fixed three-column grid without overlap or a clipped left button border. Right scrolls to and focuses the first video, including after scrolling down. Refresh is removed.
- Home samples five candidate sources and balances channels across age bands, prioritizing newer uploads within each channel. Ten-video pages retain their committed prefix. Source searches can overlap channels, so candidate diversity is bounded by available content.
- Displayed batches resolve original YouTube oEmbed titles, with original player metadata fallback. At most three title requests run at once; cancellation aborts their HTTP calls. The memory cache retains up to 512 titles for 24 hours. Related-title work starts after playback; visible history cards refresh titles through the browse model.
- Centered geometric play/pause and bar-plus-triangle previous/next symbols. Settings uses rounded Quality/Subtitles/Speed rows with current values and Back-driven option lists; no Close button.
- Comments uses rounded focusable cards, real avatars/date text, available like/reply counts, pinned markers, and the supplied total count. Its own loading/list focus handles asynchronous results; Back closes the panel. No Close or account-posting row. Video shrinks into the remaining width beside settings/comments.

## Executed checks

- `scripts/build.sh`: fourteen local unit tests passed, three opt-in live/research cases skipped; debug/release lint and both APK assemblies passed. The channel-monopoly regression verifies that a much fresher channel cannot occupy most of a balanced ten-video sample.
- Hardware layout test passed on the final edge-to-edge layout: header controls/brand, removed Search/Refresh, non-overlap, grid viewport boundaries, and Right-to-first-video after scrolling.
- Original-title hardware test passed: two recent Renato channel candidates matched original player metadata, and a deliberately incorrect English fixture title resolved to `Me at the zoo`.
- Player upgrade test passed after the comment-card changes: centered transport, right settings bounds, actual speed change/restoration, selected text track and actual timed caption cues, no Close in either panel, loaded comment cards, previous/next playback and avatar-to-channel navigation.
- Home paging, autocomplete and player Down/Up navigation passed. Autocomplete was rechecked through the header after removing sidebar Search. An independent decoder-output/progress check passed during this version's implementation before the last panel/layout changes.
- Voice launch/result contract passed with an intercepted recognition result. The test did not record audio or exercise actual spoken recognition; the device has a system speech handler.
- Live comment-avatar validation passed after adapting the newer `author.avatarThumbnailUrl` field to the image-source structure consumed by the pinned extractor. Existing image arrays and unrelated/malformed bodies remain unchanged; the real NewPipe image decoder is exercised by the compatibility regressions.
- Before/after read-only preference snapshots retained identical channel/topic identities. See `artifacts/performance/before-0.5-preferences.json` and `after-0.5-final-preferences.json`.

Per-case results are in `artifacts/0.5-device-checks.json`; logs are in the project root. Final optimized ADB walkthrough results are in `artifacts/optimized-playback-results.json` and `optimized-adb-playback-0.5.log`. APK identity and signing evidence are in `artifacts/build-info.json`, `artifacts/SHA256SUMS`, and `artifacts/signature-verification.txt`.

## Original-APK research

REA matched the saved original APK hash and decompiled the Cobalt bridge entry point. Its manifest loads `https://www.youtube.com/tv`; static inspection does not recover all current server-supplied screen layouts. The screenshots therefore guide visible geometry. Exact Evidence IDs and retained outputs are in `docs/YOUTUBE-UI-REFERENCE.md` and `artifacts/rea-ui-reference/`. No official executable code/resources were embedded in MyTube.

## Scope and limitations

One Chromecast and a bounded set of public videos were exercised. This does not establish every codec, quality, subtitle language, IME, phone layout, long viewing session or audiovisual synchronization. Original-title lookup retains the existing feed title if both original metadata sources are unavailable. Comments currently expose one response page and do not post comments. Some captures omit the native video surface; they establish UI layout rather than independent video pixels.

The earlier `0.5-optimized-adb-native-warm` frame report was captured before the last comments/full-screen updates. It reports a single run with 69 rendered frames, 73.91% missing their deadline and a 77 ms 95th percentile; it is diagnostic, not evidence that reflow is jank-free. It uses a different key sequence and far fewer animated frames than 0.4, so percentages are not a like-for-like speedup comparison. Raw CPU, RSS, elapsed time and key pacing are retained under `artifacts/performance/`. No cold-start, agent-token or final-frame benchmark is claimed.
