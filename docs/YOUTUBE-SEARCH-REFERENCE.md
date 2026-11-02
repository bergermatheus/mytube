# Original YouTube APK search reference

Inspected 2026-11-02 using the existing REA CLI 6.7.0 and JADX headless 0.7.1. No software was installed, no original app was launched, and no account or network traffic was accessed.

## Identity and existing evidence

Caller-selected artifact: `youtube-5.30.340/base.apk`, package `com.google.android.youtube.tv`, version 5.30.340. SHA-256: `b1d9a3db9929be5c659821bdd8c360c89b3a766fa9426db1a2b55266f80e6129` (recomputed before retaining these results and matched to every new Evidence subject).

Existing package Evidence `ev_da108a8a2629f9543eb862cd926113430cf09570f41f668af80bb7eb9ff8c79d` declares `MainActivity` with `cobalt.APP_URL=https://www.youtube.com/tv`, `android.app.lib_name=cobalt`. Its exported ShellActivity declares `MEDIA_PLAY_FROM_SEARCH` and YouTube deep links. These are static declarations, not observations of search execution. Existing archive findings in [YOUTUBE-UI-REFERENCE.md](YOUTUBE-UI-REFERENCE.md) found splash HTML but no named JavaScript/CSS members or Android layout resources for the photographed search page.

## Fresh focused observations

- StarboardBridge member inventory contains `nativeOnSearchRequested(): boolean`. Evidence: `ev_2a40f913e65f6f012e20b72e9f614af8356dfd8383a79ae478ff659a6f9ff099`.
- CobaltActivity member inventory declares `onSearchRequested(): boolean`. Evidence: `ev_2ef0f9d8f5b9ec37c1ae632fd9bf9a72f1832a8caddba4e114d9c1f33b641e04`.
- Decompiled CobaltActivity.onSearchRequested obtains the StarboardBridge, checks `starboardBridge.h`, and if true returns `starboardBridge.nativeOnSearchRequested()`; otherwise returns false. This method does not construct an Android search dialog, EditText, keyboard, history list, or results layout. Evidence: `ev_fbd5ba317d0b9af2f7ebb9f37bb3887430f9f13f2d11f22dc42c6fdc483a4f56`.
- A case-sensitive class-name substring query `Keyboard` returned zero matches among 8,454 classes with complete class-name query coverage. Evidence: `ev_341494bc5a47f3e57afe20f25ed182490d15dce9213a25e6eea374342797b6ca`. This bounded negative says nothing about obfuscated classes, native code, remotely supplied code, or generic input widgets.

Complete new REA records are retained in `artifacts/rea-search-reference/`. No native analysis session was opened.

## Inference and reconstruction boundary

The remote application declaration, Cobalt entry bridge, and actual search-request forwarding support implementing search as an in-app TV route rather than treating a separate Android search dialog as the reference. The examined callback alone does not prove what the native engine and server page render. Exact search-bar geometry, the embedded keyboard, the sidebar-to-back-arrow transition, left-hand query history, and the lower recommended-video row are established by the caller's `youtube_improve/search-bar.jpeg` and `search-feature.jpeg`, not recovered Android layout code.

Independent MyTube implementation can therefore use a narrower pill search trigger on Home and an internal search page with Back, microphone, editable query display, D-pad keyboard, local persisted recent searches, live text suggestions, and a lower video row. Local history and recommendation source selection are MyTube design choices to deliver the photographed behavior without Google login. Do not claim that the original APK disclosed its history storage, recommendation algorithm, keyboard layout switching rules, CSS dimensions, or focus graph; those remain unknown in this bounded static investigation. No official binary code or assets need to be copied.
