package app.mytube.tv

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

/** Opt-in ADB helper, intentionally absent from the shipped application's exported components. */
@RunWith(AndroidJUnit4::class)
class PreferenceImportTest {
    @Test fun applyExplicitPreferences() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("applyPreferences") == "true")
        val json = JSONObject(String(Base64.decode(requireNotNull(args.getString("preferences")), Base64.DEFAULT), Charsets.UTF_8))
        val channelsJson = json.getJSONArray("channels")
        val topicsJson = json.getJSONArray("topics")
        val channels = (0 until channelsJson.length()).map { i ->
            val item = channelsJson.getJSONObject(i)
            val channel = Subscription(item.getString("url"), item.getString("name"))
            val uri = URI(channel.url)
            require(uri.scheme == "https" && uri.host == "www.youtube.com" && uri.userInfo == null && uri.query == null && uri.fragment == null)
            require(uri.path.matches(Regex("/channel/UC[A-Za-z0-9_-]{22}")))
            channel
        }
        val topics = (0 until topicsJson.length()).map { topicsJson.getString(it) }
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MyTubeApplication
        app.library.addPreferences(channels, topics)
        assertTrue(app.library.subscriptions().map { it.url }.containsAll(channels.map { it.url }))
        assertTrue(app.library.interests().map { it.id }.containsAll(topics.map { Interest.fromInput(it)!!.id }))
        println("Applied ${channels.size} channels and ${topics.size} topics without replacing existing preferences or history.")
    }
}
