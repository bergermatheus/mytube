package app.mytube.tv

import com.grack.nanojson.JsonParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Resolves only displayed batches. Feed translations never overwrite a known original title. */
class OriginalTitles {
    private val client = OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(10,TimeUnit.SECONDS).build()
    private val requests = Semaphore(3)
    private data class Entry(val title: String,val at: Long)
    private val cache = object : LinkedHashMap<String,Entry>(512,.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,Entry>?) = size > 512
    }
    fun remember(url: String,title: String) { if(title.isNotBlank()) synchronized(cache) { cache[url] = Entry(title,System.currentTimeMillis()) } }
    suspend fun resolve(videos: List<Video>,fallback: suspend (Video) -> String?): List<Video> = coroutineScope {
        videos.map { video -> async {
            val cached = synchronized(cache) { cache[video.url]?.takeIf { System.currentTimeMillis()-it.at < 86_400_000L }?.title }
            val title = cached ?: requests.withPermit { fetch(video.url) ?: fallback(video) }
            if(title.isNullOrBlank()) video else { remember(video.url,title);video.copy(title = title) }
        } }.awaitAll()
    }
    private suspend fun fetch(url: String): String? = suspendCancellableCoroutine { continuation ->
        val canonical = VideoUrls.canonical(url)
        if(canonical == null) { continuation.resume(null);return@suspendCancellableCoroutine }
        val endpoint = "https://www.youtube.com/oembed".toHttpUrl().newBuilder().addQueryParameter("url",canonical).addQueryParameter("format","json").build()
        val call = client.newCall(Request.Builder().url(endpoint).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call,e: IOException) { if(continuation.isActive) continuation.resume(null) }
            override fun onResponse(call: Call,response: Response) {
                val title = response.use { reply ->
                    if(!reply.isSuccessful) null else try { JsonParser.`object`().from(reply.body?.string().orEmpty()).getString("title")?.takeIf { it.isNotBlank() } } catch(_: Exception) { null }
                }
                if(continuation.isActive) continuation.resume(title)
            }
        })
    }
}
