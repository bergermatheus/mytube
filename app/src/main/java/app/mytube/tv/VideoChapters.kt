package app.mytube.tv

data class VideoChapter(val title: String,val startMs: Long)
data class ChapterSpan(val startMs: Long,val endMs: Long)

/** Normalized metadata for one video; absent chapters remain absent. End times use playback duration. */
class VideoChapters private constructor(val entries: List<VideoChapter>) {
    fun current(positionMs: Long,durationMs: Long): VideoChapter? =
        if(durationMs <= 0) null else entries.lastOrNull { it.startMs <= positionMs.coerceIn(0,durationMs) && it.startMs < durationMs }
    fun spans(durationMs: Long): List<ChapterSpan> {
        if(durationMs <= 0) return emptyList()
        val boundaries = (listOf(0L) + entries.map { it.startMs }.filter { it in 1 until durationMs } + durationMs)
        return boundaries.zipWithNext { start,end -> ChapterSpan(start,end) }
    }
    companion object {
        val Empty = VideoChapters(emptyList())
        fun from(entries: List<VideoChapter>): VideoChapters = VideoChapters(entries.filter { it.startMs >= 0 }.sortedBy { it.startMs }.distinctBy { it.startMs })
    }
}
