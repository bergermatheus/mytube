package app.mytube.tv

import org.junit.Assert.*
import org.junit.Test

class VideoChaptersTest {
    @Test fun boundariesAreSortedDeduplicatedAndBoundToActualDuration() {
        val chapters = VideoChapters.from(listOf(VideoChapter("Second",30_000),VideoChapter("Invalid",-1),
            VideoChapter("Introdução",0),VideoChapter("Duplicate",30_000),VideoChapter("Past the end",100_000)))
        assertEquals(listOf("Introdução","Second","Past the end"),chapters.entries.map { it.title })
        assertEquals(listOf(ChapterSpan(0,30_000),ChapterSpan(30_000,90_000)),chapters.spans(90_000))
        assertEquals("Introdução",chapters.current(29_999,90_000)?.title)
        assertEquals("Second",chapters.current(30_000,90_000)?.title)
        assertEquals("Second",chapters.current(100_000,90_000)?.title)
    }
    @Test fun absentMetadataAndUnknownDurationDoNotInventChapters() {
        assertEquals(listOf(ChapterSpan(0,60_000)),VideoChapters.Empty.spans(60_000))
        assertNull(VideoChapters.Empty.current(10_000,60_000))
        val chapters = VideoChapters.from(listOf(VideoChapter("Later",20_000)))
        assertNull(chapters.current(19_999,60_000))
        assertEquals(listOf(ChapterSpan(0,20_000),ChapterSpan(20_000,60_000)),chapters.spans(60_000))
        assertTrue(chapters.spans(0).isEmpty());assertNull(chapters.current(20_000,0))
    }
}
