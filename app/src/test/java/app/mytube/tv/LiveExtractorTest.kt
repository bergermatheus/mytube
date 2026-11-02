package app.mytube.tv

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in real network smoke test, never counted as executed when skipped. */
class LiveExtractorTest {
    @Test fun followedChannelProvidesVideoCandidates() = runBlocking {
        assumeTrue(System.getProperty("mytube.liveTests") == "true")
        ExtractorGateway.initialize()
        val gateway = ExtractorGateway()
        val channelUrl = gateway.details("https://www.youtube.com/watch?v=aqz-KE-bpKQ").uploaderUrl
        assertTrue("Test video did not identify an uploader channel", !channelUrl.isNullOrBlank())
        val videos = gateway.channelVideos(channelUrl)
        assertTrue("Channel returned no video candidates", videos.isNotEmpty())
        assertTrue(videos.all { VideoUrls.canonical(it.url) != null })
        println("Live channel feed: ${videos.size} video candidates")
    }
    @Test fun searchAndExtractRealVideo() = runBlocking {
        assumeTrue(System.getProperty("mytube.liveTests") == "true")
        ExtractorGateway.initialize()
        val gateway = ExtractorGateway()
        val page = gateway.search("Big Buck Bunny Blender")
        assertTrue("Search returned no videos", page.videos.isNotEmpty())
        val info = gateway.details("https://www.youtube.com/watch?v=aqz-KE-bpKQ", fresh = true)
        assertTrue(info.name.isNotBlank())
        assertTrue("No playable video streams", info.videoStreams.isNotEmpty() || info.videoOnlyStreams.isNotEmpty() || !info.hlsUrl.isNullOrBlank())
        println("Live extraction: ${page.videos.size} search results; ${info.videoStreams.size + info.videoOnlyStreams.size} video formats")
    }
}
