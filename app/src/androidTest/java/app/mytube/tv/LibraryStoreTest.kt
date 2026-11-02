package app.mytube.tv

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryStoreTest {
    @Test fun publicationAndLocalLikesSurviveReopenAndHistoryDeletion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "publication-rating.db"
        context.deleteDatabase(name)
        val video = Video("dated-video","A dated video",publishedAtMs=1_799_000_000_000,publishedText="2 days ago",publishedApproximate=true)
        var store = LibraryStore(context,name)
        try {
            store.setRating(video,1); store.record(video,12_000)
            store.close(); store = LibraryStore(context,name)
            assertEquals(1,store.rating(video.url))
            assertEquals(video.publishedAtMs,store.history().single().publishedAtMs)
            assertTrue(store.history().single().publishedApproximate)
            store.record(video.copy(publishedAtMs=null,publishedText="",publishedApproximate=false),15_000)
            assertEquals(video.publishedAtMs,store.history().single().publishedAtMs)
            store.clearHistory()
            assertTrue(store.history().isEmpty())
            assertEquals(video.url,store.likedVideos().single().url)
            store.setRating(video,-1)
            assertTrue(store.likedVideos().isEmpty()); assertTrue(video.url in store.hidden())
            store.setSeed(video,true)
            assertEquals(0,store.rating(video.url)); assertFalse(video.url in store.hidden())
        } finally { store.close(); context.deleteDatabase(name) }
    }
    @Test fun savedPreferencesSurviveReopenAndHistoryClear() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "preferences-test.db"
        context.deleteDatabase(name)
        val video = Video("https://www.youtube.com/watch?v=jNQXAC9IVRw", "A useful example", "Channel", "https://www.youtube.com/@example")
        var store = LibraryStore(context, name)
        try {
            store.addInterest("  Android  Development ")
            store.addInterest("ANDROID development")
            store.addPreferences(listOf(Subscription(video.channelUrl, video.channel)), listOf("Android Development"))
            store.addPreferences(listOf(Subscription(video.channelUrl, video.channel)), listOf("ANDROID Development"))
            store.setSeed(video, true)
            store.record(video, 30_000)
            store.close(); store = LibraryStore(context, name)
            assertEquals(listOf("Android Development"), store.interests().map { it.label })
            assertEquals(video.url, store.seeds().single().url)
            store.clearHistory()
            assertTrue(store.history().isEmpty())
            assertEquals(0L, store.progress(video.url))
            assertEquals(video.url, store.seeds().single().url)
            assertEquals(1, store.interests().size)
            assertEquals(1, store.subscriptions().size)
            store.hide(video)
            assertTrue(store.seeds().isEmpty())
            assertTrue(video.url in store.hidden())
            store.setSeed(video, true)
            assertEquals(video.url, store.seeds().single().url)
            assertFalse(video.url in store.hidden())
            store.removeInterest(store.interests().single().id)
            store.setSeed(video, false)
            assertTrue(store.interests().isEmpty())
            assertTrue(store.seeds().isEmpty())
        } finally { store.close(); context.deleteDatabase(name) }
    }
    @Test fun upgradesOriginalDatabaseWithoutLosingUserLibrary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "upgrade-test.db"
        context.deleteDatabase(name)
        // Historical schema from MyTube 0.1.0, not the new schema under test.
        context.openOrCreateDatabase(name, android.content.Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE videos(url TEXT PRIMARY KEY,title TEXT NOT NULL,channel TEXT NOT NULL,channel_url TEXT NOT NULL,thumbnail TEXT NOT NULL,duration INTEGER NOT NULL,views INTEGER NOT NULL,watched_at INTEGER NOT NULL,position INTEGER NOT NULL,hidden INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE INDEX watched_at_idx ON videos(watched_at DESC)")
            db.execSQL("CREATE TABLE subscriptions(url TEXT PRIMARY KEY,name TEXT NOT NULL)")
            db.execSQL("INSERT INTO videos VALUES('old-video','A saved title','Channel','channel-url','',60,100,1000,12000,0)")
            db.execSQL("INSERT INTO videos VALUES('hidden-video','Dismissed','Channel','channel-url','',60,100,1000,9000,1)")
            db.execSQL("INSERT INTO subscriptions VALUES('channel-url','Channel')")
            db.version = 1
        }
        val store = LibraryStore(context, name)
        try {
            assertEquals("old-video", store.history().single().url)
            assertEquals(12_000L, store.progress("old-video"))
            assertTrue("hidden-video" in store.hidden())
            assertEquals("channel-url", store.subscriptions().single().url)
            assertTrue(store.seeds().isEmpty())
            store.addInterest("Astronomy")
            assertEquals("Astronomy", store.interests().single().label)
        } finally { store.close(); context.deleteDatabase(name) }
    }
    @Test fun durableProgressAndSubscriptionsPreserveHiddenPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "library-test.db"
        context.deleteDatabase(name)
        val video = Video("https://www.youtube.com/watch?v=jNQXAC9IVRw", "A title's punctuation", "Channel", "https://www.youtube.com/channel/example")
        var store = LibraryStore(context, name)
        try {
            store.record(video, 25_000)
            store.toggleSubscription(Subscription(video.channelUrl, video.channel))
            store.close(); store = LibraryStore(context, name)
            assertEquals(25_000L, store.progress(video.url))
            assertEquals(video.title, store.history().single().title)
            assertEquals(video.channelUrl, store.subscriptions().single().url)
            store.hide(video)
            store.record(video, 30_000)
            assertTrue(store.history().isEmpty())
            assertTrue(video.url in store.hidden())
            store.clearHistory()
            assertTrue(video.url in store.hidden())
            assertEquals(1, store.subscriptions().size)
            store.resetRecommendations()
            assertTrue(store.hidden().isEmpty())
            store.toggleSubscription(Subscription(video.channelUrl, video.channel))
            assertTrue(store.subscriptions().isEmpty())
        } finally { store.close(); context.deleteDatabase(name) }
    }
}
