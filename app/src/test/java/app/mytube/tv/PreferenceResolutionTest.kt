package app.mytube.tv

import org.junit.Assume.assumeTrue
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import java.util.Base64

/** Explicit local research helper; never runs as part of normal builds. */
class PreferenceResolutionTest {
    @Test fun resolveRequestedChannels() {
        assumeTrue(System.getProperty("mytube.liveTests") == "true")
        val inputs = System.getenv("MYTUBE_CHANNEL_INPUTS")?.lines()?.filter { it.isNotBlank() }.orEmpty()
        assumeTrue(inputs.isNotEmpty())
        ExtractorGateway.initialize()
        val service = ServiceList.YouTube
        fun emit(input: String, name: String, url: String, detail: String) {
            val fields = listOf(input, name, url, detail).map { Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) }
            println("MYTUBE_CHANNEL\t" + fields.joinToString("\t"))
        }
        for(input in inputs) {
            try {
                if(input.startsWith("https://")) {
                    val channel = ChannelInfo.getInfo(service, input)
                    emit(input, channel.name, channel.url, channel.description.orEmpty().take(300))
                } else {
                    val handler = service.searchQHFactory.fromQuery(input, listOf("channels"), "")
                    SearchInfo.getInfo(service, handler).relatedItems.filterIsInstance<ChannelInfoItem>().take(5).forEach {
                        emit(input, it.name, it.url, "verified=${it.isVerified}; subscribers=${it.subscriberCount}; ${it.description.orEmpty().take(200)}")
                    }
                }
            } catch(e: Exception) { emit(input, "UNRESOLVED", "", e.javaClass.simpleName) }
        }
    }
}
