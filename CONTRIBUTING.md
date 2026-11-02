# Contributing to MyTube

Everyone is welcome to improve MyTube: code, TV interaction design, accessibility, translations, extraction compatibility, documentation, screenshots and reproducible bug reports all help. The shared purpose is an open, ad-free television client that respects the viewer's choices.

## Get started

Read the [constitution](CONSTITUTION.md), [agent/project guide](AGENT.md) and [development guide](docs/DEVELOPMENT.md). Clone your fork, open this folder as the Android project and use the included Gradle wrapper. You do not need the original YouTube APK, REA, someone else's `preferences.json`, a Google account or a YouTube API key.

Make a focused change on a branch. Keep unrelated user edits intact. For behavior changes, explain the trigger and observable result; for UI changes, include actual screenshots and the remote steps used to reach the page. Favor predictable D-pad focus, direct playback and responsive navigation during data loading.

## Verification

Run checks relevant to your change:

```sh
./gradlew --no-daemon --console=plain testDebugUnitTest lintDebug
```

Use `./scripts/build.sh` for the full local packaging gate. For device tests follow the explicit in-place installation workflow in [DEVELOPMENT.md](docs/DEVELOPMENT.md#5-verify-changes). Do not erase a personal library, uninstall the target or run tests against someone else's TV without authorization. State which device/build was checked and which network/playback checks were skipped. Documentation-only edits need link/image/command validation, not an Android rebuild.

For a loading/focus/performance change, preserve committed feed order and verify interaction while requests are pending. For storage changes, use additive migrations with historical fixtures. For player changes, check cancellation, stale callbacks, Back/Up/Down routing, progress and next-video behavior as applicable. Report performance with equivalent before/after workloads and label debug instrumentation overhead.

## Bug reports and pull requests

A useful issue includes the app version, TV/Android version, steps, expected/actual behavior and a relevant error or screenshot. Include a public video URL when needed to reproduce extraction/playback. Keep private profiles, pairing codes, credentials and unrelated viewing history out of reports. Missing evidence is fine when clearly identified.

A pull request should explain what changed, why, validation actually run and material limits. Keep screenshots under `docs/images/` only when they are intended for public documentation; keep raw captures, logs, APKs and performance reports in ignored `artifacts/`. Use a concise Conventional Commit title such as `fix: preserve Home focus after search` or `docs: explain Android CLI setup`.

Human maintainers review contributions and release decisions. Do not publish keys, copied official APKs, account/session data or unreviewed public releases. The current local release-signing configuration is for development; stable public distribution still needs a maintainer-owned signing process.

## License

Contributions must be compatible with this project's [GPL-3.0-or-later license](LICENSE). Keep dependency notices current and preserve original attribution. Do not copy Google's proprietary application code/assets into MyTube. Video/creator media appearing in screenshots is not relicensed as MyTube source code.
