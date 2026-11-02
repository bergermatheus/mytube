package app.mytube.tv

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativePublicationTest {
    private val now = Instant.parse("2026-11-02T12:00:00Z").toEpochMilli()
    private val zone = ZoneId.of("Europe/Rome")
    @Test fun relativeDaysWeeksMonthsAndYears() {
        for((days,label) in mapOf(0 to "Today",1 to "1 day ago",2 to "2 days ago",6 to "6 days ago",7 to "1 week ago",14 to "2 weeks ago",30 to "1 month ago",90 to "3 months ago",365 to "1 year ago",730 to "2 years ago")) {
            assertEquals(label,publicationLabel(Video("v","title",publishedAtMs = now-days*86_400_000L),now,zone))
        }
    }
    @Test fun liveVideosUseCurrentStatusRatherThanAnUnknownDate() {
        assertEquals("Live now",publicationLabel(Video("v","live",isLive = true),now,zone))
    }
    @Test fun usesCalendarDaysAndPreservesUnknownAndFutureDates() {
        val beforeMidnight = Instant.parse("2026-11-01T22:55:00Z").toEpochMilli()
        val afterMidnight = Instant.parse("2026-11-01T23:05:00Z").toEpochMilli()
        assertEquals("1 day ago",publicationLabel(Video("v","title",publishedAtMs = beforeMidnight),afterMidnight,zone))
        assertEquals("Date unavailable",publicationLabel(Video("v","title",publishedText = "unparsed source date"),now,zone))
        assertEquals("Upcoming",publicationLabel(Video("v","title",publishedAtMs = now+86_400_000L),now,zone))
    }
}
