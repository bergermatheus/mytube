package app.mytube.tv

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class TvRedesignTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun sidebarCollapsesOnContentFocusAndExpandsOnReturn() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("uiTests") == "true")
        requireAwake()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        try {
            compose.waitForIdle()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("sidebar-expanded").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(60_000) { compose.onAllNodesWithTag("initial-loading").fetchSemanticsNodes().isEmpty() }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            compose.waitUntil(5000) { compose.onAllNodesWithTag("sidebar-collapsed").fetchSemanticsNodes().isNotEmpty() }
            screenshot("sidebar-collapsed.png")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT)
            compose.waitUntil(5000) { compose.onAllNodesWithTag("sidebar-expanded").fetchSemanticsNodes().isNotEmpty() }
            screenshot("sidebar-expanded.png")
            compose.onNodeWithContentDescription("Subscriptions").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitForIdle()
            compose.waitUntil(5000) { compose.onAllNodesWithText("Select a channel to see its videos").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Select a channel to see its videos").assertIsDisplayed()
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun controlsStayAtBottomAndDownSelectsAnotherVideo() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("livePlayback") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        requireAwake()
        val original = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext,PlayerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("video_url",original).putExtra("from_start",true))
        try {
            compose.waitForIdle()
            waitForPlaying(activity.window.decorView)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE)
            instrumentation.runOnMainSync { assertFalse(findPlayer(activity.window.decorView)!!.useController) }
            compose.onNodeWithTag("player-controls").assertDoesNotExist()
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.onNodeWithTag("player-controls").assertIsDisplayed()
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val controls = compose.onNodeWithTag("player-controls").fetchSemanticsNode().boundsInRoot
            assertTrue("Controls overlap the middle of the video",controls.top >= root.height * 0.5f)
            assertTrue("Control dock does not reach the bottom",controls.bottom >= root.bottom - 2f)
            screenshot("player-bottom-controls.png")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.onNodeWithContentDescription("Play / pause").assertIsFocused()
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.waitUntil(60_000) { compose.onAllNodesWithTag("player-related").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("player-related").assertIsDisplayed()
            compose.onNodeWithTag("related-video-0").assertIsDisplayed()
            compose.onNodeWithText("Back to player").assertDoesNotExist()
            screenshot("player-related-videos.png")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.waitForIdle()
            compose.onNodeWithTag("related-video-0").assertIsNotFocused()
            compose.onNodeWithTag("player-related").assertIsDisplayed()
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
            compose.onNodeWithTag("related-video-0").assertIsFocused()
            compose.onNodeWithTag("player-related").assertIsDisplayed()
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
            compose.onNodeWithTag("player-controls").assertIsDisplayed()
            compose.onNodeWithTag("player-related").assertDoesNotExist()
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
            compose.onNodeWithTag("player-seek").assertIsFocused()
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
            compose.onNodeWithTag("player-controls").assertDoesNotExist()
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.waitUntil(60_000) { compose.onAllNodesWithTag("player-related").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("player-related").assertIsDisplayed()
            compose.onNodeWithTag("related-video-0").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitUntil(5000) { activity.intent.getStringExtra("video_url") != original }
            waitForPlaying(activity.window.decorView)
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun requireAwake() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Wake the Chromecast before visible UI tests", (context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager).isInteractive)
    }
    private fun waitForPlaying(root: View) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.elapsedRealtime() + 90_000
        var success = false
        while(SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync { success = findPlayer(root)?.player?.let { it.isPlaying && it.currentPosition > 1000 } == true }
            if(success) break
            SystemClock.sleep(250)
        }
        assertTrue("Video did not play within 90 seconds",success)
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null),"test-screenshots").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        FileOutputStream(File(directory,name)).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    private fun findPlayer(view: View): PlayerView? {
        if(view is PlayerView) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) findPlayer(view.getChildAt(i))?.let { return it }
        return null
    }
}
