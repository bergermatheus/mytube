# Public UI previews

These are real MyTube captures, not photos of the official YouTube app or generated mockups. The PNGs live outside ignored `artifacts/`, so relative image links work when the source is committed to GitHub or another Markdown renderer. Click-through links in the project README open the full-size originals.

| Image | Working build | Capture context |
| --- | --- | --- |
| [Home](home.png) | 0.10.0 optimized | Mixed Home grid, current sidebar and local display identity; optimized APK smoke check |
| [Search](search.png) | 0.13.0 debug | `VerticalBrowseTest`: aligned results with complete title/channel/publication metadata and collapsed keyboard |
| [Player chapters](player-chapters.png) | 0.10.0 debug capture | Actual decoded Python-course frame, chapter title and separated timeline |
| [Settings](settings.png) | 0.10.0 debug capture | Quality/subtitles/speed drawer beside the actual video frame |
| [Comments](comments.png) | 0.10.0 debug capture | Real public-video comments beside the actual video frame |
| [Related videos](related-videos.png) | 0.10.0 debug capture | Recommendation grid below the actual video frame |

All were captured on the development Chromecast with Google TV running Android 14. Legacy folders in test output names do not imply that a new screenshot came from that older app version. [manifest.json](manifest.json) records each curated asset's version and SHA-256; the 0.10 check history is in [VALIDATION-0.10.md](../VALIDATION-0.10.md).

## How the player frames were captured

The normal Chromecast `SurfaceView` hardware video layer was omitted by both ADB screenshots and a native screen recording, even after more than twenty seconds of advancing playback. Waiting longer did not make that layer appear in the capture.

The opt-in [`DocumentationCaptureTest`](../../app/src/androidTest/java/app/mytube/tv/DocumentationCaptureTest.kt) therefore replaces the video surface with a `TextureView` **only inside its temporary test activity**. It plays the same public [Python course](https://www.youtube.com/watch?v=rfscVS0vtbw), waits for decoded video pixels, pauses around 2:16, opens the real controls/drawers/grid and takes unedited `UiAutomation` screenshots. These are real frames rendered together with the actual UI, not a downloaded image composited into a black area. Production player code and rendering defaults are unchanged.

The helper verifies that a sample of the visible video area contains varied pixels before saving each screen. [player-capture.json](player-capture.json) retains the capture method and results. It completed on the development Chromecast; the normal optimized APK was restored afterward. Subscription/interest rows and profile/player-setting preference snapshots matched before/after. Playback updates this public video's normal watch progress.

To reproduce on an authorized, explicitly selected device after configuring Java/SDK (see the [development guide](../DEVELOPMENT.md)):

```sh
./gradlew --no-daemon --console=plain assembleDebug assembleDebugAndroidTest
adb -s "$MYTUBE_DEVICE" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$MYTUBE_DEVICE" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$MYTUBE_DEVICE" shell am instrument -w \
  -e class app.mytube.tv.DocumentationCaptureTest -e captureDocs true \
  app.mytube.tv.test/androidx.test.runner.AndroidJUnitRunner
mkdir -p artifacts/screenshots
adb -s "$MYTUBE_DEVICE" pull /sdcard/Android/data/app.mytube.tv/files/docs-refresh artifacts/screenshots/
# Restore the matching, already built optimized APK for normal use.
adb -s "$MYTUBE_DEVICE" install -r artifacts/mytube-tv-0.13.0.apk
adb -s "$MYTUBE_DEVICE" shell am start -W -n app.mytube.tv/.MainActivity
```

Use matching local signing keys and preserve the existing library; do not uninstall to work around a mismatch. The utility is skipped unless `captureDocs=true`. Its color checks establish visible video pixels, not a complete device/codec compatibility test. Titles, thumbnails, avatars and comments originate from public videos/creators and remain their owners' content. They illustrate the app rather than an affiliation with those creators.

## Add a preview

1. Run the intended MyTube build on an authorized TV/emulator, open the page and wait for loading/transition states to settle.
2. Capture a real screen to an ignored working directory. Replace the serial with the one from `adb devices -l`:

```sh
mkdir -p artifacts/screenshots
android screen capture --device "$MYTUBE_DEVICE" --output artifacts/screenshots/new-page.png
# Or, in a binary-safe shell:
adb -s "$MYTUBE_DEVICE" exec-out screencap -p > artifacts/screenshots/new-page.png
```

3. Review the capture for profiles/history/network information that should not be public. Do not silently alter the app's appearance or fill missing video surfaces and present the result as an unedited screenshot.
4. Copy a reviewed PNG into this directory, update the table/manifest with its actual build/source and add a relative image link to the README:

```md
![Description of the real MyTube page](docs/images/new-page.png)
```

5. Check that the image path exists and is not ignored before including it in a contribution. Do not link to local absolute paths or ignored `artifacts/` files in the public README. Keep full raw capture sets out of the repository; curate only useful previews.
