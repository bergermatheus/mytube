package app.mytube.tv

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ChapterPlayerTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun extractedChaptersAppearAndUpdateWhenSeekingAcrossTheirBoundary() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("livePlayback") == "true")
        val i = InstrumentationRegistry.getInstrumentation()
        val app = i.targetContext.applicationContext as MyTubeApplication
        val url = "https://www.youtube.com/watch?v=rfscVS0vtbw"
        val info = runBlocking { app.gateway.details(url,fresh = true) }
        val chapters = app.gateway.chapters(info)
        assertTrue("Public fixture has no extracted chapters",chapters.entries.size > 1)
        val first = chapters.entries.first();val second = chapters.entries[1]
        val directory = File(i.targetContext.getExternalFilesDir(null),"chapters-0.10").apply { mkdirs() }
        val entries = org.json.JSONArray().apply { chapters.entries.forEach { put(org.json.JSONObject().put("title",it.title).put("start_ms",it.startMs)) } }
        File(directory,"extracted-chapters.json").writeText(org.json.JSONObject().put("url",url).put("extractor","NewPipeExtractor v0.26.5")
            .put("observed_at_ms",System.currentTimeMillis()).put("duration_seconds",info.duration).put("chapters",entries).toString(2))
        val activity = i.startActivitySync(Intent(i.targetContext,PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("video_url",url).putExtra("from_start",true))
        fun player(view: View): PlayerView? {
            if(view is PlayerView) return view
            if(view is ViewGroup) for(n in 0 until view.childCount) player(view.getChildAt(n))?.let { return it }
            return null
        }
        try {
            val deadline = SystemClock.elapsedRealtime()+90_000
            var ready = false
            while(!ready && SystemClock.elapsedRealtime() < deadline) {
                i.runOnMainSync { ready = player(activity.window.decorView)?.player?.let { it.isPlaying && it.currentPosition > 1000 } == true }
                if(!ready) SystemClock.sleep(200)
            }
            assertTrue("Chapter fixture did not start playing",ready)
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE)
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.onNodeWithTag("player-chapter-title").assertTextEquals(first.title)
            compose.onNodeWithTag("player-seek").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            i.runOnMainSync { player(activity.window.decorView)!!.player!!.seekTo(second.startMs+1000) }
            fun waitTitle(title: String) { compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("player-chapter-title") and hasText(title)).fetchSemanticsNodes().isNotEmpty() } }
            waitTitle(second.title)
            val bitmap = i.uiAutomation.takeScreenshot()
            File(directory,"segmented-progress.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);waitTitle(first.title)
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);waitTitle(second.title)
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
            compose.onNodeWithTag("player-seek").assertDoesNotExist()
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.onNodeWithTag("player-chapter-title").assertTextEquals(second.title)
            fun selectText(text: String) {
                compose.onNode(hasText(text) and hasClickAction()).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);compose.waitForIdle()
            }
            compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);compose.waitForIdle()
            selectText("Audio language")
            compose.onNodeWithText("Original (automatic)").assertIsDisplayed()
            val tracks = audioLanguages(info.audioStreams.filter { it.isUrl && it.deliveryMethod == org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP })
            val alternate = tracks.firstOrNull { !it.original }
            if(alternate != null) {
                selectText(alternate.label)
                var preserved = false
                i.runOnMainSync { preserved = kotlin.math.abs(player(activity.window.decorView)!!.player!!.currentPosition-(second.startMs+1000)) < 3000 }
                assertTrue("Changing audio reset playback position",preserved)
                selectText("Audio language");selectText("Original (automatic)")
            }
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.onNodeWithTag("player-controls").assertIsDisplayed()
        } finally { i.runOnMainSync { activity.finish() } }
    }
}
