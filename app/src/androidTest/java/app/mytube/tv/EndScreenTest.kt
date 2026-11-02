package app.mytube.tv

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class EndScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val i get() = InstrumentationRegistry.getInstrumentation()
    private val original = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
    private fun player(root: View): ExoPlayer? {
        if(root is PlayerView) return root.player as? ExoPlayer
        if(root is ViewGroup) for(index in 0 until root.childCount) player(root.getChildAt(index))?.let { return it }
        return null
    }
    private fun start(): PlayerActivity {
        val activity = i.startActivitySync(Intent(i.targetContext,PlayerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("video_url",original).putExtra("from_start",true)) as PlayerActivity
        compose.waitUntil(90_000) {
            var ready = false
            i.runOnMainSync { ready = player(activity.window.decorView)?.let { it.isPlaying && it.currentPosition > 500 && (it.videoDecoderCounters?.renderedOutputBufferCount ?: 0) > 0 } == true }
            ready
        }
        return activity
    }
    private fun end(activity: PlayerActivity) {
        i.runOnMainSync {
            val current = player(activity.window.decorView)!!
            assertTrue(current.duration > 0)
            current.seekTo(current.duration-100);current.play()
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("up-next").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun replayAndEnd(activity: PlayerActivity) {
        i.runOnMainSync { player(activity.window.decorView)!!.apply { seekTo(0);play() } }
        compose.waitUntil(10_000) {
            var ready = false
            i.runOnMainSync { ready = player(activity.window.decorView)?.let { it.playbackState == Player.STATE_READY && it.currentPosition < 3000 } == true }
            ready
        }
        // Hide the related grid/control dock before this fresh viewing reaches its end.
        i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        end(activity)
    }
    @Test fun thumbnailBarsRepresentPartialAndCompletedViewing() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("uiTests") == "true")
        val activity = i.startActivitySync(Intent(i.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        try {
            i.runOnMainSync { activity.setContent { MyTubeTheme { Row {
                VideoCard(Video("partial","Partially watched","Channel",durationSeconds = 100,positionMs = 25_000),Modifier.width(245.dp)) { }
                VideoCard(Video("complete","Completed","Channel",durationSeconds = 100,positionMs = 100_000,completed = true),Modifier.width(245.dp)) { }
            } } } }
            val bars = compose.onAllNodesWithTag("thumbnail-progress",useUnmergedTree = true)
            bars.assertCountEquals(2)
            assertEquals(.25f,bars[0].fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current,.001f)
            assertEquals(1f,bars[1].fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current,.001f)
            val title = compose.onAllNodesWithTag("video-title",useUnmergedTree = true).onFirst().fetchSemanticsNode().boundsInRoot
            val bar = bars[0].fetchSemanticsNode().boundsInRoot
            assertTrue("Progress belongs at the thumbnail bottom, above the title",bar.bottom <= title.top)
            screenshot("partial-and-completed-thumbnail-bars.png")
        } finally { i.runOnMainSync { activity.finish() } }
    }
    @Test fun cancelAndDownStopAutoplayWhilePlayNextIsImmediate() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("livePlayback") == "true")
        val activity = start()
        try {
            end(activity)
            screenshot("up-next-countdown.png")
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            compose.onNodeWithTag("up-next-status").assertTextEquals("Autoplay canceled")
            compose.onNodeWithTag("up-next-cancel").assertIsFocused()
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.onNodeWithTag("player-related").assertIsDisplayed()
            SystemClock.sleep(10_500)
            assertEquals(original,activity.intent.getStringExtra("video_url"))
            val app = i.targetContext.applicationContext as MyTubeApplication
            compose.waitUntil(5000) { app.library.history().any { it.url == original && it.completed } }
            assertEquals(0L,app.library.resumePosition(original))
            replayAndEnd(activity)
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.onNodeWithTag("player-related").assertIsDisplayed()
            compose.onNodeWithTag("up-next").assertDoesNotExist()
            screenshot("related-after-cancel.png")
            replayAndEnd(activity)
            val target = compose.onNodeWithTag("up-next-target").fetchSemanticsNode().config[SemanticsProperties.StateDescription]
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitUntil(3000) { activity.intent.getStringExtra("video_url") == target }
            compose.onNodeWithTag("up-next").assertDoesNotExist()
        } finally { i.runOnMainSync { activity.finish() } }
    }
    @Test fun noInputAutomaticallyLoadsThePinnedRecommendationAfterTenSeconds() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("livePlayback") == "true")
        val activity = start()
        try {
            end(activity)
            val target = compose.onNodeWithTag("up-next-target").fetchSemanticsNode().config[SemanticsProperties.StateDescription]
            val start = SystemClock.elapsedRealtime()
            compose.waitUntil(15_000) { activity.intent.getStringExtra("video_url") == target }
            assertTrue("Autoplay skipped the ten-second interval",SystemClock.elapsedRealtime()-start >= 8000)
            compose.onNodeWithTag("up-next").assertDoesNotExist()
        } finally { i.runOnMainSync { activity.finish() } }
    }
    @Test fun backgroundingThePlayerCancelsPendingAutoplay() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("livePlayback") == "true")
        val activity = start()
        try {
            end(activity)
            i.runOnMainSync { activity.moveTaskToBack(true) }
            SystemClock.sleep(10_500)
            assertEquals(original,activity.intent.getStringExtra("video_url"))
            assertFalse(activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
        } finally { i.runOnMainSync { activity.finish() } }
    }
    private fun screenshot(name: String) {
        val folder = File(i.targetContext.getExternalFilesDir(null),"endscreen-0.9").apply { mkdirs() }
        val image = i.uiAutomation.takeScreenshot()
        File(folder,name).outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };image.recycle()
    }
}
