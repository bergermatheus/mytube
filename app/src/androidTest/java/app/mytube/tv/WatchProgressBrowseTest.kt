package app.mytube.tv

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class WatchProgressBrowseTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun savedCompletionAndNewViewingAppearOnTheRealSearchThumbnail() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("livePlayback") == "true")
        val i = InstrumentationRegistry.getInstrumentation()
        val original = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
        val app = i.targetContext.applicationContext as MyTubeApplication
        assertTrue("Run the completed-video checks first",app.library.history().any { it.url == original && it.completed })
        val activity = i.startActivitySync(Intent(i.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        val monitor = i.addMonitor(PlayerActivity::class.java.name,null,false)
        var playback: android.app.Activity? = null
        fun progress(): Float? = compose.onAllNodesWithTag("thumbnail-progress",useUnmergedTree = true).fetchSemanticsNodes()
            .firstOrNull()?.config?.get(SemanticsProperties.ProgressBarRangeInfo)?.current
        try {
            compose.onNodeWithTag("search-input").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.onNodeWithTag("key-0-0").assertIsFocused()
            i.sendStringSync(original)
            compose.onNodeWithTag("search-input").assertTextContains(original)
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_SEARCH)
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("search-video-0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Me at the zoo",useUnmergedTree = true).assertIsDisplayed()
            assertNotNull("Expected saved completion on the original video thumbnail",progress())
            assertEquals(1f,progress()!!,.001f)
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            playback = monitor.waitForActivityWithTimeout(10_000)
            assertNotNull(playback)
            compose.waitUntil(90_000) {
                var ready = false
                i.runOnMainSync { ready = findPlayer(playback!!.window.decorView)?.player?.let { it.isPlaying && it.currentPosition > 2000 } == true }
                ready
            }
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("search-video-0").fetchSemanticsNodes().isNotEmpty() && progress()?.let { it > 0 && it < 1 } == true }
            val folder = File(i.targetContext.getExternalFilesDir(null),"endscreen-0.9").apply { mkdirs() }
            val image = i.uiAutomation.takeScreenshot()
            File(folder,"saved-progress-on-search.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };image.recycle()
        } finally {
            i.removeMonitor(monitor)
            i.runOnMainSync { playback?.takeUnless { it.isFinishing }?.finish();activity.finish() }
        }
    }
    private fun findPlayer(root: View): PlayerView? {
        if(root is PlayerView) return root
        if(root is ViewGroup) for(index in 0 until root.childCount) findPlayer(root.getChildAt(index))?.let { return it }
        return null
    }
}
