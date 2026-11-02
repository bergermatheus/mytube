package app.mytube.tv

import java.net.URI
import java.util.Locale
import kotlin.random.Random

data class Video(
    val url: String, val title: String, val channel: String = "",
    val channelUrl: String = "", val thumbnail: String = "", val durationSeconds: Long = 0,
    val views: Long = -1, val watchedAt: Long = 0, val positionMs: Long = 0,
    val hidden: Boolean = false,
    val publishedAtMs: Long? = null, val publishedText: String = "", val publishedApproximate: Boolean = false,
    val isLive: Boolean = false, val completed: Boolean = false,
)
data class Subscription(val url: String, val name: String)
data class Interest private constructor(val id: String, val label: String) {
    companion object {
        fun fromInput(input: String): Interest? {
            val label = input.trim().replace(Regex("\\s+"), " ")
            return label.takeIf { it.isNotEmpty() }?.let { Interest(it.lowercase(Locale.ROOT), it) }
        }
    }
}
data class VideoRow(val title: String, val videos: List<Video>, val key: String = title)
data class FeedPage(val videos: List<Video>, val hasMore: Boolean)

/** Balance channels across age bands, then prefer fresh uploads within each channel. */
object HomeFeed {
    fun mix(candidates: List<Video>, nowMs: Long, seed: Long, previousChannel: String? = null): List<Video> {
        val unique = linkedMapOf<String,Video>()
        for(video in candidates) {
            val previous = unique[video.url]
            if(previous == null || previous.publishedAtMs == null && video.publishedAtMs != null) unique[video.url] = video
        }
        fun ageBand(video: Video): Int {
            val age = video.publishedAtMs?.let { nowMs-it } ?: return 4
            return when { age < 0 -> 4; age < 86_400_000L -> 0; age < 7*86_400_000L -> 1; age < 30*86_400_000L -> 2; else -> 3 }
        }
        val shuffled = unique.values.sortedBy { it.url }.shuffled(Random(seed))
        val queues = shuffled.groupBy(::channelIdentity).mapValues { (_,videos) -> videos.sortedBy(::ageBand).toMutableList() }.toMutableMap()
        val counts = mutableMapOf<String,Int>()
        val result = mutableListOf<Video>()
        var last = previousChannel
        while(queues.isNotEmpty()) {
            val known = queues.keys.filter { ageBand(queues.getValue(it).first()) < 4 }
            val pool = known.ifEmpty { queues.keys.toList() }
            val alternatives = pool.filter { it != last }.ifEmpty { pool }
            val next = alternatives.minWithOrNull(compareBy<String>({ counts[it] ?: 0 },{ ageBand(queues.getValue(it).first()) }))!!
            val queue = queues.getValue(next)
            result += queue.removeAt(0); counts[next] = (counts[next] ?: 0)+1; last = next
            if(queue.isEmpty()) queues.remove(next)
        }
        return result
    }
    fun channelIdentity(video: Video): String = video.channelUrl.ifBlank { video.channel.trim().lowercase(Locale.ROOT).ifBlank { video.url } }
}

object VideoUrls {
    fun canonical(input: String): String? = try {
        val uri = URI(input.trim())
        if (uri.scheme !in listOf("https", "http") || uri.userInfo != null) null else {
            val host = uri.host?.lowercase()
            val id = when (host) {
                "youtu.be" -> uri.path.trim('/').takeIf { !it.contains('/') }
                "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" ->
                    if (uri.path == "/watch") uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
                    else if (uri.path.startsWith("/shorts/") || uri.path.startsWith("/live/")) uri.path.split('/').getOrNull(2)
                    else null
                else -> null
            }
            id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }?.let { "https://www.youtube.com/watch?v=$it" }
        }
    } catch (_: Exception) { null }
}

object Recommendations {
    /** Local ranking only; stream URLs and network requests are not persisted here. */
    fun rank(candidates: List<Video>, history: List<Video>, subscriptions: List<Subscription>, hidden: Set<String>, interests: List<Interest> = emptyList()): List<Video> {
        val affinity = history.groupingBy { it.channelUrl }.eachCount()
        val followed = subscriptions.map { it.url }.toSet()
        val watched = history.map { it.url }.toSet()
        val keywords = history.take(20).flatMap { words(it.title) }.groupingBy { it }.eachCount()
        val interestWords = interests.flatMap { words(it.label, 1) }.toSet()
        return candidates.distinctBy { it.url }.filter { it.url !in hidden && it.url !in watched }
            .sortedByDescending { v ->
                (if (v.channelUrl in followed) 8 else 0) + (affinity[v.channelUrl] ?: 0).coerceAtMost(5) * 2 +
                    words(v.title).sumOf { (keywords[it] ?: 0).coerceAtMost(3) } +
                    words(v.title, 1).count { it in interestWords } * 3
            }
    }
    private fun words(s: String, minLength: Int = 4) = s.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length >= minLength }.toSet()
}

data class VideoComment(val author: String,val text: String,val published: String,
    val avatar: String = "",val likes: Int? = null,val likesText: String = "",val replies: Int? = null,
    val pinned: Boolean = false)
data class VideoCommentsPage(val items: List<VideoComment>,val total: Int?)
