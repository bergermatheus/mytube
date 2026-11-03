package app.mytube.tv

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class ChannelFeedTest {
    /** Public broadcaster fixture: regular uploads and broadcasts use separate tabs. */
    @Test fun channelIncludesBroadcastsFromLiveTab() = runBlocking {
        assumeTrue(System.getProperty("mytube.liveTests") == "true")
        ExtractorGateway.initialize()
        val service = ServiceList.YouTube
        val url = "https://www.youtube.com/@CazeTV"
        val channel = ChannelInfo.getInfo(service,url)
        val tabItems = channel.tabs.filter { tab -> tab.contentFilters.any { it in listOf("videos","livestreams") } }.map { tab ->
            val info = ChannelTabInfo.getInfo(service,tab)
            val initial = info.relatedItems.filterIsInstance<StreamInfoItem>()
            var items = initial
            var next = info.nextPage
            repeat(5) { index ->
                if(items.isEmpty() && org.schabi.newpipe.extractor.Page.isValid(next)) {
                    val page = ChannelTabInfo.getMoreItems(service,tab,next)
                    items = page.items.filterIsInstance<StreamInfoItem>()
                    next = page.nextPage
                    println("Deferred ${tab.contentFilters} step=$index items=${items.size} more=${org.schabi.newpipe.extractor.Page.isValid(next)} errors=${page.errors.map { it.message }}")
                }
            }
            println("Channel tab ${tab.contentFilters}: initial=${initial.size}, resolved=${items.size}")
            tab.contentFilters to items
        }
        val broadcasts = tabItems.first { "livestreams" in it.first }.second
        assertTrue("Public fixture has no broadcasts to check",broadcasts.isNotEmpty())
        val candidates = ExtractorGateway().channelCandidates(url)
        val urls = candidates.map { it.url }.toSet()
        assertTrue("Channel feed must include the Live tab, including replays",broadcasts.all { VideoUrls.canonical(it.url) in urls })
        val uploads = tabItems.firstOrNull { "videos" in it.first }?.second.orEmpty()
        assertTrue("Regular uploads must remain available",uploads.all { VideoUrls.canonical(it.url) in urls })
        assertEquals("Tabs must not duplicate a video",candidates.size,urls.size)
        println("Merged channel feed: ${candidates.size} videos; active live=${candidates.count { it.isLive }}")
    }
}
