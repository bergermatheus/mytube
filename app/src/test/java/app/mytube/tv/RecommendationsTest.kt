package app.mytube.tv

import org.junit.Assert.*
import org.junit.Test

class RecommendationsTest {
    @Test fun savedInterestsGuideRankingWithoutWatchHistory() {
        val other = Video("other", "An unrelated vacation")
        val preferred = Video("preferred", "AI and machine learning explained")
        val result = Recommendations.rank(listOf(other, preferred), emptyList(), emptyList(), emptySet(), listOf(Interest.fromInput("AI")!!))
        assertEquals(preferred, result.first())
    }
    @Test fun interestsHaveStableIdentityAndPreserveReadableLabel() {
        assertEquals("Android Development", Interest.fromInput("  Android   Development\n")!!.label)
        assertEquals(Interest.fromInput("ANDROID development")!!.id, Interest.fromInput("android development")!!.id)
        assertNull(Interest.fromInput(" \n\t"))
    }
    @Test fun excludesWatchedHiddenAndDuplicates() {
        val watched = Video("seen", "Kotlin coroutines", channelUrl = "one")
        val hidden = Video("hidden", "Kotlin coroutines")
        val fresh = Video("fresh", "Kotlin coroutines")
        val result = Recommendations.rank(listOf(watched, hidden, fresh, fresh), listOf(watched), emptyList(), setOf("hidden"))
        assertEquals(listOf(fresh), result)
    }
    @Test fun followedChannelAndHistoryAffinityInfluenceRanking() {
        val unrelated = Video("a", "Travel", channelUrl = "other")
        val followed = Video("b", "Travel", channelUrl = "followed")
        val familiar = Video("c", "Kotlin coroutine tutorial", channelUrl = "familiar")
        val history = listOf(Video("old", "Kotlin coroutine introduction", channelUrl = "familiar"))
        val result = Recommendations.rank(listOf(unrelated, familiar, followed), history, listOf(Subscription("followed", "Name")), emptySet())
        assertTrue(result.indexOf(unrelated) > result.indexOf(followed))
        assertTrue(result.indexOf(unrelated) > result.indexOf(familiar))
    }
    @Test fun canonicalizesSupportedLinksAndRejectsOtherHosts() {
        val url = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
        assertEquals(url, VideoUrls.canonical("https://youtu.be/jNQXAC9IVRw?t=5"))
        assertEquals(url, VideoUrls.canonical("https://www.youtube.com/shorts/jNQXAC9IVRw"))
        assertEquals(url, VideoUrls.canonical("https://www.youtube.com/watch?x=1&v=jNQXAC9IVRw&list=whatever"))
        listOf("https://youtube.com.evil.test/watch?v=jNQXAC9IVRw", "file:///watch?v=jNQXAC9IVRw", "https://youtube.com@evil.test/watch?v=jNQXAC9IVRw", "https://youtube.com/watch?v=bad").forEach { assertNull(VideoUrls.canonical(it)) }
    }
}
