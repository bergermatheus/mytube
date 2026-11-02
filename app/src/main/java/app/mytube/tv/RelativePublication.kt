package app.mytube.tv

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Calendar days avoid showing yesterday's upload as Today after local midnight. */
fun publicationLabel(video: Video,nowMs: Long = System.currentTimeMillis(),zone: ZoneId = ZoneId.systemDefault()): String {
    if(video.isLive) return "Live now"
    val published = video.publishedAtMs ?: return "Date unavailable"
    val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(published).atZone(zone).toLocalDate(),Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate())
    fun ago(count: Long,unit: String) = "$count $unit${if(count == 1L) "" else "s"} ago"
    return when {
        days < 0 -> "Upcoming"
        days == 0L -> "Today"
        days < 7 -> ago(days,"day")
        days < 30 -> ago(days/7,"week")
        days < 365 -> ago(days/30,"month")
        else -> ago(days/365,"year")
    }
}
