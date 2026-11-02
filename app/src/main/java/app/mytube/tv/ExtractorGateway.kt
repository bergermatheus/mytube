package app.mytube.tv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit

class ExtractorDownloader : Downloader() {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS).build()
    override fun execute(request: Request): Response {
        val headers = request.headers()
        val builder = okhttp3.Request.Builder().url(request.url())
            .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36")
        headers.forEach { (key, values) -> builder.removeHeader(key); values.forEach { builder.addHeader(key, it) } }
        val method = request.httpMethod()
        val body = if (method in listOf("GET", "HEAD")) null else
            (request.dataToSend() ?: ByteArray(0)).toRequestBody(headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value?.firstOrNull()?.toMediaTypeOrNull())
        builder.method(method, body)
        return client.newCall(builder.build()).execute().use { reply ->
            if (reply.code == 429) throw ReCaptchaException("YouTube is limiting requests on this network. Try again later.", request.url())
            Response(reply.code, reply.message, reply.headers.toMultimap(), YouTubeCommentResponses.normalizeAvatars(reply.body?.string().orEmpty()), reply.request.url.toString())
        }
    }
}

class ExtractorGateway {
    private val service = ServiceList.YouTube
    private val gate = Mutex()
    private val titles = OriginalTitles()
    private val detailsCache = object : LinkedHashMap<String, StreamInfo>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StreamInfo>?) = size > 8
    }
    class SearchPage internal constructor(val query: String,val videos: List<Video>,
        internal val handler: org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler,internal val next: Page?) {
        val hasMore: Boolean get() = Page.isValid(next)
    }
    suspend fun searchPage(query: String,previous: SearchPage? = null): SearchPage = withContext(Dispatchers.IO) { gate.withLock {
        require(previous == null || previous.query == query)
        if(previous == null) {
            val handler = service.searchQHFactory.fromQuery(query,listOf("videos"),"")
            val info = SearchInfo.getInfo(service,handler)
            SearchPage(query,videos(info.relatedItems),handler,info.nextPage)
        } else {
            val next = previous.next ?: return@withLock SearchPage(query,emptyList(),previous.handler,null)
            val info = SearchInfo.getMoreItems(service,previous.handler,next)
            SearchPage(query,videos(info.items),previous.handler,info.nextPage)
        }
    } }
    suspend fun searchCandidates(query: String): FeedPage {
        val page = searchPage(query)
        return FeedPage(page.videos,page.hasMore)
    }
    suspend fun search(query: String): FeedPage {
        val page = searchCandidates(query)
        return page.copy(videos = originalTitles(page.videos))
    }
    private data class Avatar(val url: String,val at: Long)
    private val avatarRequests = kotlinx.coroutines.sync.Semaphore(3)
    private val channelAvatars = object : LinkedHashMap<String,Avatar>(64,.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,Avatar>?) = size > 64
    }
    private fun rememberAvatar(url: String,channel: ChannelInfo): String {
        val avatar = channel.avatars.lastOrNull()?.url.orEmpty()
        if(avatar.isNotBlank()) synchronized(channelAvatars) { channelAvatars[url] = Avatar(avatar,System.currentTimeMillis()) }
        return avatar
    }
    suspend fun channelAvatar(url: String): String = withContext(Dispatchers.IO) {
        val cached = synchronized(channelAvatars) { channelAvatars[url]?.takeIf { System.currentTimeMillis()-it.at < 86_400_000L }?.url }
        cached ?: avatarRequests.withPermit { rememberAvatar(url,ChannelInfo.getInfo(service,url)) }
    }
    suspend fun channelVideos(url: String): List<Video> = originalTitles(channelCandidates(url))
    suspend fun originalTitles(videos: List<Video>): List<Video> = titles.resolve(videos) { video ->
        try { details(video.url).name } catch(e: kotlinx.coroutines.CancellationException) { throw e } catch(_: Exception) { null }
    }
    suspend fun details(url: String, fresh: Boolean = false): StreamInfo = withContext(Dispatchers.IO) { gate.withLock {
        val canonical = requireNotNull(VideoUrls.canonical(url)) { "Use a YouTube video URL." }
        if (!fresh) detailsCache[canonical]?.let { return@withLock it }
        StreamInfo.getInfo(service, canonical).also { detailsCache[canonical] = it; titles.remember(canonical,it.name) }
    } }
    fun chapters(info: StreamInfo): VideoChapters = if(isActiveLive(info.streamType,info.contentAvailability)) VideoChapters.Empty else
        VideoChapters.from(info.streamSegments.orEmpty().map { VideoChapter(it.title.orEmpty(),it.startTimeSeconds.toLong()*1000L) })
    suspend fun channelCandidates(url: String): List<Video> = withContext(Dispatchers.IO) { gate.withLock {
        val channel = ChannelInfo.getInfo(service, url)
        rememberAvatar(url,channel)
        val tab = channel.tabs.firstOrNull { "videos" in it.contentFilters } ?: channel.tabs.firstOrNull()
            ?: return@withLock emptyList()
        videos(ChannelTabInfo.getInfo(service, tab).relatedItems)
    } }
    suspend fun channelLiveCandidates(url: String): List<Video> = withContext(Dispatchers.IO) {
        val channel = ChannelInfo.getInfo(service,url)
        rememberAvatar(url,channel)
        val tab = channel.tabs.firstOrNull { "livestreams" in it.contentFilters } ?: return@withContext emptyList()
        videos(ChannelTabInfo.getInfo(service,tab).relatedItems).filter { it.isLive }
    }
    suspend fun liveSearchCandidates(query: String): List<Video> = withContext(Dispatchers.IO) {
        val handler = service.searchQHFactory.fromQuery("$query ao vivo",listOf("videos"),"")
        videos(SearchInfo.getInfo(service,handler).relatedItems).filter { it.isLive }
    }
    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        service.suggestionExtractor.suggestionList(query).filter { it.isNotBlank() }.distinct().take(8)
    }
    suspend fun comments(url: String): VideoCommentsPage = withContext(Dispatchers.IO) {
        val canonical = requireNotNull(VideoUrls.canonical(url))
        val info = org.schabi.newpipe.extractor.comments.CommentsInfo.getInfo(service,canonical)
        val items = info.relatedItems.map {
            VideoComment(it.uploaderName.orEmpty(),
                if(it.commentText.type == org.schabi.newpipe.extractor.stream.Description.HTML) android.text.Html.fromHtml(it.commentText.content,android.text.Html.FROM_HTML_MODE_LEGACY).toString() else it.commentText.content,
                it.textualUploadDate.orEmpty(),it.uploaderAvatars.firstOrNull()?.url.orEmpty(),
                it.likeCount.takeIf { count -> count >= 0 },it.textualLikeCount.orEmpty(),
                it.replyCount.takeIf { count -> count >= 0 },it.isPinned)
        }
        VideoCommentsPage(items,info.commentsCount.takeIf { it >= 0 && (it > 0 || items.isEmpty()) })
    }
    fun related(info: StreamInfo) = videos(info.relatedItems.orEmpty())
    fun video(info: StreamInfo) = Video(info.url, info.name, info.uploaderName.orEmpty(), info.uploaderUrl.orEmpty(),
        info.thumbnails.lastOrNull()?.url.orEmpty(), info.duration, info.viewCount,
        publishedAtMs = info.uploadDate?.instant?.toEpochMilli(), publishedText = info.textualUploadDate.orEmpty(),
        publishedApproximate = info.uploadDate?.isApproximation ?: false,isLive = isActiveLive(info.streamType,info.contentAvailability))
    private fun videos(items: List<InfoItem>): List<Video> = items.filterIsInstance<StreamInfoItem>().mapNotNull { item ->
        VideoUrls.canonical(item.url)?.let { url -> Video(url, item.name, item.uploaderName.orEmpty(),
            item.uploaderUrl.orEmpty(), item.thumbnails.lastOrNull()?.url.orEmpty(), item.duration, item.viewCount,
            publishedAtMs = item.uploadDate?.instant?.toEpochMilli(), publishedText = item.textualUploadDate.orEmpty(),
            publishedApproximate = item.uploadDate?.isApproximation ?: false,isLive = isActiveLive(item.streamType,item.contentAvailability)) }
    }
    companion object {
        fun initialize() { NewPipe.init(ExtractorDownloader()) }
        internal fun isActiveLive(type: org.schabi.newpipe.extractor.stream.StreamType,availability: org.schabi.newpipe.extractor.stream.ContentAvailability?): Boolean =
            type in listOf(org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM,org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM) && availability !in listOf(org.schabi.newpipe.extractor.stream.ContentAvailability.UPCOMING,org.schabi.newpipe.extractor.stream.ContentAvailability.MEMBERSHIP,org.schabi.newpipe.extractor.stream.ContentAvailability.PAID)
    }
}
