package app.mytube.tv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class VideoSource(val label: String, val fetch: suspend () -> List<Video>)
data class HomePage(val videos: List<Video>, val hasMore: Boolean, val warnings: List<String>)

/** Owns a browse session. Committed pages are immutable; later sources only feed future pages. */
class HomePager(
    private val sources: List<VideoSource>,
    private val rank: (List<Video>) -> List<Video>,
    private val nowMs: Long,
    private val seed: Long,
    private val prepare: suspend (List<Video>) -> List<Video> = { it },
) {
    private val advance = Mutex()
    private var sourceIndex = 0
    private var pageIndex = 0
    private val pending = linkedMapOf<String,Video>()
    private val delivered = mutableSetOf<String>()
    private var lastChannel: String? = null

    suspend fun next(): HomePage = withContext(Dispatchers.Default) { advance.withLock {
        var nextSource = sourceIndex
        val nextPending = LinkedHashMap(pending)
        val warnings = mutableListOf<String>()
        // Sample five sources before committing the first ten; later batches add one.
        val sourceBudget = if(pageIndex == 0) 5 else 1
        var fetched = 0
        while(nextSource < sources.size && (fetched < sourceBudget || rank(nextPending.values.toList()).size < 10)) {
            val source = sources[nextSource++]
            fetched++
            try {
                for(video in source.fetch()) if(video.url !in delivered) {
                    val previous = nextPending[video.url]
                    if(previous == null || previous.publishedAtMs == null && video.publishedAtMs != null) nextPending[video.url] = video
                }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { warnings += "${source.label}: ${readableError(e)}" }
        }
        val selected = HomeFeed.mix(rank(nextPending.values.toList()),nowMs,seed+pageIndex,lastChannel).take(10)
        val page = prepare(selected)
        currentCoroutineContext().ensureActive()
        sourceIndex = nextSource
        pending.clear();pending.putAll(nextPending)
        pageIndex++
        lastChannel = page.lastOrNull()?.let(HomeFeed::channelIdentity) ?: lastChannel
        for(video in page) { delivered += video.url; pending.remove(video.url) }
        // Drop filtered-out candidates rather than advertising an empty, endless next page.
        val eligible = rank(pending.values.toList()).map { it.url }.toSet()
        pending.keys.retainAll(eligible)
        HomePage(page,sourceIndex < sources.size || pending.isNotEmpty(),warnings)
    } }
}
