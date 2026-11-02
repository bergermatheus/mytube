package app.mytube.tv

import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.ContentAvailability

class LiveFeedTest {
    @Test fun currentLiveStreamsExcludeRecordingsPremieresAndPaidStreams() {
        assertTrue(ExtractorGateway.isActiveLive(StreamType.LIVE_STREAM,ContentAvailability.AVAILABLE))
        assertTrue(ExtractorGateway.isActiveLive(StreamType.AUDIO_LIVE_STREAM,ContentAvailability.AVAILABLE))
        assertFalse(ExtractorGateway.isActiveLive(StreamType.POST_LIVE_STREAM,ContentAvailability.AVAILABLE))
        assertFalse(ExtractorGateway.isActiveLive(StreamType.VIDEO_STREAM,ContentAvailability.AVAILABLE))
        assertFalse(ExtractorGateway.isActiveLive(StreamType.LIVE_STREAM,ContentAvailability.UPCOMING))
        assertFalse(ExtractorGateway.isActiveLive(StreamType.LIVE_STREAM,ContentAvailability.MEMBERSHIP))
        assertTrue(ExtractorGateway.isActiveLive(StreamType.LIVE_STREAM,ContentAvailability.UNKNOWN))
        assertTrue(ExtractorGateway.isActiveLive(StreamType.LIVE_STREAM,null))
    }
    @Test fun prioritizesFollowedChannelsWithoutMistakingTitlesForLiveState() {
        val recorded = Video("recorded","LIVE NOW",durationSeconds = 0)
        val suggested = Video("suggested","Suggested",channelUrl = "other",isLive = true)
        val followed = Video("followed","Followed",channelUrl = "subscribed",isLive = true)
        val hidden = Video("hidden","Hidden",isLive = true)
        assertEquals(listOf(followed,suggested),LiveFeed.select(listOf(recorded,suggested,followed,followed,hidden),listOf(Subscription("subscribed","Channel")),setOf("hidden")))
    }
    @Test fun localProfileHasReadableDefaultInitial() {
        assertEquals("User",LocalProfile().displayName)
        assertEquals("U",LocalProfile().initial)
        assertEquals("M",LocalProfile("Matheus").initial)
    }
}
