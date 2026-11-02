package app.mytube.tv

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class WatchProgressStoreTest {
    @Test fun schemaFourUpgradeProgressSnapshotsCompletionAndHistoryClear() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "watch-progress-upgrade.db"
        createLegacyLibrary(context,name,4)
        context.openOrCreateDatabase(name,android.content.Context.MODE_PRIVATE,null).use {
            it.execSQL("INSERT INTO videos(url,title,channel,channel_url,thumbnail,duration,views,watched_at,position) VALUES('old','Old title','Channel','channel-url','',100,100,1000,25000)")
            it.execSQL("INSERT INTO subscriptions VALUES('channel-url','Channel')")
            it.execSQL("INSERT INTO interests VALUES('technology','Technology')")
            it.execSQL("INSERT INTO search_history VALUES('query','query',1000)")
        }
        var store = LibraryStore(context,name)
        try {
            assertEquals(25_000L,store.progress("old"))
            assertEquals(25_000L,store.resumePosition("old"))
            assertFalse(store.history().single().completed)
            assertEquals(1,store.subscriptions().size)
            assertEquals("Technology",store.interests().single().label)
            assertEquals(listOf("query"),store.searchHistory())
            val original = Video("old","Current original title",durationSeconds = 100)
            store.record(original,100_000,completed = true)
            store.close();store = LibraryStore(context,name)
            assertTrue(store.history().single().completed)
            assertEquals(0L,store.resumePosition("old"))
            assertEquals(1f,watchedFraction(store.withProgress(listOf(original)).single())!!,.001f)
            store.record(original,95_000)
            assertFalse(store.history().single().completed)
            assertEquals(95_000L,store.resumePosition("old"))
            val batch = (0..1000).map { Video(if(it == 1000) "old" else "unseen-$it","Video $it",durationSeconds = 100) }
            val decorated = store.withProgress(batch)
            assertEquals(.95f,watchedFraction(decorated.last())!!,.001f)
            assertTrue(decorated.dropLast(1).all { watchedFraction(it) == null })
            store.setSeed(original,true)
            store.clearHistory()
            assertEquals(0L,store.withProgress(listOf(original.copy(positionMs = 95_000,completed = true))).single().positionMs)
            assertFalse(store.withProgress(listOf(original)).single().completed)
            assertEquals(1,store.subscriptions().size)
        } finally { store.close();context.deleteDatabase(name) }
    }
}
