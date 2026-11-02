package app.mytube.tv

/** The stored record owns completion; being close to the end does not mean finished. */
data class WatchProgress(val positionMs: Long,val durationSeconds: Long,val completed: Boolean,val watchedAt: Long)

class WatchProgressSnapshot internal constructor(private val records: Map<String,WatchProgress?>) {
    fun apply(video: Video): Video {
        if(!records.containsKey(video.url)) return video
        val progress = records[video.url]
        return video.copy(positionMs = progress?.positionMs ?: 0,completed = progress?.completed ?: false,
            watchedAt = progress?.watchedAt ?: 0,
            durationSeconds = if(!video.isLive && video.durationSeconds <= 0) progress?.durationSeconds ?: video.durationSeconds else video.durationSeconds)
    }
    fun apply(videos: List<Video>): List<Video> = videos.map(::apply)
}

fun watchedFraction(video: Video): Float? {
    if(video.isLive) return null
    if(video.completed) return 1f
    if(video.positionMs <= 0 || video.durationSeconds <= 0) return null
    return (video.positionMs.toDouble()/(video.durationSeconds.toDouble()*1000)).coerceIn(0.0,1.0).toFloat()
}
