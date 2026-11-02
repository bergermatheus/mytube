# MyTube 0.12.0 validation

The player's remote focus order is now video → progress bar → play/pause action row → related grid. Up reverses those levels, including normal row navigation inside the related grid. The first Down reveals the dock and requests progress focus, rather than play/pause focus. The second Down requests the centered play/pause button. Up from any action returns to progress; Up from progress hides the dock and returns to the video.

The progress component reports its actual focus to the player owner. Left/Right on the video are consumed without seeking or opening the dock; the timeline alone handles ten-second D-pad seeking. Dedicated rewind/fast-forward keys also require timeline focus. Select on the video continues to toggle playback while keeping the dock hidden. Existing five-second playing/idle dock hiding and user-interaction deadline refresh remain in place.

The first related row returns to the action row, then progress, then video. Up from Up next explicitly enters progress. Drawer and Back behavior otherwise retains the existing panel lifecycle. This change adds no database migration, storage reset or dependency update.

Local unit tests, debug/release lint and both APK builds passed (`artifacts/validation/0.12/build.txt`). The dedicated Chromecast `PlayerFocusTest` passed: while paused it compared actual Media3 positions before/after ignored Left/Right on video, verified ten-second seeking on progress, checked both directions of vertical focus, verified Select play/pause on video and entered/exited the related grid through the action row. Device output is retained in `artifacts/validation/0.12/device.txt`; the existing real-playback related-row regression was also run with its updated three-level control sequence. No new performance benchmark was made.

The optimized APK uses the existing local development signing key and is installed in place. Current version is 0.12.0/code 12, with APKs/hashes under ignored `artifacts/`. Existing previews remain accurately labeled as 0.10.0 captures; they were not regenerated for this focus-only update.
