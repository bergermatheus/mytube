package app.mytube.tv

import org.junit.Assert.*
import org.junit.Test

class UpNextTest {
    private val video = Video("next","Next video")
    @Test fun countdownExpiresAtTenSecondsWithCeilingDisplay() {
        val countdown = UpNextCountdown.start(video,"current",500)
        assertEquals(10,countdown.remainingSeconds(500))
        assertEquals(10,countdown.remainingSeconds(501))
        assertEquals(9,countdown.remainingSeconds(1500))
        assertEquals(1,countdown.remainingSeconds(10_499))
        assertFalse(countdown.isDue(10_499))
        assertTrue(countdown.isDue(10_500))
        assertEquals(0,countdown.remainingSeconds(10_500))
    }
    @Test fun canceledCountdownNeverBecomesDueAndRetainsExplicitPlayTarget() {
        val canceled = UpNextCountdown.start(video,"current",0).cancel()
        assertNull(canceled.remainingSeconds(50_000))
        assertFalse(canceled.isDue(50_000))
        assertEquals(video,canceled.target)
        assertEquals("current",canceled.sourceUrl)
    }
    @Test fun newVideoHasItsOwnFullDeadline() {
        val previous = UpNextCountdown.start(video,"old",0).cancel()
        val next = UpNextCountdown.start(video.copy(url = "another"),"new",50_000)
        assertFalse(previous.isDue(60_000))
        assertEquals(10,next.remainingSeconds(50_000))
        assertEquals("another",next.target.url)
    }
}
