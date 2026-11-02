package app.mytube.tv

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.json.JSONObject
import java.io.File

/** Opt-in documentation capture utility. No production rendering setting is changed.
 * Chromecast's SurfaceView hardware plane is absent from screencap/screenrecord. Swap only
 * this test activity's video surface to TextureView, then capture the real composed screen.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DocumentationCaptureTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun captureRealPlayerFramesAndPanels() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureDocs") == "true")
        val i = InstrumentationRegistry.getInstrumentation()
        val url = "https://www.youtube.com/watch?v=rfscVS0vtbw"
        val activity = i.startActivitySync(Intent(i.targetContext,PlayerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("video_url",url).putExtra("from_start",true))
        val out = File(i.targetContext.getExternalFilesDir(null),"docs-refresh").apply { mkdirs() }
        fun find(view: View): PlayerView? {
            if(view is PlayerView) return view
            if(view is ViewGroup) for(n in 0 until view.childCount) find(view.getChildAt(n))?.let { return it }
            return null
        }
        fun await(reason: String,predicate: () -> Boolean) {
            val until = SystemClock.elapsedRealtime()+90_000
            while(SystemClock.elapsedRealtime() < until) {
                var done = false;i.runOnMainSync { done = predicate() }
                if(done) return
                SystemClock.sleep(250)
            }
            fail(reason)
        }
        fun key(code: Int) { i.sendKeyDownUpSync(code);compose.waitForIdle() }
        fun select(description: String) {
            compose.onNodeWithContentDescription(description).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            key(KeyEvent.KEYCODE_DPAD_CENTER)
        }
        fun diversity(bitmap: Bitmap,left: Int,top: Int,right: Int,bottom: Int): Int {
            val colors = mutableSetOf<Int>()
            for(y in top until bottom step maxOf(1,(bottom-top)/28))
                for(x in left until right step maxOf(1,(right-left)/40)) colors.add(bitmap.getPixel(x,y) and 0x00FFFFFF)
            return colors.size
        }
        val captures = JSONObject()
        fun capture(name: String,videoRightFraction: Float = 1f) {
            compose.waitForIdle();SystemClock.sleep(600)
            val bitmap = i.uiAutomation.takeScreenshot()
            val colors = diversity(bitmap,bitmap.width/12,bitmap.height/9,(bitmap.width*videoRightFraction).toInt()-bitmap.width/12,bitmap.height*3/8)
            assertTrue("Screen capture has no visible video frame: $name (colors=$colors)",colors > 30)
            File(out,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            captures.put(name,JSONObject().put("sampled_video_colors",colors))
            println("Captured real video + UI: $name ($colors sampled colors)")
        }
        try {
            await("Video did not decode") {
                val p = find(activity.window.decorView)?.player as? ExoPlayer
                val counters = p?.videoDecoderCounters;counters?.ensureUpdated()
                p?.isPlaying == true && (counters?.renderedOutputBufferCount ?: 0) > 3
            }
            lateinit var texture: TextureView
            lateinit var player: ExoPlayer
            i.runOnMainSync {
                val view = find(activity.window.decorView)!!
                player = view.player as ExoPlayer
                val surface = view.videoSurfaceView!!
                val frame = surface.parent as ViewGroup
                frame.removeView(surface)
                texture = TextureView(activity).apply {
                    isFocusable = false
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
                }
                frame.addView(texture,0)
                player.setVideoTextureView(texture)
                player.seekTo(135_000);player.play()
            }
            await("TextureView did not receive actual video pixels") {
                if(!texture.isAvailable || texture.width == 0 || texture.height == 0 || player.currentPosition < 136_000) false
                else texture.bitmap?.let { bitmap ->
                    val ok = diversity(bitmap,bitmap.width/10,bitmap.height/10,bitmap.width*9/10,bitmap.height*9/10) > 30
                    bitmap.recycle();ok
                } == true
            }
            i.runOnMainSync { player.pause() }
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.onNodeWithTag("player-chapter-title").assertTextEquals("Installing Python & PyCharm")
            capture("player-chapters.png")
            select("Settings")
            compose.onNodeWithTag("player-settings").assertIsDisplayed()
            capture("settings.png",.6f)
            key(KeyEvent.KEYCODE_BACK)
            select("Comments")
            compose.waitUntil(90_000) { compose.onAllNodesWithTag("comment-card-0").fetchSemanticsNodes().isNotEmpty() }
            capture("comments.png",.57f)
            key(KeyEvent.KEYCODE_BACK)
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("related-video-0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("player-related").assertIsDisplayed()
            capture("related-videos.png")
            val report = JSONObject().put("video_url",url).put("version",BuildConfig.VERSION_NAME)
                .put("observed_at_ms",System.currentTimeMillis())
                .put("capture_method","UiAutomation screenshot of real playback; instrumentation-only TextureView replaces uncapturable SurfaceView")
                .put("production_code_changed",false).put("captures",captures)
            File(out,"capture.json").writeText(report.toString(2))
        } finally { i.runOnMainSync { activity.finish() } }
    }
}
