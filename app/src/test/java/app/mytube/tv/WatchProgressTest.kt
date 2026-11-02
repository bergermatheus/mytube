package app.mytube.tv

import org.junit.Assert.*
import org.junit.Test

class WatchProgressTest {
    @Test fun fractionUsesSavedPositionAndExactCompletion() {
        val video = Video("v","Video",durationSeconds = 100)
        assertNull(watchedFraction(video))
        assertEquals(.25f,watchedFraction(video.copy(positionMs = 25_000))!!,.001f)
        assertEquals(.95f,watchedFraction(video.copy(positionMs = 95_000))!!,.001f)
        assertEquals(1f,watchedFraction(video.copy(positionMs = 150_000))!!,.001f)
        assertEquals(1f,watchedFraction(video.copy(completed = true))!!,.001f)
        assertNull(watchedFraction(video.copy(isLive = true,positionMs = 50_000,completed = true)))
        assertNull(watchedFraction(video.copy(durationSeconds = 0,positionMs = 50_000)))
    }
    @Test fun snapshotsPreserveMetadataAndDistinguishAbsentFromUnrequestedProgress() {
        val source = Video("v","Original title",durationSeconds = 100,publishedText = "Yesterday")
        val snapshot = WatchProgressSnapshot(mapOf("v" to WatchProgress(25_000,100,false,123),"absent" to null))
        assertEquals(source.copy(positionMs = 25_000,watchedAt = 123),snapshot.apply(source))
        assertEquals(0L,snapshot.apply(source.copy(url = "absent",positionMs = 50_000,completed = true)).positionMs)
        assertFalse(snapshot.apply(source.copy(url = "absent",completed = true)).completed)
        val unrequested = source.copy(url = "new",positionMs = 50_000)
        assertEquals(unrequested,snapshot.apply(unrequested))
    }
}
