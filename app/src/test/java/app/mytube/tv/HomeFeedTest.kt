package app.mytube.tv

import org.junit.Assert.*
import org.junit.Test

class HomeFeedTest {
    private val now = 1_800_000_000_000L
    private fun video(id: String, channel: String, days: Long?) = Video(id, id, channelUrl = channel, publishedAtMs = days?.let { now - it*86_400_000 })
    @Test fun favorsRecentUploadsAndKeepsUnknownDatesLast() {
        val old = video("old","a",90); val recent = video("recent","b",0); val unknown = video("unknown","c",null)
        val week = video("week","a",3)
        assertEquals(listOf(recent,week,old,unknown),HomeFeed.mix(listOf(old,unknown,week,recent),now,12))
    }
    @Test fun mixesChannelsWithinAgeBandAndRetainsAllVideos() {
        val videos = (0..5).map { video("a$it","a",1) } + (0..5).map { video("b$it","b",1) }
        val mixed = HomeFeed.mix(videos,now,42)
        assertEquals(videos.map { it.url }.toSet(),mixed.map { it.url }.toSet())
        assertTrue(mixed.zipWithNext().all { (a,b) -> a.channelUrl != b.channelUrl })
        assertEquals(mixed,HomeFeed.mix(videos.reversed(),now,42))
        assertNotEquals(mixed,HomeFeed.mix(videos,now,99))
    }
    @Test fun mergesDuplicateIdentityWithoutLosingPublicationEvidence() {
        val known = video("same","a",1).copy(publishedText="yesterday",publishedApproximate=true)
        val unknown = video("same","a",null)
        assertEquals(listOf(known),HomeFeed.mix(listOf(unknown,known),now,1))
        assertEquals("Date unavailable",publicationLabel(unknown))
    }
    @Test fun oneFreshChannelCannotMonopolizeTheFirstPage() {
        val fresh = (0..24).map { video("fresh$it","a",0) }
        val rest = listOf("b","c","d","e").flatMap { channel -> (0..4).map { video("$channel$it",channel,4) } }
        val page = HomeFeed.mix(fresh+rest,now,42).take(10)
        assertEquals(5,page.map { it.channelUrl }.toSet().size)
        assertTrue(page.groupingBy { it.channelUrl }.eachCount().values.all { it == 2 })
        assertTrue(page.zipWithNext().all { (a,b) -> a.channelUrl != b.channelUrl })
    }

}
