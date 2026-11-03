# How MyTube works

MyTube is a single-module native Android application. Kotlin/Compose for TV draws the interface; NewPipeExtractor obtains public YouTube metadata/stream information; Media3 plays streams; SQLite and local profile storage retain viewer choices. There is no MyTube server to start, required API key or Google login.

## From a page to a video

1. `MainActivity` creates `BrowseViewModel` with the application-owned gateway/library. Sidebar focus selects a destination after giving focus feedback a chance to paint.
2. The model obtains saved preferences/history off the UI thread and asks `ExtractorGateway` for feeds, searches, channels, livestreams, metadata or comments. The gateway owns NewPipe initialization, request coordination and metadata caches.
3. `BrowsePages` stores a separate snapshot for each browse route. The loader captures that page's owner; changing selection does not redirect a late result into a different page. Jobs belong to the ViewModel and are canceled when it is cleared.
4. `RetainedPages` preserves visited compositions for the activity lifetime. It measures/places only the active page, clears inactive semantics and prevents hidden focus entry. Explicit focus restoration wins over automatic destination entry. There are eight route slots, including one channel slot reused for the selected channel.
5. `HomeVideoGrid`, `SubscriptionGrid` and `SearchPage` render their current data without waiting for the entire discovery process. Stable URLs identify cards. Selecting a video launches `PlayerActivity` immediately; long-press opens additional actions.
6. `PlayerActivity` obtains stream info, selects media sources and owns the Media3 player/session and related background jobs. `PlayerControls`/`PlayerDrawer` render the custom bottom dock, related grid, comments and settings. The default Media3 overlay is disabled.
7. Playback updates the local library. Progress revisions refresh retained browse/search cards without replacing current video metadata or reordering committed pages.

## Recommendations and caches

Home candidates come from followed channels, saved topic searches, saved/liked examples and recent history. An empty preference set uses a generic discovery search. `HomePager` prepares batches on a worker dispatcher, samples several initial sources and commits ten videos at a time. Further pages preserve the previous prefix.

`Recommendations.rank` in `Models.kt` filters watched/hidden items and computes local affinity scores. **The final display is also processed by `HomeFeed.mix`, which sorts URLs and shuffles its pool before channel/recency balancing. Numeric rank order is therefore not preserved as final Home ordering.** Do not advertise fixed recommendation percentages or describe this as Google's recommendation algorithm. Source selection, filtering, mixing and committed paging are different stages.

| State | Lifetime / freshness |
| --- | --- |
| Page compositions and per-route scroll | Current activity; one slot per browse route |
| Page requests and snapshots | ViewModel; late completion updates the captured page |
| Home session and pagination | Retained while browsing; explicit preference changes invalidate its data for a later sidebar visit |
| Library | Refreshes local history on entry |
| Live | Rechecks on entry after one minute or preference invalidation; a broadcast can end after discovery |
| Channel | Opening a channel fetches its feed; one channel slot |
| Search | Separate query/results/continuation; keyboard mode and page state survive visits in the current activity |
| Original titles | Bounded 512-entry, 24-hour in-memory cache |
| Channel avatars | Bounded 64-entry, 24-hour in-memory cache; up to three metadata requests at once |

Retaining layout is not persisting screenshots or preventing all recomposition. New data still updates visible components; activity/process destruction releases those compositions. Keep cache keys, freshness and ownership explicit when adding new caches.

## Local data and player state

`LibraryStore` owns SQLite schema 5: subscriptions, topics, saved recommendation examples, local reactions, search history, watch history, position/duration and explicit completion. `ProfileStore` owns a local display name/photo. Current imports and migrations must preserve unrelated choices.

Progress strips use actual saved position/duration. Completion comes from a real Media3 playback-end event; near-end position alone is not completion. `WatchProgress` snapshots distinguish absent requested records from URLs never queried. Completed videos restart on explicit replay; partial videos resume.

`VideoChapters` normalizes NewPipe stream-segment starts once at the extraction boundary. `ChapterSeekBar` uses current playback duration to draw divisions and played portions; missing metadata stays a continuous track. Current chapter titles retain their supplied language. A chapter picker and seek-preview thumbnails are not implemented.

`UpNext` pins a next target and ten-second monotonic deadline. The player owns cancellation on interaction, backgrounding, errors and video switches. Old callbacks must not seek/save/switch a newly selected player. Related-grid navigation owns its row boundaries: only Up from row zero returns to the dock.

## Independence and network boundaries

Public video metadata, thumbnails, searches, autocomplete and stream requests reach YouTube and its delivery infrastructure. Local subscriptions/reactions do not change a Google account. The application has no analytics/ad SDK, mandatory account or MyTube backend. Playback avoids YouTube-inserted ad breaks; sponsorships embedded in video content remain.

An official APK and REA reports exist only as ignored local research context. Prior static findings show a Cobalt host for the remote TV interface; they do not supply the complete page implementation or prove exact runtime parity. No official executable/assets are needed to build this repository. See [feature behavior](FEATURES.md), [development commands](DEVELOPMENT.md), [agent context](../AGENT.md) and [latest recorded validation](VALIDATION-0.16.md).
