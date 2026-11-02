package app.mytube.tv

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test

class HomePagerTest {
    private val now = 1_800_000_000_000L
    @Test fun pagePreparationDoesNotRunOnTheCallerThread() = runBlocking {
        val caller = Thread.currentThread()
        val pager = HomePager(listOf(VideoSource("source") {
            assertNotEquals(caller,Thread.currentThread())
            listOf(Video("one","One"))
        }),{
            assertNotEquals(caller,Thread.currentThread()); it
        },now,1,{
            assertNotEquals(caller,Thread.currentThread()); it
        })
        assertEquals("one",pager.next().videos.single().url)
    }
    @Test fun cancelledPreparationCanResumeWithoutSkippingSourcesOrVideos() = runBlocking {
        val preparing = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        var attempts = 0
        val pager = HomePager(listOf(VideoSource("one") { listOf(Video("one","One")) }),{it},now,1,{
            if(attempts++ == 0) { preparing.complete(Unit);release.await() }
            it
        })
        val job = launch { pager.next() }
        preparing.await();job.cancel();job.join()
        val page = pager.next()
        assertEquals("one",page.videos.single().url)
        assertFalse(page.hasMore)
    }
    @Test fun firstBatchDoesNotWaitForEverySourceAndLaterBatchesOnlyAppend() = runBlocking {
        val calls = mutableListOf<Int>()
        val sources = (0..8).map { channel -> VideoSource("Channel $channel") {
            calls += channel
            (0..14).map { Video("$channel-$it","Video $it",channelUrl="$channel",publishedAtMs=now - (8-channel)*86_400_000L) }
        } }
        val pager = HomePager(sources,{it},now,42)
        val first = pager.next()
        assertEquals(10,first.videos.size)
        assertEquals(listOf(0,1,2,3,4),calls)
        assertTrue(first.hasMore)
        val visible = first.videos.toMutableList()
        val firstIdentities = visible.map { it.url }
        val second = pager.next()
        visible += second.videos
        assertEquals(10,second.videos.size)
        assertEquals(firstIdentities,visible.take(10).map { it.url })
        assertEquals(listOf(0,1,2,3,4,5),calls)
        var page = second
        while(page.hasMore) { page = pager.next(); visible += page.videos; assertTrue(page.videos.size <= 10) }
        assertEquals(135,visible.size)
        assertEquals(visible.size,visible.map { it.url }.toSet().size)
    }
    @Test fun partialFailuresAndDuplicateSourcesDoNotLoseCommittedVideos() = runBlocking {
        val a = Video("a","A"); val b = Video("b","B")
        val pager = HomePager(listOf(VideoSource("first") { listOf(a) },VideoSource("offline") { throw java.io.IOException() },VideoSource("third") { listOf(a,b) }),{it},now,1)
        val page = pager.next()
        assertEquals(setOf("a","b"),page.videos.map { it.url }.toSet())
        assertEquals(1,page.warnings.size)
        assertFalse(page.hasMore)
        assertTrue(pager.next().videos.isEmpty())
    }
}
